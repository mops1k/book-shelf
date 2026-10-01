package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.repository.BookOptimizationBatch
import io.github.bookshelf.core.data.repository.BookOptimizationResult
import io.github.bookshelf.core.data.repository.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield

/**
 * Фейк репозитория книг для тестов экранов.
 *
 * По правилу ADR-012 (свои типы не мокаются) — здесь вместо `mockk`: видно, что именно экран
 * запросил, и настраивается только то, что тест проверяет. Живёт в `:core:testing` — общем модуле
 * фейков проекта (каталогов `src/testFixtures` нет: на AGP 8.7.3 они не компилируют Kotlin).
 *
 * @param pageFlow что отдаёт [observePage]; тест подставляет свои книги, если они нужны.
 * @param byIdFlow что отдаёт [observeById]; по умолчанию «книги нет».
 */
class FakeBookRepository(
    var pageFlow: Flow<PageResult<Book>> = flowOf(
        PageResult(items = emptyList(), totalCount = 0, offset = 0, limit = 0),
    ),
    var byIdFlow: Flow<Book?> = flowOf(null),
) : BookRepository {

    /** Отчёт о дублях, который увидит экран. */
    var report: DuplicateReport = DuplicateReport()

    /** Итог удаления навсегда. */
    var deletionResult: DeletionResult = DeletionResult(deleted = 1, failed = 0)

    /** Пакеты книг, отправленные на удаление, в порядке обращений. */
    val deletedBatches: MutableList<List<String>> = mutableListOf()

    /** Что вернуть на [getById]. */
    var bookById: AppResult<Book?> = AppResult.success(null)

    /** Запросы, с которыми просили страницу книг: по ним видно, что фильтр дошёл до данных. */
    val pageQueries: MutableList<LibraryQuery> = mutableListOf()

    /** Сколько раз просили «пересканировать всё». */
    var rescanCalls: Int = 0
        private set

    /** Последний выставленный статус чтения; `null` — не выставляли. */
    var lastReadingStatus: ReadingStatus? = null
        private set

    /** Последняя выставленная оценка вместе с признаком «оценку снимали». */
    var lastRating: String? = null
        private set

    override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> {
        pageQueries += query
        return pageFlow
    }

    override fun observeById(id: String): Flow<Book?> = byIdFlow

    override suspend fun getById(id: String): AppResult<Book?> = bookById

    /** Что вернуть на [updateMetadata]: тест может проверить и отказ сохранения. */
    var updateMetadataResult: AppResult<Unit> = AppResult.success(Unit)

    /** Патчи правки метаданных, дошедшие до репозитория: по ним видно, что именно сохраняли. */
    val metadataPatches: MutableList<MetadataPatch> = mutableListOf()

    override suspend fun updateMetadata(bookId: String, patch: MetadataPatch): AppResult<Unit> {
        metadataPatches += patch
        return updateMetadataResult
    }

    override suspend fun setFavorite(bookId: String, favorite: Boolean): AppResult<Unit> =
        AppResult.success(Unit)

    override suspend fun setRating(bookId: String, rating: Int?): AppResult<Unit> {
        lastRating = if (rating == null) "снята" else rating.toString()
        return AppResult.success(Unit)
    }

    override suspend fun markRead(bookId: String, status: ReadingStatus): AppResult<Unit> {
        lastReadingStatus = status
        return AppResult.success(Unit)
    }

    override suspend fun delete(bookId: String): AppResult<Unit> = AppResult.success(Unit)

    override suspend fun deletePermanently(bookIds: List<String>): AppResult<DeletionResult> {
        deletedBatches += bookIds
        return AppResult.success(deletionResult)
    }

    override suspend fun findByDocumentUri(documentUri: String): AppResult<Book?> =
        AppResult.success(null)

    override suspend fun saveReadingProgress(
        bookId: String,
        percent: Float,
        locatorJson: String?,
        source: String,
    ): AppResult<Unit> = AppResult.success(Unit)

    /** Итог, который вернёт оптимизация; тесты подставляют свой. */
    var optimization: AppResult<BookOptimizationResult> = AppResult.success(
        BookOptimizationResult(savedBytes = 0, beforeBytes = 0, afterBytes = 0, images = 0),
    )

    /** Книги, у которых просили оптимизацию. */
    val optimized = mutableListOf<String>()

    override suspend fun optimizeBook(bookId: String): AppResult<BookOptimizationResult> {
        optimized += bookId
        return optimization
    }

    /** Итог пакетной оптимизации; тесты подставляют свой. */
    var optimizationBatch: AppResult<BookOptimizationBatch> = AppResult.success(
        BookOptimizationBatch(
            total = 0,
            optimized = 0,
            skipped = 0,
            failed = 0,
            savedBytes = 0,
            stoppedByReader = false,
        ),
    )

    /** Прогресс, который фейк сообщает во время пакетной оптимизации, в порядке сообщений. */
    var batchProgress: List<Pair<Int, Int>> = emptyList()

    /**
     * Если задано — после каждого сообщения прогресса работа ждёт этот сигнал.
     *
     * Нужно тесту на остановку: без точки ожидания прогон успевал завершиться за один `runCurrent`,
     * и проверить отмену было нечем.
     */
    var batchGate: CompletableDeferred<Unit>? = null

    /** Сколько раз запускали пакетную оптимизацию: по этому видно, что второй запуск не начался. */
    var batchRuns: Int = 0
        private set

    /** Отменяли ли пакетную оптимизацию: по этому видно, что экран действительно прервал работу. */
    var batchCancelled: Boolean = false
        private set

    /**
     * Пакетная оптимизация: сообщает заданный прогресс и отдаёт заданный итог.
     *
     * Между сообщениями стоит `yield()`: без точки приостановки отмена корутины не наблюдалась бы,
     * и тест на кнопку «Остановить» проверял бы пустоту.
     */
    override suspend fun optimizeAllFb2(
        onProgress: (done: Int, total: Int) -> Unit,
    ): AppResult<BookOptimizationBatch> {
        batchRuns += 1
        try {
            for ((done, total) in batchProgress) {
                onProgress(done, total)
                yield()
                batchGate?.await()
            }
        } catch (cancellation: CancellationException) {
            batchCancelled = true
            throw cancellation
        }
        return optimizationBatch
    }

    /** Что вернуть на [booksWithParseErrors]. */
    var parseErrors: AppResult<List<Book>> = AppResult.success(emptyList())

    override suspend fun booksWithParseErrors(): AppResult<List<Book>> = parseErrors

    override suspend fun findDuplicates(): AppResult<DuplicateReport> = AppResult.success(report)

    /** Копии, найденные сканом: тест кладёт сюда то, что должен увидеть экран «Копии книг». */
    var copies: List<io.github.bookshelf.core.data.model.BookCopy> = emptyList()

    override fun copies(): Flow<List<io.github.bookshelf.core.data.model.BookCopy>> = flowOf(copies)

    /** Сколько копий «удалено»: проверяет экран, не трогая файловую систему. */
    var copiesRemoved: Int = 0

    /** Сколько копий «не удалось убрать»: так проверяется честный отчёт о частичном отказе. */
    var copiesFailed: Int = 0

    /** Отказ всей операции: экран обязан показать ошибку, а не «убрано 0». */
    var copiesError: io.github.bookshelf.core.common.error.AppError? = null

    override suspend fun removeCopies(
        copyUris: List<String>,
    ): AppResult<DeletionResult> {
        copiesError?.let { error -> return AppResult.failure(error) }
        copiesRemoved += copyUris.size - copiesFailed
        return AppResult.success(
            DeletionResult(deleted = copyUris.size - copiesFailed, failed = copiesFailed),
        )
    }

    override suspend fun rescanAll(): AppResult<Unit> {
        rescanCalls += 1
        return AppResult.success(Unit)
    }
}
