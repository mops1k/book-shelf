package io.github.bookshelf.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.sync.afterFileChange
import io.github.bookshelf.core.data.sync.booksToDelete
import io.github.bookshelf.core.data.sync.shouldRetryScan
import io.github.bookshelf.core.data.duplicates.BookCopyDetector
import io.github.bookshelf.core.data.duplicates.KnownBookFile
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.data.sync.BookUserDataMover
import io.github.bookshelf.core.database.dao.BookCopyDao
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.ScanRunDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.entity.BookCopyEntity
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.ScanRunEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.ScanRunStatus
import io.github.bookshelf.core.storage.saf.DocumentEntry
import io.github.bookshelf.core.storage.saf.SafTreeReader
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import io.github.bookshelf.core.storage.saf.bookIdOfDocumentId
import io.github.bookshelf.core.storage.saf.SourceRef
import io.github.bookshelf.core.storage.scan.BookFileFilter
import io.github.bookshelf.core.storage.scan.ScanCursor
import io.github.bookshelf.core.storage.scan.ScanCursorCodec
import io.github.bookshelf.core.storage.scan.ScanOptions
import io.github.bookshelf.core.storage.scan.TreeWalker
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Сканирование источника (docs/architecture.md §4.5, ADR-010).
 *
 * Четыре решения, ради которых работа написана именно так:
 *
 * **1. Работа уникальна на источник, `KEEP`.** Ставит её [WorkManagerScanScheduler]; здесь это
 * уже не влияет, но два параллельных скана одного дерева писали бы в `books` одновременно.
 *
 * **2. Скан возобновляем.** После каждого батча курсор обхода пишется в `scan_runs`, поэтому
 * убийство процесса и `Service.onTimeout` на Android 15 (лимит `dataSync` — 6 часов за 24 часа)
 * приводят к паузе, а не к тому, чтобы начинать с нуля. Возобновление — это повторный запуск
 * с той же точки, а не отдельный код.
 *
 * **3. Ключ книги — `documentId`, а не путь.** При переименовании файла его `documentId` в SAF
 * не меняется: книга остаётся той же с обновлёнными метаданными, а не задваивается.
 *
 * **4. Пропавшие книги не удаляются, а помечаются.** История чтения и полки дороже дискового
 * места: без `isMissing` коллекции пользователя рассыпались бы после переноса папки.
 */
@HiltWorker
class ScanSourceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sourceDao: SourceDao,
    private val scanRunDao: ScanRunDao,
    private val bookDao: BookDao,
    /** Удаление книг вместе со связями: с автосинхронизацией оно происходит при каждом проходе. */
    private val bookCleanupDao: io.github.bookshelf.core.database.dao.BookCleanupDao,
    /** Связи «файл-копия → книга»: копии, созданные не нами, книгой не считаются. */
    private val bookCopyDao: BookCopyDao,
    /** Распознавание копии: решает содержимое, а не имя файла. */
    private val copyDetector: BookCopyDetector,
    /** Перенос данных человека с копии на книгу: прогресс, статус, полки не должны пропасть. */
    private val userDataMover: BookUserDataMover,
    /** Удаление файлов копий, когда книга исчезла: файл, оставшийся навсегда, — тот же дефект. */
    private val treeWriter: SafTreeWriter,
    private val treeReader: SafTreeReader,
    private val treeWalker: TreeWalker,
    private val metadataScheduler: io.github.bookshelf.core.data.sync.MetadataScheduler,
    private val fileChangeHandler: io.github.bookshelf.core.data.sync.BookFileChangeHandler,
    /** Кэш обложек: обложка изменившегося файла не должна остаться старой. */
    private val coverCache: io.github.bookshelf.core.covers.CoverCache,
    /** Сессия чтения: пока читалка открыта, обход уступает ей процессор. */
    private val readerSession: ReaderSession,
    private val clock: AppClock,
) : CoroutineWorker(appContext, params) {

    /** Идентификаторы книг, встреченных в текущем проходе. */
    private val seenIds = mutableSetOf<String>()

    /**
     * Адреса копий, встреченных в текущем проходе.
     *
     * Отдельно от [seenIds]: у копии нет строки в `books`, и по книгам её не увидеть. Нужно, чтобы
     * после полного прохода убрать связи, файлов которых больше нет.
     */
    private val seenCopyUris = mutableSetOf<String>()
    private var filesSeen = 0
    private var added = 0
    private var updated = 0

    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return Result.failure()
        val source = sourceDao.getById(sourceId) ?: return Result.failure()
        // Читалка открыта — обход не начинаем вовсе. Проверка стоит ДО создания прогона: иначе в
        // базе осталась бы запись «идёт скан», которого нет, и экран показывал бы вечный прогресс.
        // Работа не теряется: прогон поставит следующий возврат в приложение или расписание.
        if (readerSession.isActive()) {
            Log.w("BookShelf", "скан отложен: открыта читалка")
            return Result.success()
        }
        val run = resumeOrCreateRun(sourceId)
        // Возобновлённый прогон видит только хвост дерева: список встреченных книг в нём неполный,
        // и удалять по нему нельзя. 30.09.2026 так исчезла вся полка: прогон ушёл в PAUSED на
        // `UNIQUE books.documentUri`, повтор продолжил его с курсора и удалил 79 «не встреченных» книг.
        val resumed = run.cursorDocumentId != null

        return try {
            val result = walkUntilFinished(source, run)
            when (result) {
                is ScanOutcome.Failed -> {
                    // Курсор сохранён, прогон не терминален: следующий скан продолжит с него.
                    scanRunDao.finish(run.id, ScanRunStatus.PAUSED, null, result.reason)
                    // Повторы ограничены: без предела работа уходит в backoff, а `KEEP` не даёт
                    // поставить новый скан — кнопка «Пересканировать» перестаёт действовать.
                    if (shouldRetryScan(runAttemptCount)) Result.retry() else Result.success()
                }

                is ScanOutcome.Deferred -> {
                    // PAUSED с причиной: прогон не терминален, курсор на месте, статистика источника
                    // не переписывается — «N книг» остаётся прежним, пока обход не закончится.
                    scanRunDao.finish(run.id, ScanRunStatus.PAUSED, null, result.reason)
                    Log.w("BookShelf", "скан остановлен: ${result.reason}")
                    Result.success()
                }

                is ScanOutcome.Completed -> {
                    deleteMissing(sourceId, resumed)
                    forgetUnseenCopies(sourceId, resumed)
                    val finishedAt = clock.nowMillis()
                    scanRunDao.finish(
                        id = run.id,
                        status = ScanRunStatus.COMPLETED,
                        finishedAt = finishedAt,
                        error = null,
                    )
                    // Итог прогона в сам источник: настройки и онбординг показывают по нему
                    // «N книг» вместо «ещё не сканировалось», а счётчик книг берётся из базы,
                    // а не из числа файлов в последнем батче.
                    sourceDao.updateScanStats(
                        id = sourceId,
                        at = finishedAt,
                        documents = bookDao.countBySource(sourceId),
                    )
                    Result.success(workDataOf(KEY_SOURCE_ID to sourceId))
                }
            }
        } catch (cancelled: CancellationException) {
            // Отмена — не ошибка. Прогон остаётся PAUSED со своим курсором, и возобновление
            // продолжит с того же места. Помечать его FAILED значило бы потерять прогресс.
            scanRunDao.finish(run.id, ScanRunStatus.PAUSED, null, null)
            throw cancelled
        } catch (error: Throwable) {
            scanRunDao.finish(run.id, ScanRunStatus.FAILED, clock.nowMillis(), error.message)
            // Предел повторов тот же, что и у исчерпания обхода: без него неожиданная ошибка
            // крутила бы работу бесконечно, уводила её в экспоненциальный backoff и жгла батарею
            // на каждом шаге. Прогон уже помечен FAILED, курсор не потерян — следующий скан
            // начнёт новый прогон.
            if (shouldRetryScan(runAttemptCount)) Result.retry() else Result.success()
        }
    }

    private sealed interface ScanOutcome {
        data object Completed : ScanOutcome
        data class Failed(val reason: String) : ScanOutcome

        /**
         * Обход остановлен, потому что открылась читалка.
         *
         * Отдельный исход, а не [Failed]: ничего не сломалось, и повторять с задержкой не нужно —
         * курсор сохранён, а работу продолжит следующий запуск.
         */
        data class Deferred(val reason: String) : ScanOutcome
    }

    /**
     * Обход до конца дерева с сохранением курсора после каждого батча.
     *
     * `walk` отдаёт управление на один батч, поэтому цикл и есть продолжение скана: именно так
     * возобновляемость выглядит снаружи.
     */
    private suspend fun walkUntilFinished(source: SourceEntity, run: ScanRunEntity): ScanOutcome {
        val options = scanOptionsFor(source)
        var cursor = run.cursorDocumentId?.let(ScanCursorCodec::decode)

        while (true) {
            currentCoroutineContext().ensureActive()
            // Проверка между батчами: обход длинной библиотеки идёт минутами, и читалка может
            // открыться уже посреди него. Курсор сохранён предыдущим батчем, поэтому остановка
            // ничего не теряет — следующий запуск продолжит с этого места.
            if (readerSession.isActive()) return ScanOutcome.Deferred("открыта читалка")
            val step = treeWalker.walk(source.toRef(), cursor, options) { batch, resumeFrom ->
                storeBatch(source, batch, resumeFrom, run)
            }
            if (step is io.github.bookshelf.core.common.error.AppResult.Failure) {
                return ScanOutcome.Failed(step.error.message ?: "Не удалось прочитать дерево")
            }
            val next = (step as io.github.bookshelf.core.common.error.AppResult.Success).value
            cursor = next ?: return ScanOutcome.Completed
        }
    }

    private suspend fun storeBatch(
        source: SourceEntity,
        batch: List<DocumentEntry>,
        resumeFrom: ScanCursor,
        run: ScanRunEntity,
    ) {
        batch.forEach { entry -> storeEntry(source, entry) }
        scanRunDao.updateProgress(
            id = run.id,
            cursorDocumentId = ScanCursorCodec.encode(resumeFrom),
            filesSeen = filesSeen,
            added = added,
            updated = updated,
            removed = 0,
            failedCount = 0,
        )
        setProgress(
            workDataOf(
                KEY_SOURCE_ID to source.id,
                PROGRESS_SEEN to filesSeen,
                PROGRESS_ADDED to added,
                PROGRESS_UPDATED to updated,
            ),
        )
    }

    /**
     * Вставка или обновление одной книги.
     *
     * Неизменность файла определяется по размеру и времени изменения: полное перечитывание
     * содержимого на каждом скане означало бы читать 2000 файлов ради проверки, что ничего
     * не изменилось.
     */
    private suspend fun storeEntry(source: SourceEntity, entry: DocumentEntry) {
        // Строка ищется по адресу документа, а не только по вычисленному id: адрес уникален, а id
        // мог быть посчитан прежним способом — до 30.09.2026 OPDS-загрузка хэшировала весь URI
        // вместо идентификатора документа. Без этой проверки скан падал на
        // `UNIQUE books.documentUri`, прогон уходил в PAUSED и повторялся по кругу.
        val existing = bookDao.getById(bookIdOf(entry))
            ?: bookDao.findByDocumentUri(treeReader.documentUri(source.uri, entry.documentId))
        val id = existing?.id ?: bookIdOf(entry)
        // Копия уже известной книги книгой не считается. Так ведёт себя FBReader: он копирует файл
        // в `Books/<дата>/` внутри нашей библиотеки (его настройка «Куда сохранять книги» указывает
        // на ту же папку, что мы сканируем), и в полке появлялась вторая такая же книга
        // (30.09–01.10.2026, 92 вместо 91). Решает содержимое, а не имя: копию читалка называет по
        // названию книги — `Предыстория. Книга 1.fb2.zip` вместо `… [=Истоки].fb2.zip`, — поэтому
        // прежнее правило «совпали имя файла и размер» её не поймало.
        val candidateUri = treeReader.documentUri(source.uri, entry.documentId)
        val entrySize = entry.sizeBytes
        if (entrySize != null && entrySize > 0L) {
            val knownCopy = bookCopyDao.findByUri(candidateUri)
            if (knownCopy != null && knownCopy.sizeBytes == entrySize &&
                knownCopy.lastModified == entry.lastModified
            ) {
                // Раньше уже распознали: содержимое то же, читать файл заново незачем. Строку книги
                // (состояние до этого исправления) убираем: копия книгой не считается.
                seenCopyUris += candidateUri
                existing?.let { copy -> dropCopy(copy, knownCopy.bookId) }
                return
            }
            if (knownCopy != null) {
                // Файл изменился — возможно, это уже другая книга: решаем заново.
                bookCopyDao.deleteByUri(candidateUri)
            }
            // Сравниваем только с теми книгами, которые идут РАНЬШЕ в порядке оригинала: иначе
            // настоящая книга была бы объявлена копией более поздней (порядок задаёт запрос:
            // дата добавления, затем путь).
            val sameSize = bookCopyDao.findBooksBySize(entrySize)
            val candidates = existing?.let { me -> sameSize.takeWhile { book -> book.id != me.id } }
                ?: sameSize
            if (candidates.isNotEmpty()) {
                copyDetector.findOriginal(
                    candidateUri = candidateUri,
                    candidateSize = entrySize,
                    known = candidates.map { book ->
                        KnownBookFile(
                            id = book.id,
                            documentUri = book.documentUri,
                            sizeBytes = book.sizeBytes,
                            contentHash = book.contentHash,
                        )
                    },
                    remember = { bookId, hash -> bookCopyDao.rememberContentHash(bookId, hash) },
                )?.let { match ->
                    existing?.let { copy -> dropCopy(copy, match.originalBookId) }
                    bookCopyDao.upsert(
                        BookCopyEntity(
                            copyDocumentUri = candidateUri,
                            bookId = match.originalBookId,
                            sourceId = source.id,
                            sizeBytes = entrySize,
                            lastModified = entry.lastModified ?: 0L,
                            contentHash = match.contentHash,
                        ),
                    )
                    seenCopyUris += candidateUri
                    Log.w(
                        "BookShelf",
                        "копия книги не заведена второй книгой: ${entry.displayName} → " +
                            match.originalBookId,
                    )
                    return
                }
            }
        }
        seenIds += id
        if (existing == null) {
            bookDao.insert(newBook(source, id, entry))
            added++
            // Разбор ставится только для новых книг: уже разобранная книга не должна ждать
            // работу в очереди заново, а смена файла вызывает MetadataWorker отдельно.
            metadataScheduler.enqueueMetadata(id)
        } else {
            val unchanged = existing.sizeBytes == entry.sizeBytes &&
                existing.lastModified == entry.lastModified
            if (unchanged) {
                bookDao.touchLastSeen(listOf(id), clock.nowMillis())
            } else {
                metadataScheduler.enqueueMetadata(id)
                // Файл изменился — обложка могла стать другой. Кэш сбрасывается здесь, а не в
                // воркере разбора: там уже не отличить «обложка ещё актуальна» от «её надо взять
                // заново», а решает это именно скан, который видит смену размера и времени файла.
                coverCache.invalidate(id)
                // Обновление строки вынесено в `afterFileChange`: там же сбрасывается состояние
                // разбора. Без этого `MetadataWorker` выходил сразу (`parseState = OK`) и книга
                // оставалась с метаданными прежнего файла.
                bookDao.update(
                    existing.afterFileChange(
                        documentUri = treeReader.documentUri(source.uri, entry.documentId),
                        relativePath = treeReader.relativePath(source.uri, entry.documentId),
                        fileName = entry.displayName,
                        extension = BookFileFilter.detectExtension(entry.displayName),
                        mimeType = entry.mimeType,
                        sizeBytes = entry.sizeBytes ?: existing.sizeBytes,
                        lastModified = entry.lastModified ?: existing.lastModified,
                        now = clock.nowMillis(),
                    ),
                )
                // Порядок обязателен: сначала запись обновлённых фактов файла, потом решение о
                // данных человека. Наоборот запись с прежней оценкой перезаписала бы очистку.
                //
                // Результат проверяется и попадает в журнал: сброс данных — решение, которое
                // может сорваться (занятая база, отменённая корутина), и молчаливое «не сбросилось»
                // оставило бы прежнюю оценку рядом с новым файлом, не оставив следов.
                fileChangeHandler.onFileChanged(id).errorOrNull()?.let { error ->
                    Log.w(
                        "BookShelf",
                        "данные книги $id не сброшены при смене файла: ${error.message}",
                    )
                }
                updated++
            }
        }
        filesSeen++
    }

    /**
     * Убрать строку книги, которая оказалась копией файла, и перенести её данные оригиналу.
     *
     * Так подчищается состояние до этого исправления: 30.09–01.10.2026 копия уже успела стать
     * отдельной книгой в полке (92 книги вместо 91), и её прогресс принадлежал ей, а не книге
     * человека. Данные переносятся до удаления строки: после удаления связей уже не найти.
     */
    private suspend fun dropCopy(copy: BookEntity, originalId: String) {
        userDataMover.move(fromBookId = copy.id, toBookId = originalId)
        bookCleanupDao.deleteWithLinks(listOf(copy.id))
        coverCache.invalidate(copy.id)
        Log.w("BookShelf", "копия «${copy.fileName}» слита с книгой $originalId")
    }

    private fun newBook(source: SourceEntity, id: String, entry: DocumentEntry): BookEntity {
        val now = clock.nowMillis()
        // До разбора метаданных заголовок берётся из имени файла: полка не должна быть пустой,
        // пока MetadataWorker ещё не отработал.
        val title = entry.displayName.substringBeforeLast('.', entry.displayName).trim()
        return BookEntity(
            id = id,
            sourceId = source.id,
            documentUri = treeReader.documentUri(source.uri, entry.documentId),
            relativePath = treeReader.relativePath(source.uri, entry.documentId),
            fileName = entry.displayName,
            extension = BookFileFilter.detectExtension(entry.displayName),
            mimeType = entry.mimeType ?: "",
            sizeBytes = entry.sizeBytes ?: 0L,
            lastModified = entry.lastModified ?: now,
            title = title.ifEmpty { entry.displayName },
            sortTitle = title.ifEmpty { entry.displayName },
            // Автора до разбора нет: пустая строка честнее выдуманного имени файла.
            authorsText = "",
            addedAt = now,
            updatedAt = now,
            lastSeenAt = now,
        )
    }

    /**
     * Удаляет записи книг, которых не было в полном проходе.
     *
     * Решение Александра (28.09.2026): книга, удалённая с устройства, должна исчезнуть с полки,
     * а не остаться «пропавшей» навсегда. Записи удаляются только после **успешного** завершения
     * обхода (`ScanOutcome.Completed`), поэтому недоступный источник, у которого провалился обход,
     * библиотеку не вычистит: там срабатывает отдельная ветка с пометкой `accessLost`.
     * Вместе с записью убирается её обложка — иначе кэш копил бы картинки удалённых книг.
     *
     * Что именно удалять, решает [booksToDelete]: возобновлённый проход и проход, не увидевший
     * ни одного файла, не удаляют ничего.
     *
     * Порциями по [MARK_BATCH]: на API 24 SQLite ограничивает число параметров в `IN`, и попытка
     * удалить сразу две тысячи книг упала бы с ошибкой на середине.
     */
    private suspend fun deleteMissing(sourceId: String, resumed: Boolean) {
        val gone = booksToDelete(
            allIds = bookDao.idsBySource(sourceId),
            seenIds = seenIds,
            filesSeen = filesSeen,
            resumed = resumed,
        )
        // Копии удаляемой книги уходят с диска: иначе на устройстве навсегда остался бы файл,
        // который никому не принадлежит, а следующий скан завёл бы по нему книгу заново
        // (решение Александра 01.10.2026). Файл удаляется до строки: после удаления книги связь
        // снимется каскадом, и найти копии будет уже нечем.
        val copies = gone.flatMap { id -> bookCopyDao.getByBookId(id) }
        copies.forEach { copy ->
            treeWriter.delete(copy.copyDocumentUri).errorOrNull()?.let { error ->
                // Провайдер отказал — честно сказать в журнал. Такой файл снова станет книгой:
                // скрывать его навсегда нельзя, иначе он останется невидимым мусором.
                Log.w("BookShelf", "копия ${copy.copyDocumentUri} не удалена: ${error.message}")
            }
        }
        gone.chunked(MARK_BATCH).forEach { batch ->
            if (batch.isEmpty()) return@forEach
            bookCleanupDao.deleteWithLinks(batch)
            batch.forEach { id -> coverCache.invalidate(id) }
        }
    }

    /**
     * Убрать связи с копиями, которых больше нет в источнике.
     *
     * Полный проход увидел все файлы дерева: если копии среди них не было, её файл удалили, и связь
     * с книгой стала мусором. Возобновлённый проход видит только хвост дерева, поэтому по нему
     * ничего не удаляется — по той же причине, что и для книг ([booksToDelete]).
     */
    private suspend fun forgetUnseenCopies(sourceId: String, resumed: Boolean) {
        if (resumed) return
        bookCopyDao.deleteBySourceExcept(sourceId, seenCopyUris.toList())
    }

    private suspend fun resumeOrCreateRun(sourceId: String): ScanRunEntity {
        val resumed = scanRunDao.findResumable(sourceId)
        if (resumed != null) {
            scanRunDao.finish(resumed.id, ScanRunStatus.RUNNING, null, null)
            return resumed
        }
        val run = ScanRunEntity(
            id = UUID.randomUUID().toString(),
            sourceId = sourceId,
            startedAt = clock.nowMillis(),
            status = ScanRunStatus.RUNNING,
        )
        scanRunDao.insert(run)
        return run
    }

    private fun scanOptionsFor(source: SourceEntity): ScanOptions = ScanOptions(
        excludeHidden = true,
        excludeGlobs = source.excludeGlobs.orEmpty(),
        extensions = BookFileFilter.SUPPORTED_EXTENSIONS,
    )

    private fun SourceEntity.toRef() = SourceRef(
        id = id,
        treeUri = uri,
        displayName = displayName,
        // SourceKind существует в двух слоях (БД и хранение). Маппинг явный: иначе
        // новый вид источника в одном слое молча потерялся бы в другом.
        kind = when (kind) {
            io.github.bookshelf.core.database.model.SourceKind.SAF_TREE ->
                io.github.bookshelf.core.storage.saf.SourceKind.SAF_TREE
            io.github.bookshelf.core.database.model.SourceKind.DIRECT_PATH ->
                io.github.bookshelf.core.storage.saf.SourceKind.DIRECT_PATH
            io.github.bookshelf.core.database.model.SourceKind.OPDS ->
                io.github.bookshelf.core.storage.saf.SourceKind.OPDS
        },
        recursive = recursive,
        excludeGlobs = excludeGlobs.orEmpty(),
    )

    /**
     * Идентификатор книги: `documentId` уникален в дереве, а путь меняется при переименовании.
     * Префикс имени файла оставлен, чтобы идентификатор был читаемым в отчёте и `logcat`.
     */
    /**
     * Идентификатор книги по документу.
     *
     * Считается общим помощником `:core:storage`: OPDS-загрузка обязана получить ровно такой же
     * id для того же файла, иначе одна книга попадала бы в библиотеку дважды.
     */
    private fun bookIdOf(entry: DocumentEntry): String = bookIdOfDocumentId(entry.documentId)

    companion object {
        const val KEY_SOURCE_ID: String = "sourceId"
        const val KEY_FORCE_RESCAN: String = "forceRescan"

        /** Уникальное имя работы на источник: два скана одного дерева не должны идти вместе. */
        fun uniqueWorkName(sourceId: String): String = "scan-$sourceId"

        /** Общий тег для наблюдения за всеми сканами из экрана прогресса. */
        const val WORK_TAG: String = "scan"

        const val PROGRESS_SEEN: String = "seen"
        const val PROGRESS_ADDED: String = "added"
        const val PROGRESS_UPDATED: String = "updated"

        const val BOOK_ID_PREFIX: String = "doc:"
        const val MARK_BATCH: Int = 500
    }
}
