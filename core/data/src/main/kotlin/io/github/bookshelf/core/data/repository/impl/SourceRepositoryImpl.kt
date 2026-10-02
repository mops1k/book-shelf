package io.github.bookshelf.core.data.repository.impl

import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.sync.ScanScheduler
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.storage.saf.SafTreeReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Источники библиотеки поверх `sources` и `books` («Архитектура» §4.1).
 *
 * Три решения, которые видны только в целом:
 *
 * * **Разрешение берётся раньше записи.** Строка источника без persistable-разрешения после
 *   перезапуска процесса стала бы «мёртвой»: папка пропала бы из UI, хотя её URI ещё записан.
 * * **Удаление источника не удаляет книги.** Они переходят в `isMissing`, чтобы история чтения,
 *   оценки и полки пережили отключение папки (ADR-010).
 * * **Очередь сканирования — порт, а не WorkManager.** [ScanScheduler] реализуется в `:app`,
 *   поэтому `:core:data` не зависит от модуля приложения и остаётся тестируемым на подставном
 *   фейке.
 */
@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val bookDao: BookDao,
    private val safTreeReader: SafTreeReader,
    private val scanScheduler: ScanScheduler,
    private val appClock: AppClock,
) : SourceRepository {

    /**
     * Список источников с живым числом книг.
     *
     * `observeBookCount` — отдельный Flow на каждый источник, а не поле `sources.documentCount`:
     * второе обновляет только сам скан, и после удаления/возврата файлов строка настроек долго
     * показывала бы устаревшее число. `flatMapLatest` пересобирает набор при изменении источников.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSources(): Flow<List<Source>> =
        sourceDao.observeAll().flatMapLatest { entities ->
            if (entities.isEmpty()) {
                flowOf(emptyList<Source>())
            } else {
                combine(
                    entities.map { entity ->
                        sourceDao.observeBookCount(entity.id).map { count -> entity to count }
                    },
                ) { pairs ->
                    pairs.map { (entity, bookCount) -> entity.toDomain(documentCount = bookCount) }
                }
            }
        }

    /**
     * Добавление папки SAF: persistable-разрешение → строка `sources` → первый скан.
     *
     * Первичный скан запускается сразу, как требует «Архитектура» §4.1: пустой экран после
     * добавления источника выглядел бы как поломка. `id` — UUID, он не зависит от пути и потому
     * переживает перемещение папки.
     */
    override suspend fun addSafSource(uri: String, displayName: String): AppResult<Source> {
        if (uri.isBlank()) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "treeUri", message = "URI папки не может быть пустым"),
            )
        }

        // SAF-порт сам возвращает AppResult, поэтому разворачиваются два уровня: отказ реализации
        // и неожиданное исключение приходят разными типами, а писать в БД нельзя ни в одном.
        val permissionOutcome = appRunCatching { safTreeReader.takePersistablePermission(uri) }
        if (permissionOutcome is AppResult.Failure) return permissionOutcome
        val permission = permissionOutcome.getOrNull()
        if (permission is AppResult.Failure) return permission

        val entity = SourceEntity(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = displayName,
            kind = io.github.bookshelf.core.database.model.SourceKind.SAF_TREE,
        )
        return appRunCatching {
            sourceDao.insert(entity)
            scanScheduler.enqueueScan(entity.id)
            entity.toDomain(documentCount = 0)
        }
    }

    override suspend fun addGrantedSource(uri: String, displayName: String): AppResult<Source> {
        if (uri.isBlank()) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "treeUri", message = "URI папки не может быть пустым"),
            )
        }
        // Право не запрашивается намеренно: у приложения уже есть доступ ко всем файлам, а
        // `takePersistablePermission` на адрес, который человек не выбирал, вернул бы отказ (30.09.2026).
        val entity = SourceEntity(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = displayName,
            kind = io.github.bookshelf.core.database.model.SourceKind.SAF_TREE,
        )
        return appRunCatching {
            sourceDao.insert(entity)
            scanScheduler.enqueueScan(entity.id)
            entity.toDomain(documentCount = 0)
        }
    }

    override suspend fun addDirectPathSource(path: String, displayName: String): AppResult<Source> {
        if (path.isBlank()) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "path", message = "Путь источника не может быть пустым"),
            )
        }
        val entity = SourceEntity(
            id = UUID.randomUUID().toString(),
            uri = path,
            displayName = displayName,
            kind = io.github.bookshelf.core.database.model.SourceKind.DIRECT_PATH,
        )
        return appRunCatching {
            sourceDao.insert(entity)
            scanScheduler.enqueueScan(entity.id)
            entity.toDomain(documentCount = 0)
        }
    }

    override suspend fun hasWriteAccess(sourceId: String): Boolean {
        val source = sourceDao.getById(sourceId) ?: return false
        return safTreeReader.hasWritePermission(source.uri)
    }

    /**
     * Мягкое удаление источника.
     *
     * Порядок обязателен: сначала книги помечаются пропавшими, потом исчезает строка источника.
     * Так при сбое между шагами останется источник с помеченными книгами, а не наоборот.
     */
    override suspend fun removeSource(sourceId: String): AppResult<Unit> {
        val found = appRunCatching { sourceDao.getById(sourceId) }
        if (found is AppResult.Failure) return found
        if (found.getOrNull() == null) return sourceNotFound(sourceId)

        return appRunCatching {
            // Сначала останавливаем уже поставленную работу: иначе удалённый источник может
            // быть повторно записан воркером между чтением книг и удалением строки.
            scanScheduler.cancelScan(sourceId)
            val books = bookDao.observePage(booksOfSource(sourceId)).first()
            markMissing(books.map { it.id }, since = appClock.nowMillis())
            sourceDao.delete(sourceId)
        }
    }

    override suspend fun rescan(sourceId: String): AppResult<Unit> = enqueueForEnabledSource(sourceId)

    /**
     * Проверки источника выполняются до постановки работы.
     *
     * `UPDATE`/`DELETE` в Room не сообщают, затронули ли они строку, поэтому «источника нет» и
     * «источник выключен» проверяются чтением: тихий успех для несуществующей папки сначала
     * показывал бы в UI «сканирование запущено», а на деле ничего бы не происходило.
     */
    @Suppress("ReturnCount")
    private suspend fun enqueueForEnabledSource(sourceId: String): AppResult<Unit> {
        val found = appRunCatching { sourceDao.getById(sourceId) }
        if (found is AppResult.Failure) return found
        val source = found.getOrNull() ?: return sourceNotFound(sourceId)
        if (!source.enabled) {
            return AppResult.failure(
                AppError.InvalidArgument(
                    what = "source",
                    message = "Источник $sourceId отключён",
                ),
            )
        }
        // Ручное пересканирование заменяет работу источника: запрос человека не должен теряться.
        return appRunCatching { scanScheduler.enqueueScan(sourceId, restart = true) }
    }

    /**
     * Пометить книги источника порциями.
     *
     * На старых системных SQLite `IN (:ids)` может содержать лишь несколько сотен параметров.
     * Без деления крупная полка дала бы синтаксическую ошибку на API 24 вместо мягкого удаления.
     */
    private suspend fun markMissing(bookIds: List<String>, since: Long) {
        bookIds.chunked(MISSING_IDS_BATCH_SIZE).forEach { ids ->
            bookDao.markMissing(ids, since = since)
        }
    }

    private fun sourceNotFound(sourceId: String): AppResult<Unit> = AppResult.failure(
        AppError.NotFound(what = "source", message = "Источник $sourceId не найден"),
    )

    /**
     * Выборка книг источника для `markMissing`.
     *
     * В `BookDao` нет запроса «id книг по источнику», а получить их нужно до удаления строки
     * источника. `@RawQuery` — единственный доступный путь без изменения контракта `:core:database`.
     */
    private fun booksOfSource(sourceId: String) =
        SimpleSQLiteQuery("SELECT * FROM books WHERE sourceId = ?", arrayOf<Any?>(sourceId))
}

/** Строка `sources` → доменная модель. Неизвестный `kind` трактуется как SAF-дерево. */
private fun SourceEntity.toDomain(documentCount: Int) = Source(
    id = id,
    uri = uri,
    displayName = displayName,
    kind = SourceKind.entries.firstOrNull { it.name == kind.name } ?: SourceKind.SAF_TREE,
    enabled = enabled,
    recursive = recursive,
    excludeGlobs = excludeGlobs.orEmpty(),
    lastScanAt = lastScanAt,
    documentCount = documentCount,
    accessLost = accessLost,
)

/** Ниже лимита параметров `IN` на API 24; конкретное значение не зависит от размера библиотеки. */
private const val MISSING_IDS_BATCH_SIZE = 500
