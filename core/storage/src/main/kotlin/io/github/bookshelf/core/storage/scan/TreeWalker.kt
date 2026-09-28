package io.github.bookshelf.core.storage.scan

import android.net.Uri
import android.provider.DocumentsContract
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.storage.saf.DocumentEntry
import io.github.bookshelf.core.storage.saf.SafTreeReader
import io.github.bookshelf.core.storage.saf.SourceRef
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.coroutines.cancellation.CancellationException

/**
 * Обход дерева источника.
 *
 * Реализация обязана быть прерываемой и возобновляемой: обход идёт в ширину порциями,
 * периодически отдаёт накопленный батч в [onBatch] и возвращает [ScanCursor] для продолжения.
 * `null` в успешном результате означает «обход завершён»; непустой курсор — «продолжить
 * с сохранённого места» (docs/architecture.md §4.5).
 */
interface TreeWalker {

    /**
     * @param cursor точка продолжения или `null` для обхода с начала;
     * @param onBatch вызывается на каждом батче найденных записей (50–100 файлов);
     * @param resumeFrom точка продолжения после обработки [batch]; её воркер сохраняет
     * после каждого вызова, чтобы пережить остановку процесса;
     * @return курсор для продолжения либо `null`, если дерево обойдено целиком.
     */
    suspend fun walk(
        source: SourceRef,
        cursor: ScanCursor?,
        options: ScanOptions,
        onBatch: suspend (batch: List<DocumentEntry>, resumeFrom: ScanCursor) -> Unit,
    ): AppResult<ScanCursor?>
}

/**
 * Реализация обхода по умолчанию.
 *
 * Сознательно без состояния: точка продолжения целиком лежит в [ScanCursor], поэтому
 * перезапуск после `Service.onTimeout` или убийства процесса не теряет прогресс.
 *
 * После каждого батча текущая папка остаётся в начале очереди, если в ней есть ещё записи.
 * Поле [ScanCursor.documentId] отмечает последнюю запись, поэтому следующий вызов повторно
 * читает только эту папку и пропускает уже обработанный префикс. Глубина каждого элемента
 * очереди хранится в [ScanCursor.Item], поскольку одна граница BFS может содержать папки
 * разных уровней.
 *
 * @param treeReader источник дочерних записей SAF;
 * @param dispatchers IO-диспетчер, чтобы блокирующий DocumentsProvider не занимал поток вызова.
 */
open class DefaultTreeWalker(
    private val treeReader: SafTreeReader,
    private val dispatchers: AppDispatchers,
) : TreeWalker {

    override suspend fun walk(
        source: SourceRef,
        cursor: ScanCursor?,
        options: ScanOptions,
        onBatch: suspend (batch: List<DocumentEntry>, resumeFrom: ScanCursor) -> Unit,
    ): AppResult<ScanCursor?> = withContext(dispatchers.io) {
        when (val result = appRunCatching {
            walkInternal(source, cursor, options, onBatch)
        }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> result
        }
    }

    private suspend fun walkInternal(
        source: SourceRef,
        cursor: ScanCursor?,
        options: ScanOptions,
        onBatch: suspend (batch: List<DocumentEntry>, resumeFrom: ScanCursor) -> Unit,
    ): AppResult<ScanCursor?> {
        val pending = ArrayDeque<ScanCursor.Item>()
        if (cursor == null) {
            val rootId = DocumentsContract.getTreeDocumentId(Uri.parse(source.treeUri))
            pending.add(ScanCursor.Item(rootId, depth = 0))
        } else {
            pending.addAll(cursor.queue)
        }
        if (pending.isEmpty()) return AppResult.success(null)

        val excludedGlobs = (options.excludeGlobs + source.excludeGlobs).distinct()
        val extensions = options.extensions
            .map { it.trim().lowercase().removePrefix(".") }
            .toSet()
        val batch = mutableListOf<DocumentEntry>()
        val nextQueue = mutableListOf<ScanCursor.Item>()
        var lastDocumentId = cursor?.documentId.orEmpty()
        var firstFolder = true
        val resumeMarker = cursor?.documentId

        while (pending.isNotEmpty()) {
            checkCancellation()
            val current = pending.removeFirst()
            val children = when (val result = treeReader.children(source.treeUri, current.documentId)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> return result
            }
            var childIndex = 0

            if (firstFolder && resumeMarker != null) {
                val markerIndex = children.indexOfFirst { entry: DocumentEntry ->
                    entry.documentId == resumeMarker
                }
                childIndex = if (markerIndex >= 0) markerIndex + 1 else 0
            }
            firstFolder = false

            while (childIndex < children.size) {
                checkCancellation()
                val entry = children[childIndex++]
                lastDocumentId = entry.documentId
                if (entry.isHidden(options.excludeHidden) || entry.isExcluded(excludedGlobs)) continue

                if (entry.isDirectory) {
                    if (source.recursive && current.depth < options.maxDepth) {
                        nextQueue += ScanCursor.Item(entry.documentId, current.depth + 1)
                    }
                } else if (entry.isBook(extensions)) {
                    batch += entry
                    if (batch.size == BATCH_SIZE) {
                        return emitBatchAndCursor(
                            batch = batch,
                            current = current,
                            childIndex = childIndex,
                            children = children,
                            pending = pending,
                            nextQueue = nextQueue,
                            lastDocumentId = lastDocumentId,
                            onBatch = onBatch,
                        )
                    }
                }
            }
        }

        if (batch.isNotEmpty()) {
            val resumeFrom = ScanCursor(lastDocumentId, nextQueue)
            onBatch(batch.toList(), resumeFrom)
        }
        return if (nextQueue.isEmpty()) AppResult.success(null) else AppResult.success(
            ScanCursor(lastDocumentId, nextQueue),
        )
    }

    private suspend fun emitBatchAndCursor(
        batch: MutableList<DocumentEntry>,
        current: ScanCursor.Item,
        childIndex: Int,
        children: List<DocumentEntry>,
        pending: ArrayDeque<ScanCursor.Item>,
        nextQueue: List<ScanCursor.Item>,
        lastDocumentId: String,
        onBatch: suspend (batch: List<DocumentEntry>, resumeFrom: ScanCursor) -> Unit,
    ): AppResult<ScanCursor?> {
        checkCancellation()
        val resumeQueue = mutableListOf<ScanCursor.Item>()
        // Текущая папка возвращается в очередь, только если в ней остались непрочитанные дети.
        if (childIndex < children.size) resumeQueue += current
        // Подкаталоги, найденные именно в этом батче, обязаны попасть в очередь: иначе
        // возобновление потеряет целый уровень дерева молча.
        pending.forEach { resumeQueue += it }
        nextQueue.forEach { resumeQueue += it }
        val resumeFrom = ScanCursor(lastDocumentId, resumeQueue)
        onBatch(batch.toList(), resumeFrom)
        batch.clear()
        return AppResult.success(resumeFrom)
    }

    private suspend fun checkCancellation() {
        if (!currentCoroutineContext().isActive) {
            throw CancellationException("Обход дерева SAF отменён")
        }
    }

    private fun DocumentEntry.isHidden(excludeHidden: Boolean): Boolean =
        excludeHidden && isHidden

    private fun DocumentEntry.isExcluded(excludeGlobs: List<String>): Boolean =
        BookFileFilter.isExcluded(displayName, excludeGlobs)

    private fun DocumentEntry.isBook(extensions: Set<String>): Boolean =
        BookFileFilter.isBookFileName(displayName) &&
            BookFileFilter.detectExtension(displayName) in extensions

    private companion object {

        const val BATCH_SIZE = 100
    }
}
