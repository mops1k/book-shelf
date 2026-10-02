package io.github.bookshelf.feature.settings

import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.FakeFeatureFlags
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.covers.CoverCache.CoverKind
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.sync.ProgressSyncSchedule
import io.github.bookshelf.core.readerlink.fbreader.FbReaderApi
import io.github.bookshelf.core.readerlink.fbreader.FbReaderOpenBook
import io.github.bookshelf.core.readerlink.fbreader.FbReaderReadingPosition
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.eink.RefreshMode
import io.github.bookshelf.core.eink.vendor.EinkVendor
import java.io.File
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
import io.github.bookshelf.core.testing.fake.FakeBookRepository

/**
 * Запись настроек через `SettingsViewModel` (`SettingsSettersTest`).
 *
 * Проверяется то, ради чего экран переделан: у каждой настройки нет кнопки «Сохранить», значение
 * уходит в `SettingsRepository.update` сразу при касании и попадает **в своё поле** снимка.
 * Артефакт, от которого этот тест защищает, — «ползунок дёргается, а настройка не пишется»:
 * опечатка в сеттере (`appearance` вместо `eink`, забытый `copy`) на устройстве выглядит как
 * работающий переключатель.
 *
 * Все зависимости `SettingsViewModel` подменены фейками: те, что участвуют в проверке (настройки,
 * кэш обложек, выгрузка библиотеки), содержательные, остальные — пустые, но без них ViewModel
 * не собрать.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSettersTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = RecordingSettingsRepository()
    private val readers = FakeReaderRepository()
    private val sources = FakeSourceRepository()
    private val books = FakeBookRepository()
    private val eink = FakeEinkController()
    private val detector = FakeEinkDeviceDetector()
    private val covers = FakeCoverCache()
    private val schedule = FakeProgressSyncSchedule()
    private val fbReader = FakeFbReaderApi()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `appearance is written field by field`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSetThemeMode(ThemeMode.DARK)
        viewModel.onSetDensity(EinkDensityLike.COMPACT.name)
        viewModel.onSetFontScale(130)
        viewModel.onSetInvertCovers(true)
        advanceUntilIdle()

        val appearance = settings.last().appearance
        assertThat(appearance.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(appearance.density).isEqualTo(EinkDensityLike.COMPACT)
        assertThat(appearance.fontScale).isEqualTo(130)
        assertThat(appearance.invertCovers).isTrue()
    }

    @Test
    fun `reset returns every setting to its default and does not touch data`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSetThemeMode(ThemeMode.DARK)
        viewModel.onSetFontScale(130)
        advanceUntilIdle()
        val writesBeforeReset = settings.writes.size

        viewModel.onResetToDefaults()
        advanceUntilIdle()

        // Сброс — это один вызов репозитория настроек: он возвращает умолчания целиком. Данные
        // книги, полок и прогресса чтения живут в других таблицах, и сброс настроек их не трогает
        // (требование Александра: кнопка «Сбросить по умолчанию» про настройки, а не про библиотеку).
        assertThat(settings.last()).isEqualTo(AppSettings())
        assertThat(settings.writes.size).isEqualTo(writesBeforeReset + 1)
        assertThat(viewModel.uiState.value.isResetting).isFalse()
        assertThat(viewModel.uiState.value.isConfirmingReset).isFalse()
    }

    @Test
    fun `unknown density and font scale beyond the list do not break the snapshot`() = runTest(dispatcher) {
        val viewModel = viewModel()

        // Значения приходят строками из UI: неизвестное имя не должно переписывать плотность
        // «мусором», иначе следующий запуск применит непонятно что.
        viewModel.onSetDensity("НЕТ-ТАКОГО")
        advanceUntilIdle()

        assertThat(settings.last().appearance.density).isEqualTo(AppSettings().appearance.density)
    }

    @Test
    fun `an unknown vendor leaves the words to the screen`() = runTest(dispatcher) {
        // Ядро текста не отдаёт: у неизвестного вендора подписи нет, а слова «неизвестное
        // устройство» выбирает экран из своих ресурсов. Иначе русская строка из core осталась бы
        // русской и в английском интерфейсе (дефект 01.10.2026).
        detector.device = detector.device.copy(vendor = EinkVendor.UNKNOWN)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.diagnostics.vendorLabel).isNull()
    }

    @Test
    fun `paper without vendor commands still reports its vendor and screen`() = runTest(dispatcher) {
        // Ровно случай Bigme: вендорского контроллера нет, но экран — цветная бумага.
        val viewModel = viewModel()
        advanceUntilIdle()

        val diagnostics = viewModel.uiState.value.diagnostics
        assertThat(diagnostics.vendorLabel).isEqualTo("Bigme")
        // Слова выбирает экран приложения: ядро отдаёт только вид («TDD» §5).
        assertThat(diagnostics.screenLabel)
            .isEqualTo(AppText(R.string.settings_screen_eink_color))
        assertThat(diagnostics.hasVendorCommands).isFalse()
    }

    @Test
    fun `e-ink and library switches are written`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSetRefreshOnScreenChange(false)
        viewModel.onSetRefreshOnDialog(false)
        viewModel.onSetFullRefreshOnPage(false)
        viewModel.onSetGcInterval(50)
        viewModel.onSetVolumeKeysAsPageTurn(true)
        viewModel.onSetLibraryBehaviour(LibraryBehaviourAction.SetScanOnStart(true))
        viewModel.onSetLibraryBehaviour(LibraryBehaviourAction.SetKeepMetadataOnFileChange(false))
        viewModel.onSetLibraryBehaviour(LibraryBehaviourAction.SetShowRemovableShelf(false))
        viewModel.onSetDiskLimit(DiskLimitAction.SetCoverCacheMb(500))
        viewModel.onSetDiskLimit(DiskLimitAction.SetTempCopyMb(50))
        advanceUntilIdle()

        val state = settings.last()
        // Данные книги при замене файла: выключено — значит, заменённый файл считается новой книгой.
        assertThat(state.advanced.keepMetadataOnFileChange).isFalse()
        // Полка съёмного носителя: выключена — карта памяти отдельной полкой не показывается.
        assertThat(state.library.showRemovableShelf).isFalse()
        assertThat(state.eink.refreshOnScreenChange).isFalse()
        assertThat(state.eink.refreshOnDialog).isFalse()
        assertThat(state.eink.refreshOnPageChange).isFalse()
        assertThat(state.eink.gcInterval).isEqualTo(50)
        assertThat(state.eink.volumeKeysAsPageTurn).isTrue()
        assertThat(state.library.autoScanOnStart).isTrue()
        assertThat(state.library.coverCacheLimitMb).isEqualTo(500)
        assertThat(state.library.tempCopyLimitMb).isEqualTo(50)
    }

    @Test
    fun `shelf defaults and opds choices are written`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSetViewPreset(ShelfViewPreset.SERIES)
        viewModel.onSetDefaultSortOrder(SortOrder.ADDED_AT)
        viewModel.onSetDefaultSortDescending(true)
        viewModel.onSetDefaultGrouping(Grouping.AUTHOR)
        viewModel.onSetFeedCacheMaxAgeMinutes(15)
        viewModel.onSetDownloadFileNameTemplate("{author}/{title}.{ext}")
        advanceUntilIdle()

        val state = settings.last()
        assertThat(state.library.viewPreset).isEqualTo(ShelfViewPreset.SERIES)
        assertThat(state.library.sortOrder).isEqualTo(SortOrder.ADDED_AT)
        assertThat(state.library.sortDescending).isTrue()
        assertThat(state.library.grouping).isEqualTo(Grouping.AUTHOR)
        assertThat(state.opds.feedCacheMaxAgeMinutes).isEqualTo(15)
        assertThat(state.opds.downloadFileNameTemplate).isEqualTo("{author}/{title}.{ext}")
    }

    @Test
    fun `template outside the known list falls back to the documented default`() = runTest(dispatcher) {
        val viewModel = viewModel()

        // Шаблон приходит из UI списком; чужое значение сломало бы раскладку по папкам молча.
        viewModel.onSetDownloadFileNameTemplate("{author}/{series}/{title}/{isbn}/{ext}")
        advanceUntilIdle()

        assertThat(settings.last().opds.downloadFileNameTemplate)
            .isEqualTo(AppSettings().opds.downloadFileNameTemplate)
    }

    @Test
    fun `reader progress switch and interval are written and reschedule the chain`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onReaderProgress(ReaderProgressAction.SetEnabled(true))
        viewModel.onReaderProgress(ReaderProgressAction.SetInterval(2))
        advanceUntilIdle()

        val reader = settings.last().reader
        assertThat(reader.enableFbReaderProgress).isTrue()
        assertThat(reader.progressSyncIntervalMinutes).isEqualTo(2)
        // Пересмотр расписания — часть записи: иначе новая настройка не изменила бы поведение
        // до перезапуска приложения.
        assertThat(schedule.reviews).isEqualTo(2)
    }

    @Test
    fun `temp export switch is written`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onReaderProgress(ReaderProgressAction.SetTempExport(false))
        advanceUntilIdle()

        assertThat(settings.last().reader.tempExportEnabled).isFalse()
    }

    @Test
    fun `unknown interval falls back to the documented one`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onReaderProgress(ReaderProgressAction.SetInterval(7))
        advanceUntilIdle()

        assertThat(settings.last().reader.progressSyncIntervalMinutes)
            .isEqualTo(AppSettings().reader.progressSyncIntervalMinutes)
    }

    @Test
    fun `reader api check reports what is open and closes the session`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fbReader.open = FbReaderOpenBook(path = "content://doc/1", title = "Книга", language = "ru")

        viewModel.onReaderProgress(ReaderProgressAction.CheckApi)
        advanceUntilIdle()

        // Статус — переводимый текст: название открытой книги передаётся аргументом ресурса.
        assertThat(viewModel.uiState.value.readerApiStatus)
            .isEqualTo(AppText(R.string.settings_reader_api_open, "Книга"))
        // Сессию закрываем всегда: незакрытая привязка держала бы чужой сервис поднятым.
        assertThat(fbReader.disconnectCalls).isEqualTo(1)
    }

    @Test
    fun `reader api check reports unavailable reader`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fbReader.available = false

        viewModel.onReaderProgress(ReaderProgressAction.CheckApi)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.readerApiStatus)
            .isEqualTo(AppText(R.string.settings_reader_api_unavailable))
    }

    @Test
    fun `clear cover cache goes to the cache and does not touch settings`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val before = settings.last()

        viewModel.onClearCoverCache()
        advanceUntilIdle()

        assertThat(covers.cleared).isTrue()
        assertThat(settings.last()).isEqualTo(before)
    }

    private fun viewModel() = SettingsViewModel(
        settingsRepository = settings,
        readerRepository = readers,
        sourceRepository = sources,
        bookRepository = books,
        einkController = eink,
        einkDeviceDetector = detector,
        coverCache = covers,
        progressSchedule = schedule,
        fbReaderApi = fbReader,
        featureFlags = FakeFeatureFlags(),
    )

    /** Репозиторий настроек: хранит снимок, записывает каждый `update` в список. */
    private class RecordingSettingsRepository : SettingsRepository {

        private val state = MutableStateFlow(AppSettings())

        val writes = mutableListOf<AppSettings>()

        override val settings: Flow<AppSettings> = state

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            val next = transform(state.value)
            state.value = next
            writes += next
        }

        override suspend fun current(): AppSettings = state.value

        override suspend fun resetToDefaults() {
            state.value = AppSettings()
            writes += state.value
        }

        fun last(): AppSettings = state.value
    }

    private class FakeReaderRepository : ReaderRepository {

        override fun installedReaders(): Flow<List<ReaderProfileSummary>> = flowOf(emptyList())

        override fun defaultForFormat(format: String): Flow<String?> = flowOf(null)

        override fun readerDefaults(): Flow<Map<String, String>> = flowOf(emptyMap())

        override suspend fun setDefault(format: String, profileId: String?): AppResult<Unit> =
            AppResult.success(Unit)

        override suspend fun openBook(bookId: String, profileId: String?): AppResult<Unit> =
            AppResult.success(Unit)
    }

    private class FakeSourceRepository : SourceRepository {

        override fun observeSources(): Flow<List<Source>> = flowOf(emptyList())

        override suspend fun addSafSource(uri: String, displayName: String): AppResult<Source> =
            AppResult.success(
            Source(id = "s", uri = uri, displayName = displayName, kind = SourceKind.SAF_TREE),
        )

        override suspend fun addDirectPathSource(path: String, displayName: String): AppResult<Source> =

            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))


        override suspend fun addGrantedSource(uri: String, displayName: String): AppResult<Source> =
                AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun removeSource(sourceId: String): AppResult<Unit> = AppResult.success(Unit)

        override suspend fun rescan(sourceId: String): AppResult<Unit> = AppResult.success(Unit)

        override suspend fun hasWriteAccess(sourceId: String): Boolean = true
    }


    /** API читалки: проверяется, что диагностика связи закрывает сессию и сообщает результат. */
    private class FakeFbReaderApi : FbReaderApi {

        var available: Boolean = true
        var open: FbReaderOpenBook? = null
        var disconnectCalls: Int = 0

        override suspend fun connect(): Boolean = available

        override suspend fun disconnect() {
            disconnectCalls++
        }

        override fun isAvailable(): Boolean = available

        override suspend fun currentBook(): FbReaderOpenBook? = open

        override suspend fun currentPosition(): FbReaderReadingPosition? = null
    }

    /** Расписание синхронизации: тест проверяет, что его пересматривают при записи настройки. */
    private class FakeProgressSyncSchedule : ProgressSyncSchedule {

        var reviews: Int = 0
            private set

        override suspend fun review() {
            reviews++
        }
    }

    /** Детектор подменяется: настоящий читает `Build.*` и свойства системы. */
    private class FakeEinkDeviceDetector(
        var device: io.github.bookshelf.core.eink.device.EinkDeviceInfo =
            io.github.bookshelf.core.eink.device.EinkDeviceInfo(
                isEink = true,
                vendor = EinkVendor.BIGME,
                screen = io.github.bookshelf.core.eink.device.EinkScreen.EINK_COLOR,
                model = "HiBreak",
                androidSdk = 34,
            ),
    ) : io.github.bookshelf.core.eink.device.EinkDeviceDetector {
        override fun detect() = device
    }

    private class FakeEinkController(
        private val hasCommands: Boolean = false,
    ) : EinkController {

        override val isEinkDevice: Boolean = true

        override val hasVendorCommands: Boolean = hasCommands

        override val vendor: EinkVendor = EinkVendor.UNKNOWN

        override val supportsFastMode: Boolean = false

        override fun setRefreshMode(view: View, mode: RefreshMode) = Unit

        override fun fullRefresh(view: View) = Unit

        override fun setGcInterval(partialUpdates: Int) = Unit

        override fun setFrontLight(level: Int) = Unit

        override fun frontLight(): Int? = null
    }

    private class FakeCoverCache : CoverCache {

        var cleared = false
            private set

        override suspend fun read(bookId: String, kind: CoverKind): AppResult<File?> =
            AppResult.success(null)

        override suspend fun store(bookId: String, kind: CoverKind, bytes: ByteArray): AppResult<File> =
            AppResult.success(File("/dev/null"))

        override suspend fun invalidate(bookId: String) = Unit

        override suspend fun clear() {
            cleared = true
        }

        override suspend fun sizeBytes(): Long = 0L

        override fun fileFor(bookId: String, kind: CoverKind): File = File("/dev/null")
    }
}
