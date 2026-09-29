package io.github.bookshelf.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ScanSchedule
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.model.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Хранение настроек в DataStore (docs/eink-ux.md §6.8).
 *
 * Проверяется ровно то, без чего остальные экраны настроек бессмысленны: пустое хранилище даёт
 * документированные умолчания, а запись читается обратно без потерь. Отдельные кейсы — на
 * устойчивость: битый файл и мусор в значениях не должны ронять приложение, потому что настройки
 * живут в файле, который пользователь может повредить (docs/architecture.md §1 — «никогда не падать»).
 *
 * DataStore создаётся той же фабрикой [createSettingsDataStore], что и в боевом DI, иначе тест
 * проверял бы не боевую устойчивость к битому файлу.
 */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun repository(fileName: String = "settings.preferences_pb"): DataStoreSettingsRepository {
        val file: File = temporaryFolder.newFolder("store-${System.nanoTime()}").resolve(fileName)
        return DataStoreSettingsRepository(createSettingsDataStore(produceFile = { file }))
    }

    @Test
    fun empty_store_returns_documented_defaults() = runBlocking {
        val repository = repository()

        assertThat(repository.current()).isEqualTo(AppSettings())
    }

    @Test
    fun settings_flow_reports_defaults_and_then_written_value() = runBlocking {
        val repository = repository()

        assertThat(repository.settings.first()).isEqualTo(AppSettings())

        repository.update { it.copy(appearance = it.appearance.copy(themeMode = ThemeMode.DARK)) }

        assertThat(repository.settings.first().appearance.themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun update_round_trips_every_group() = runBlocking {
        val repository = repository()
        val target = AppSettings(
            appearance = io.github.bookshelf.core.datastore.model.AppearanceSettings(
                themeMode = ThemeMode.DARK,
                density = EinkDensityLike.COMPACT,
                fontScale = 130,
                invertCovers = true,
                textOnlyMode = true,
            ),
            eink = io.github.bookshelf.core.datastore.model.EinkSettings(
                refreshMode = RefreshModeLike.FULL,
                refreshOnScreenChange = false,
                refreshOnDialog = false,
                refreshOnPageChange = false,
                gcInterval = 25,
                frontLightLevel = 70,
                volumeKeysAsPageTurn = true,
                tapZoneLeftPercent = 10,
                tapZoneRightPercent = 40,
            ),
            library = io.github.bookshelf.core.datastore.model.LibrarySettings(
                gridColumns = 4,
                rowsPerPage = 3,
                sortOrder = SortOrder.SERIES,
                sortDescending = true,
                grouping = Grouping.AUTHOR,
                showMissing = true,
                autoScanOnStart = true,
                scanSchedule = ScanSchedule.DAILY,
                missingCleanupDays = 7,
                metadataParallelism = 4,
                tempCopyLimitMb = 250,
                coverCacheLimitMb = 512,
            ),
            reader = io.github.bookshelf.core.datastore.model.ReaderSettings(
                defaultReaderByFormat = mapOf("fb2" to "profile-1", "epub" to "profile-2"),
                lastUsedReaderByFormat = mapOf("pdf" to "profile-3"),
                enableFbReaderProgress = true,
                tempExportEnabled = false,
                progressSyncIntervalMinutes = 2,
            ),
            opds = io.github.bookshelf.core.datastore.model.OpdsSettings(
                enableOpds = false,
                feedCacheMaxAgeMinutes = 15,
                downloadFolderSourceId = "source-1",
                downloadFolderRelativePath = "Загрузки/Книги",
                downloadFileNameTemplate = "{title}.{ext}",
                allowHttp = true,
            ),
            advanced = io.github.bookshelf.core.datastore.model.AdvancedSettings(
                keepMetadataOnFileChange = true,
                duplicateScanEnabled = true,
                dbBackupEnabled = false,
            ),
        )

        repository.update { target }

        assertThat(repository.current()).isEqualTo(target)
    }

    @Test
    fun partial_update_keeps_untouched_groups() = runBlocking {
        val repository = repository()
        repository.update {
            it.copy(
                library = it.library.copy(gridColumns = 5, viewPreset = ShelfViewPreset.AUTHORS),
                opds = it.opds.copy(downloadFolderRelativePath = "Загрузки"),
            )
        }

        repository.update { it.copy(appearance = it.appearance.copy(themeMode = ThemeMode.SYSTEM)) }

        val current = repository.current()
        assertThat(current.appearance.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(current.library.gridColumns).isEqualTo(5)
        // Вид полки по умолчанию переживает запись: иначе настройка «сбрасывалась» бы в сетку.
        assertThat(current.library.viewPreset).isEqualTo(ShelfViewPreset.AUTHORS)
        assertThat(current.opds.downloadFolderRelativePath).isEqualTo("Загрузки")
    }

    @Test
    fun null_front_light_is_not_stored_as_zero() = runBlocking {
        val repository = repository()

        repository.update { it.copy(eink = it.eink.copy(frontLightLevel = 50)) }
        assertThat(repository.current().eink.frontLightLevel).isEqualTo(50)

        repository.update { it.copy(eink = it.eink.copy(frontLightLevel = null)) }
        assertThat(repository.current().eink.frontLightLevel).isNull()
    }

    @Test
    fun clearing_reader_map_returns_it_to_empty() = runBlocking {
        val repository = repository()
        repository.update { it.copy(reader = it.reader.copy(defaultReaderByFormat = mapOf("fb2" to "p1"))) }

        repository.update { it.copy(reader = it.reader.copy(defaultReaderByFormat = emptyMap())) }

        assertThat(repository.current().reader.defaultReaderByFormat).isEmpty()
    }

    @Test
    fun corrupted_file_does_not_crash_and_yields_defaults() = runBlocking {
        val file = temporaryFolder.newFolder("store-corrupt").resolve("settings.preferences_pb")
        // Мусор вместо protobuf: ровно то, что остаётся после обрыва записи.
        file.writeBytes(byteArrayOf(0x00, 0x7F, -1, -1, 0x42, 0x13, 0x37))
        val repository = DataStoreSettingsRepository(createSettingsDataStore(produceFile = { file }))

        assertThat(repository.current()).isEqualTo(AppSettings())
    }

    @Test
    fun unknown_enum_name_falls_back_to_documented_default() = runBlocking {
        val file = temporaryFolder.newFolder("store-garbage").resolve("settings.preferences_pb")
        val store: DataStore<Preferences> = createSettingsDataStore(produceFile = { file })
        store.edit { prefs ->
            prefs[SettingsKeys.THEME_MODE] = "НЕ_СУЩЕСТВУЕТ"
            prefs[SettingsKeys.SORT_ORDER] = "SORT_BY_VIBES"
        }
        val repository = DataStoreSettingsRepository(store)

        val current = repository.current()
        assertThat(current.appearance.themeMode).isEqualTo(SettingsKeys.Defaults.THEME_MODE)
        assertThat(current.library.sortOrder).isEqualTo(SettingsKeys.Defaults.SORT_ORDER)
    }

    @Test
    fun non_positive_numbers_fall_back_because_zero_would_crash_the_app() = runBlocking {
        val file = temporaryFolder.newFolder("store-zeros").resolve("settings.preferences_pb")
        val store: DataStore<Preferences> = createSettingsDataStore(produceFile = { file })
        store.edit { prefs ->
            // metadataParallelism уходит в Executors.newFixedThreadPool, который на 0 бросает
            // IllegalArgumentException (проверено на JDK 21), а gcInterval = 0 означал бы полный
            // GC после каждого частичного обновления.
            prefs[SettingsKeys.METADATA_PARALLELISM] = 0
            prefs[SettingsKeys.GC_INTERVAL] = 0
            prefs[SettingsKeys.GRID_COLUMNS] = 0
            prefs[SettingsKeys.ROWS_PER_PAGE] = -3
            prefs[SettingsKeys.FRONT_LIGHT_LEVEL] = 9999
        }
        val repository = DataStoreSettingsRepository(store)

        val current = repository.current()
        assertThat(current.library.metadataParallelism)
            .isEqualTo(SettingsKeys.Defaults.METADATA_PARALLELISM)
        assertThat(current.eink.gcInterval).isEqualTo(SettingsKeys.Defaults.GC_INTERVAL)
        assertThat(current.library.gridColumns).isEqualTo(SettingsKeys.Defaults.GRID_COLUMNS)
        assertThat(current.library.rowsPerPage).isEqualTo(SettingsKeys.Defaults.ROWS_PER_PAGE)
        assertThat(current.eink.frontLightLevel).isEqualTo(100)
        assertThat(current.reader.progressSyncIntervalMinutes)
            .isEqualTo(SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES)
        assertThat(current.reader.enableFbReaderProgress)
            .isEqualTo(SettingsKeys.Defaults.ENABLE_FBREADER_PROGRESS)
    }

    @Test
    fun unreadable_reader_map_json_is_ignored() = runBlocking {
        val file = temporaryFolder.newFolder("store-json").resolve("settings.preferences_pb")
        val store: DataStore<Preferences> = createSettingsDataStore(produceFile = { file })
        store.edit { prefs ->
            prefs[SettingsKeys.DEFAULT_READER_BY_FORMAT] = "не json вовсе"
        }
        val repository = DataStoreSettingsRepository(store)

        assertThat(repository.current().reader.defaultReaderByFormat).isEmpty()
    }
    @Test
    fun reset_returns_documented_defaults() = runBlocking {
        val repository = repository()
        repository.update { settings ->
            settings.copy(
                appearance = settings.appearance.copy(
                    themeMode = ThemeMode.DARK,
                    density = EinkDensityLike.COMPACT,
                    fontScale = 130,
                ),
                library = settings.library.copy(viewPreset = ShelfViewPreset.SERIES, gridColumns = 3),
            )
        }

        repository.resetToDefaults()

        // Сброс возвращает ровно умолчания: иначе «Сбросить» обещало бы одно, а делало другое.
        assertThat(repository.current()).isEqualTo(AppSettings())
    }

    @Test
    fun reset_keeps_service_fields_and_the_reader_mirror() = runBlocking {
        val repository = repository()
        repository.update { settings ->
            settings.copy(
                library = settings.library.copy(
                    metadataBackfillDone = true,
                    viewPreset = ShelfViewPreset.AUTHORS,
                ),
                reader = settings.reader.copy(
                    defaultReaderByFormat = mapOf("fb2" to "fbreader"),
                    lastUsedReaderByFormat = mapOf("fb2" to "ko reader"),
                ),
            )
        }

        repository.resetToDefaults()

        val settings = repository.current()
        // Флаг разового бэкфилла жанров и «последняя читалка» — память приложения, а не настройка:
        // их сброс запускал бы бэкфилл заново и заставлял спрашивать читалку на каждой книге.
        assertThat(settings.library.metadataBackfillDone).isTrue()
        assertThat(settings.reader.lastUsedReaderByFormat).containsExactly("fb2", "ko reader")
        // Выбор читалки по умолчанию живёт в таблице format_defaults, а здесь только зеркало.
        assertThat(settings.reader.defaultReaderByFormat).containsExactly("fb2", "fbreader")
        // А обычная настройка сбрасывается.
        assertThat(settings.library.viewPreset).isEqualTo(ShelfViewPreset.GRID)
    }

}
