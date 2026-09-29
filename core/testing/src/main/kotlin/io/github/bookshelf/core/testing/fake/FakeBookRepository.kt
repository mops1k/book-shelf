package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    /** Сколько раз просили «пересканировать всё». */
    var rescanCalls: Int = 0
        private set

    /** Последний выставленный статус чтения; `null` — не выставляли. */
    var lastReadingStatus: ReadingStatus? = null
        private set

    /** Последняя выставленная оценка вместе с признаком «оценку снимали». */
    var lastRating: String? = null
        private set

    override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> = pageFlow

    override fun observeById(id: String): Flow<Book?> = byIdFlow

    override suspend fun getById(id: String): AppResult<Book?> = bookById

    override suspend fun updateMetadata(bookId: String, patch: MetadataPatch): AppResult<Unit> =
        AppResult.success(Unit)

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

    override suspend fun findDuplicates(): AppResult<DuplicateReport> = AppResult.success(report)

    override suspend fun rescanAll(): AppResult<Unit> {
        rescanCalls += 1
        return AppResult.success(Unit)
    }
}
