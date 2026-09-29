package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.Grouping as SettingsGrouping
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.datastore.model.SortOrder as SettingsSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Начальный порядок полки из настроек (docs/eink-ux.md §6.2).
 *
 * Проверяется связь, которой раньше не было: настройки «сортировка по умолчанию», «по убыванию» и
 * «группировка по умолчанию» существовали, но полка их не читала и открывалась всегда с одним и
 * тем же порядком. Здесь важно и обратное: настройка — живая (изменил её — полка перестроилась
 * сразу), но выбор, сделанный на самой полке, не откатывается эмиссиями, которые тройку порядка
 * не меняют.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDefaultOrderTest {

    private val dispatcher = StandardTestDispatcher()
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
    fun `shelf opens with the sort and grouping from settings`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakeSettingsRepository(
                flowOf(
                    appSettings(
                        sort = SettingsSortOrder.ADDED_AT,
                        descending = true,
                        grouping = SettingsGrouping.SERIES,
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        val query = repository.queries.last()
        assertThat(query.sort).isEqualTo(SortOrder.ADDED_AT)
        assertThat(query.descending).isTrue()
        assertThat(query.grouping).isEqualTo(Grouping.SERIES)
        // Порядок уходит и в состояние экрана: подвал полки показывает то же, что запрошено.
        assertThat(viewModel.uiState.value.sort).isEqualTo(SortOrder.ADDED_AT)
        assertThat(viewModel.uiState.value.grouping).isEqualTo(Grouping.SERIES)
    }

    @Test
    fun `documented defaults are applied without change`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSettingsRepository(flowOf(appSettings())))
        advanceUntilIdle()

        // Умолчание настроек — «Название» и без группировки: полка обязана открыться так же.
        assertThat(repository.queries.last().sort).isEqualTo(SortOrder.TITLE)
        assertThat(repository.queries.last().descending).isFalse()
        assertThat(viewModel.uiState.value.grouping).isEqualTo(Grouping.NONE)
    }

    @Test
    fun `shelf choice survives unrelated settings and gives way to a new default`() = runTest(dispatcher) {
        val settingsFlow = MutableStateFlow(appSettings())
        val viewModel = viewModel(FakeSettingsRepository(settingsFlow))
        advanceUntilIdle()
        viewModel.onSetSort(SortOrder.SIZE)
        advanceUntilIdle()

        // Любая другая настройка рождает новый AppSettings, но тройка порядка та же: выбор на
        // полке обязан устоять, иначе полка пересортировывалась бы при смене лимита кэша.
        settingsFlow.value = appSettings()
        advanceUntilIdle()
        assertThat(repository.queries.last().sort).isEqualTo(SortOrder.SIZE)

        // Изменившееся умолчание полка принимает сразу, без перезапуска: это и есть «применяется
        // сразу» для настройки порядка.
        settingsFlow.value = appSettings(
            sort = SettingsSortOrder.ADDED_AT,
            descending = true,
            grouping = SettingsGrouping.AUTHOR,
        )
        advanceUntilIdle()

        assertThat(repository.queries.last().sort).isEqualTo(SortOrder.ADDED_AT)
        assertThat(repository.queries.last().descending).isTrue()
        assertThat(repository.queries.last().grouping).isEqualTo(Grouping.AUTHOR)
        assertThat(viewModel.uiState.value.sort).isEqualTo(SortOrder.ADDED_AT)
        assertThat(viewModel.uiState.value.grouping).isEqualTo(Grouping.AUTHOR)
    }

    private fun viewModel(settings: SettingsRepository): LibraryViewModel = LibraryViewModel(
        bookRepository = repository,
        settingsRepository = settings,
        filterSelection = FilterSelection(),
        browseRequest = LibraryBrowseRequest(),
        filterRepository = FakeFilterRepository(),
    )

    private fun appSettings(
        sort: SettingsSortOrder = SettingsSortOrder.TITLE,
        descending: Boolean = false,
        grouping: SettingsGrouping = SettingsGrouping.NONE,
    ): AppSettings = AppSettings(
        library = LibrarySettings(sortOrder = sort, sortDescending = descending, grouping = grouping),
    )

    /** Полка, запоминающая запросы: проверяем, что до репозитория доходит именно порядок из настроек. */
    private class RecordingBookRepository : BookRepository {
        val queries = mutableListOf<LibraryQuery>()

        override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> {
            queries += query
            return flowOf(PageResult(items = emptyList(), totalCount = 0, offset = 0, limit = 500))
        }

        override fun observeById(id: String): Flow<Book?> = flowOf(null)
        override suspend fun getById(id: String): AppResult<Book?> = AppResult.success(null)
        override suspend fun updateMetadata(bookId: String, patch: MetadataPatch) = AppResult.success(Unit)
        override suspend fun setFavorite(bookId: String, favorite: Boolean) = AppResult.success(Unit)
        override suspend fun setRating(bookId: String, rating: Int?) = AppResult.success(Unit)
        override suspend fun markRead(bookId: String, status: ReadingStatus) = AppResult.success(Unit)
        override suspend fun delete(bookId: String) = AppResult.success(Unit)

        /** Удаление с файлом и поиск дублей в тестах полки не проверяются. */
        override suspend fun deletePermanently(bookIds: List<String>): AppResult<DeletionResult> =
            AppResult.success(DeletionResult(deleted = bookIds.size, failed = 0))

        override suspend fun findDuplicates(): AppResult<DuplicateReport> = AppResult.success(DuplicateReport())
        override suspend fun rescanAll() = AppResult.success(Unit)
    }

    /** Настройки как константный поток или как поток, которым тест управляет сам. */
    private class FakeSettingsRepository(
        private val stream: Flow<AppSettings>,
    ) : SettingsRepository {

        override val settings: Flow<AppSettings> = stream

        override suspend fun resetToDefaults() = Unit
        override suspend fun current(): AppSettings = AppSettings()
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }
}
