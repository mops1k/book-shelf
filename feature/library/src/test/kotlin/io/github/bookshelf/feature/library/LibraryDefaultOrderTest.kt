package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.core.common.testing.FakeFeatureFlags
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.repository.BookOptimizationBatch
import io.github.bookshelf.core.data.repository.BookOptimizationResult
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
 * Начальный порядок полки из настроек («E-ink UX» §6.2).
 *
 * Проверяется связь, которой раньше не было: настройки «сортировка по умолчанию», «по убыванию» и
 * «группировка по умолчанию» существовали, но полка их не читала и открывалась всегда с одним и
 * тем же порядком. Здесь важно и обратное: настройка — живая (изменил её — полка перестроилась
 * сразу), но выбор, сделанный на самой полке, не откатывается эмиссиями, которые тройку порядка
 * не меняют.
 *
 * С 08.10.2026 выбор на полке пишется в эти же настройки (решение Александра): раньше он жил «на
 * время» и после перезапуска приложения полка возвращалась к прежнему умолчанию — пользователь
 * видел не тот порядок, который выбирал.
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
    fun `the catalogs entry follows the build flag`() = runTest(dispatcher) {
        // В сборке без OPDS (`ENABLE_OPDS = false`, ADR-006) входа в каталоги быть не должно:
        // кнопка, ведущая в незарегистрированный маршрут, хуже отсутствующей кнопки.
        val withoutOpds = viewModel(
            FakeSettingsRepository(flowOf(appSettings())),
            featureFlags = FakeFeatureFlags(opdsEnabled = false),
        )
        advanceUntilIdle()
        assertThat(withoutOpds.uiState.value.catalogsEnabled).isFalse()

        val withOpds = viewModel(
            FakeSettingsRepository(flowOf(appSettings())),
            featureFlags = FakeFeatureFlags(opdsEnabled = true),
        )
        advanceUntilIdle()
        assertThat(withOpds.uiState.value.catalogsEnabled).isTrue()
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

        // Выбор с полки записан в настройки, поэтому их следующая эмиссия несёт уже SIZE: полка
        // от неё не пересортировывается и остаётся в выбранном порядке.
        settingsFlow.value = appSettings(sort = SettingsSortOrder.SIZE)
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

    @Test
    fun `the order chosen on the shelf is written to the settings`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(MutableStateFlow(appSettings()))
        val viewModel = viewModel(settings)
        advanceUntilIdle()

        viewModel.onSetSort(SortOrder.LAST_OPENED)
        advanceUntilIdle()

        // Выбор на полке — это и есть настройка «Сортировка по умолчанию»: он виден на экране
        // настроек и переживает перезапуск (решение Александра 08.10.2026). Раньше он жил «на время»
        // и после перезапуска полка возвращалась к прежнему умолчанию — «Прогресс», из-за чего
        // первым показывался не тот, кого открывали последним.
        assertThat(settings.updated.last().library.sortOrder)
            .isEqualTo(SettingsSortOrder.LAST_OPENED)

        viewModel.onSetSortDirection(descending = false)
        advanceUntilIdle()
        assertThat(settings.updated.last().library.sortDescending).isFalse()

        viewModel.onSetGrouping(Grouping.AUTHOR)
        advanceUntilIdle()
        assertThat(settings.updated.last().library.grouping).isEqualTo(SettingsGrouping.AUTHOR)
    }

    private fun viewModel(
        settings: SettingsRepository,
        featureFlags: FeatureFlags = FakeFeatureFlags(),
    ): LibraryViewModel = LibraryViewModel(
        bookRepository = repository,
        settingsRepository = settings,
        filterSelection = FilterSelection(),
        browseRequest = LibraryBrowseRequest(),
        filterRepository = FakeFilterRepository(),
        featureFlags = featureFlags,
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

        override fun observeSeriesBooks(seriesName: String): Flow<List<Book>> = flowOf(emptyList())
        override suspend fun getById(id: String): AppResult<Book?> = AppResult.success(null)
        override suspend fun updateMetadata(bookId: String, patch: MetadataPatch) = AppResult.success(Unit)
        override suspend fun setFavorite(bookId: String, favorite: Boolean) = AppResult.success(Unit)
        override suspend fun setRating(bookId: String, rating: Int?) = AppResult.success(Unit)
        override suspend fun markRead(bookId: String, status: ReadingStatus) = AppResult.success(Unit)
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
        override fun copies(): Flow<List<io.github.bookshelf.core.data.model.BookCopy>> =
            flowOf(emptyList())

        override suspend fun removeCopies(
            copyUris: List<String>,
        ): AppResult<io.github.bookshelf.core.data.repository.DeletionResult> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

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

        override suspend fun rescanAll() = AppResult.success(Unit)
    }

    /** Настройки как константный поток или как поток, которым тест управляет сам. */
    private class FakeSettingsRepository(
        private val stream: Flow<AppSettings>,
    ) : SettingsRepository {

        override val settings: Flow<AppSettings> = stream

        override suspend fun resetToDefaults() = Unit
        override suspend fun current(): AppSettings = AppSettings()

        /** Состояния настроек после правок: полка пишет выбор порядка именно сюда. */
        val updated = mutableListOf<AppSettings>()

        /** Последнее записанное состояние: правка применяется к нему, как в настоящем хранилище. */
        private var stored: AppSettings = AppSettings()

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            stored = transform(stored)
            updated += stored
        }
    }
}
