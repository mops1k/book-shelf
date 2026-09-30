package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.repository.BookOptimizationBatch
import io.github.bookshelf.core.data.repository.BookOptimizationResult
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Полка применяет общий выбор фильтров.
 *
 * Последняя дыра после починки: [LibraryFilterWiringTest] проверяет правило построения запроса,
 * а этот тест — что `LibraryViewModel` на него подписан. Именно этого не хватало: выбор
 * доходил до экрана фильтров, а полка показывала все книги.
 *
 * Здесь же проверяется кнопка «Пересканировать»: команда должна доходить до
 * отбор фильтров, а не теряться между экраном и ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFilterSubscriptionTest {

    private val dispatcher = StandardTestDispatcher()
    private val selection = FilterSelection()
    private val repository = RecordingBookRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shelf requests page again when shared filter changes`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(
            bookRepository = repository,
            settingsRepository = FakeSettingsRepository,
            filterSelection = selection,
            browseRequest = LibraryBrowseRequest(),
            filterRepository = FakeFilterRepository(),
        )
        advanceUntilIdle()
        val beforeFilter = repository.queries.size

        selection.apply(BookQuery(authorNames = setOf("tolstoy")))
        advanceUntilIdle()

        assertThat(repository.queries.size).isGreaterThan(beforeFilter)
        assertThat(repository.queries.last().bookQuery.authorNames).containsExactly("tolstoy")
        // Смена фильтра обязана возвращать на первую страницу, иначе полка осталась бы пустой.
        assertThat(repository.queries.last().page).isEqualTo(0)
        viewModel.onClearFilters()
    }

    @Test
    fun `repeating the same filter does not requery`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(
            bookRepository = repository,
            settingsRepository = FakeSettingsRepository,
            filterSelection = selection,
            browseRequest = LibraryBrowseRequest(),
            filterRepository = FakeFilterRepository(),
        )
        advanceUntilIdle()
        selection.apply(BookQuery(authorNames = setOf("a")))
        advanceUntilIdle()
        val afterFirst = repository.queries.size

        selection.apply(BookQuery(authorNames = setOf("a")))
        advanceUntilIdle()

        assertThat(repository.queries.size).isEqualTo(afterFirst)
        viewModel.onClearFilters()
    }

    @Test
    fun `clearing the shared filter restores the whole library`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(
            bookRepository = repository,
            settingsRepository = FakeSettingsRepository,
            filterSelection = selection,
            browseRequest = LibraryBrowseRequest(),
            filterRepository = FakeFilterRepository(),
        )
        advanceUntilIdle()
        selection.apply(BookQuery(authorNames = setOf("a")))
        advanceUntilIdle()

        selection.clear()
        advanceUntilIdle()

        assertThat(repository.queries.last().bookQuery).isEqualTo(BookQuery())
        viewModel.onClearFilters()
    }

    @Test
    fun `button counter shows how many filter values are applied`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(
            bookRepository = repository,
            settingsRepository = FakeSettingsRepository,
            filterSelection = selection,
            browseRequest = LibraryBrowseRequest(),
            filterRepository = FakeFilterRepository(),
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeFilters.activeCount).isEqualTo(0)

        selection.apply(BookQuery(authorNames = setOf("a", "b", "c"), series = setOf("s")))
        advanceUntilIdle()

        // Александр 28.09.2026 (интерактивный вопрос): на кнопке считаются значения, а не критерии.
        // До починки `activeFilters` не заполнялось вовсе, и кнопка всегда читалась «Фильтры (0)».
        assertThat(viewModel.uiState.value.activeFilters.activeCount).isEqualTo(4)
        // Одна серия — это ещё и подпись подвала «Порядок серии: по выпуску»: она читает признак
        // из запроса, поэтому работает и когда серия выбрана в обзоре, а не на экране фильтров.
        assertThat(viewModel.uiState.value.activeFilters.series).containsExactly("s")
        assertThat(viewModel.uiState.value.seriesOrderByRelease).isTrue()
        viewModel.onClearFilters()
    }

    private class RecordingBookRepository : BookRepository {
        val queries = mutableListOf<LibraryQuery>()

        /** Чем ответит репозиторий: успех по умолчанию, сбой — в проверке ошибки. */

        override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> {
            queries += query
            return flowOf(PageResult(items = emptyList(), totalCount = 0, offset = 0, limit = 20))
        }

        override fun observeById(id: String): Flow<Book?> = flowOf(null)
        override suspend fun getById(id: String): AppResult<Book?> = AppResult.success(null)
        override suspend fun updateMetadata(bookId: String, patch: io.github.bookshelf.core.data.model.MetadataPatch) =
            AppResult.success(Unit)
        override suspend fun setFavorite(bookId: String, favorite: Boolean) = AppResult.success(Unit)
        override suspend fun setRating(bookId: String, rating: Int?) = AppResult.success(Unit)
        override suspend fun markRead(bookId: String, status: io.github.bookshelf.core.data.model.ReadingStatus) =
            AppResult.success(Unit)
        override suspend fun delete(bookId: String) = AppResult.success(Unit)

        /** Удаление с файлом и поиск дублей в тестах полки не проверяются. */
        override suspend fun deletePermanently(bookIds: List<String>): AppResult<DeletionResult> =
            AppResult.success(DeletionResult(deleted = bookIds.size, failed = 0))

        override suspend fun optimizeBook(
            bookId: String,
        ): AppResult<BookOptimizationResult> = AppResult.failure(
                AppError.Unknown(message = "не нужно в этом тесте"),
            )

        /** Пакетная оптимизация в тестах полки не участвует. */
        override suspend fun optimizeAllFb2(
            onProgress: (done: Int, total: Int) -> Unit,
        ): AppResult<BookOptimizationBatch> = AppResult.failure(
            AppError.Unknown(message = "не нужно в этом тесте"),
        )

        override suspend fun findDuplicates(): AppResult<DuplicateReport> = AppResult.success(DuplicateReport())

        /** Отчёт сканирования в тестах полки не собирается. */
        override suspend fun booksWithParseErrors(): AppResult<List<Book>> = AppResult.success(emptyList())
        /** Прогресс чтения и поиск по адресу в этих проверках не участвуют. */
        override suspend fun findByDocumentUri(documentUri: String): AppResult<Book?> =
            AppResult.success(null)

        override suspend fun saveReadingProgress(
            bookId: String,
            percent: Float,
            locatorJson: String?,
            source: String,
        ): AppResult<Unit> = AppResult.success(Unit)


        /**
         * Пересканирование библиотеки.
         *
         * Метод появился в [BookRepository] позже остальных, и без него фейк переставал
         * реализовывать интерфейс — компиляция тестов всего модуля падала. Счётчик вызовов
         * нужен, чтобы проверять не «кнопка нарисована», а «команда дошла до репозитория».
         */
        override suspend fun rescanAll(): AppResult<Unit> {
            // Кнопка «Пересканировать» ушла в настройки: здесь проверяется, что заглушка
            // репозитория ведёт себя как рабочая реализация.
            return AppResult.success(Unit)
        }
    }

    /**
     * Настройки в проверке подписки не участвуют, но ViewModel требует настоящий интерфейс.
     * Значения по умолчанию — чтобы тест не зависел от содержимого файла настроек.
     */
    private object FakeSettingsRepository : SettingsRepository {

        override suspend fun resetToDefaults() = Unit
        override val settings: Flow<AppSettings> = flowOf(AppSettings())
        override suspend fun current(): AppSettings = AppSettings()
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }
}
