package io.github.bookshelf.core.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ScanSchedule
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.model.ThemeMode

/**
 * Ключи DataStore Preferences и документированные значения по умолчанию (docs/eink-ux.md §2, §4, §6.8).
 *
 * Здесь **только имена и типы ключей** — никакой логики чтения/записи: она в
 * [DataStoreSettingsRepository] (Sprint 1). Имена ключей с префиксом группы (`appearance.`,
 * `eink.`, …) читаются в `preferences_pb` глазами и не конфликтуют между группами.
 *
 * [Defaults] — единственный источник значений по умолчанию: на него ссылаются и
 * [io.github.bookshelf.core.datastore.model.AppSettings], и тест `SettingsDefaultsTest`. Так
 * документированное значение невозможно поменять в одном месте и забыть в другом.
 */
object SettingsKeys {

    /** Имя файла DataStore; используется в `PreferenceDataStoreFactory` (`:core:datastore` DI). */
    const val SETTINGS_FILE_NAME = "bookshelf_settings"

    // --- Внешний вид ---
    val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("appearance.theme_mode")
    val DENSITY: Preferences.Key<String> = stringPreferencesKey("appearance.density")
    val FONT_SCALE: Preferences.Key<Int> = intPreferencesKey("appearance.font_scale")
    val INVERT_COVERS: Preferences.Key<Boolean> = booleanPreferencesKey("appearance.invert_covers")
    val TEXT_ONLY_MODE: Preferences.Key<Boolean> = booleanPreferencesKey("appearance.text_only_mode")

    // --- Экран (e-ink) ---
    val REFRESH_MODE: Preferences.Key<String> = stringPreferencesKey("eink.refresh_mode")
    val REFRESH_ON_SCREEN_CHANGE: Preferences.Key<Boolean> = booleanPreferencesKey("eink.refresh_on_screen_change")
    val REFRESH_ON_DIALOG: Preferences.Key<Boolean> = booleanPreferencesKey("eink.refresh_on_dialog")
    val REFRESH_ON_PAGE_CHANGE: Preferences.Key<Boolean> = booleanPreferencesKey("eink.refresh_on_page_change")
    val GC_INTERVAL: Preferences.Key<Int> = intPreferencesKey("eink.gc_interval")
    /** Отсутствие ключа означает «не настроено» → `frontLightLevel = null`. */
    val FRONT_LIGHT_LEVEL: Preferences.Key<Int> = intPreferencesKey("eink.front_light_level")
    val VOLUME_KEYS_AS_PAGE_TURN: Preferences.Key<Boolean> = booleanPreferencesKey("eink.volume_keys_as_page_turn")
    val TAP_ZONE_LEFT_PERCENT: Preferences.Key<Int> = intPreferencesKey("eink.tap_zone_left_percent")
    val TAP_ZONE_RIGHT_PERCENT: Preferences.Key<Int> = intPreferencesKey("eink.tap_zone_right_percent")

    // --- Полка ---
    val GRID_COLUMNS: Preferences.Key<Int> = intPreferencesKey("library.grid_columns")
    /** Вид полки по умолчанию: сетка/список/компактно или обзор по авторам/сериям. */
    val VIEW_PRESET: Preferences.Key<String> = stringPreferencesKey("library.view_preset")
    /** Разовый бэкфилл жанров выполнен: повторять его при каждом запуске нельзя. */
    val METADATA_BACKFILL_DONE: Preferences.Key<Boolean> = booleanPreferencesKey("library.metadata_backfill_done")
    val ROWS_PER_PAGE: Preferences.Key<Int> = intPreferencesKey("library.rows_per_page")
    val SORT_ORDER: Preferences.Key<String> = stringPreferencesKey("library.sort_order")
    val SORT_DESCENDING: Preferences.Key<Boolean> = booleanPreferencesKey("library.sort_descending")
    val GROUPING: Preferences.Key<String> = stringPreferencesKey("library.grouping")
    val SHOW_MISSING: Preferences.Key<Boolean> = booleanPreferencesKey("library.show_missing")
    val AUTO_SCAN_ON_START: Preferences.Key<Boolean> = booleanPreferencesKey("library.auto_scan_on_start")
    val SCAN_SCHEDULE: Preferences.Key<String> = stringPreferencesKey("library.scan_schedule")
    val MISSING_CLEANUP_DAYS: Preferences.Key<Int> = intPreferencesKey("library.missing_cleanup_days")
    val METADATA_PARALLELISM: Preferences.Key<Int> = intPreferencesKey("library.metadata_parallelism")
    val TEMP_COPY_LIMIT_MB: Preferences.Key<Int> = intPreferencesKey("library.temp_copy_limit_mb")
    val COVER_CACHE_LIMIT_MB: Preferences.Key<Int> = intPreferencesKey("library.cover_cache_limit_mb")

    /**
     * Показывать съёмный носитель (карту памяти, флешку) отдельной полкой.
     *
     * Умолчание — включено: требование Александра (01.10.2026) — вставил карту с книгами, и полка
     * появилась сама; вынул — исчезла вместе с книгами носителя.
     */
    val SHOW_REMOVABLE_SHELF: Preferences.Key<Boolean> = booleanPreferencesKey("library.show_removable_shelf")

    // --- Читалки ---
    /** JSON-объект `format -> profileId` (Preferences не умеет `Map`). */
    val DEFAULT_READER_BY_FORMAT: Preferences.Key<String> = stringPreferencesKey("reader.default_by_format")
    /** JSON-объект `format -> profileId`. */
    val LAST_USED_READER_BY_FORMAT: Preferences.Key<String> = stringPreferencesKey("reader.last_used_by_format")
    val ENABLE_FBREADER_PROGRESS: Preferences.Key<Boolean> = booleanPreferencesKey("reader.enable_fbreader_progress")
    val TEMP_EXPORT_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("reader.temp_export_enabled")

    /**
     * Как часто опрашивать читалку ради прогресса, в минутах.
     *
     * Настраивается, потому что читают с разной скоростью: пятнадцати минут одному хватит с запасом,
     * а другой за это время прочтёт главу и захочет увидеть прогресс раньше (решение Александра,
     * 29.09.2026).
     */
    val PROGRESS_SYNC_INTERVAL_MINUTES: Preferences.Key<Int> =
        intPreferencesKey("reader.progress_sync_interval_minutes")

    // --- OPDS ---
    val ENABLE_OPDS: Preferences.Key<Boolean> = booleanPreferencesKey("opds.enable")
    val FEED_CACHE_MAX_AGE_MINUTES: Preferences.Key<Int> = intPreferencesKey("opds.feed_cache_max_age_minutes")
    val DOWNLOAD_FOLDER_SOURCE_ID: Preferences.Key<String> = stringPreferencesKey("opds.download_folder_source_id")
    val DOWNLOAD_FOLDER_RELATIVE_PATH: Preferences.Key<String> =
        stringPreferencesKey("opds.download_folder_relative_path")
    val DOWNLOAD_FILE_NAME_TEMPLATE: Preferences.Key<String> = stringPreferencesKey("opds.download_file_name_template")
    val ALLOW_HTTP: Preferences.Key<Boolean> = booleanPreferencesKey("opds.allow_http")

    // --- Продвинутые ---
    val KEEP_METADATA_ON_FILE_CHANGE: Preferences.Key<Boolean> =
        booleanPreferencesKey("advanced.keep_metadata_on_file_change")
    val DUPLICATE_SCAN_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("advanced.duplicate_scan_enabled")
    val DB_BACKUP_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("advanced.db_backup_enabled")

    /**
     * Значения по умолчанию (docs/eink-ux.md §2, §4; docs/architecture.md §4.4, §5).
     *
     * Числовые и булевы — `const val`: их видно на этапе компиляции и нельзя случайно поменять
     * «на месте». Enum-значения — обычные `val`.
     */
    object Defaults {
        val THEME_MODE: ThemeMode = ThemeMode.LIGHT
        val DENSITY: EinkDensityLike = EinkDensityLike.LARGE
        const val FONT_SCALE = 100
        const val INVERT_COVERS = false
        const val TEXT_ONLY_MODE = false

        val REFRESH_MODE: RefreshModeLike = RefreshModeLike.AUTO
        const val REFRESH_ON_SCREEN_CHANGE = true
        const val REFRESH_ON_DIALOG = true
        const val REFRESH_ON_PAGE_CHANGE = true
        const val GC_INTERVAL = 10
        const val VOLUME_KEYS_AS_PAGE_TURN = false
        const val TAP_ZONE_LEFT_PERCENT = 25
        const val TAP_ZONE_RIGHT_PERCENT = 25

        const val GRID_COLUMNS = 2
        val VIEW_PRESET: ShelfViewPreset = ShelfViewPreset.GRID
        const val METADATA_BACKFILL_DONE = false
        const val ROWS_PER_PAGE = 6
        val SORT_ORDER: SortOrder = SortOrder.TITLE
        const val SORT_DESCENDING = false
        val GROUPING: Grouping = Grouping.NONE
        const val SHOW_MISSING = false
        const val AUTO_SCAN_ON_START = false
        val SCAN_SCHEDULE: ScanSchedule = ScanSchedule.MANUAL
        const val MISSING_CLEANUP_DAYS = 30
        const val METADATA_PARALLELISM = 2
        const val TEMP_COPY_LIMIT_MB = 100
        const val COVER_CACHE_LIMIT_MB = 200
        const val SHOW_REMOVABLE_SHELF = true

        const val ENABLE_FBREADER_PROGRESS = false

        /** Пять минут: достаточно часто, чтобы прогресс был свежим, и достаточно редко для батареи. */
        const val PROGRESS_SYNC_INTERVAL_MINUTES = 5
        const val TEMP_EXPORT_ENABLED = true

        const val ENABLE_OPDS = true
        const val FEED_CACHE_MAX_AGE_MINUTES = 60
        const val DOWNLOAD_FILE_NAME_TEMPLATE = "{author} - {title}.{ext}"
        const val ALLOW_HTTP = false

        /**
         * Сохранять данные книги, когда её файл заменили.
         *
         * Умолчание — **включено**: оценка, статус чтения, прогресс и полки относятся к книге,
         * которую человек читал, и терять их из-за замены файла было бы неприятным сюрпризом.
         * Выключено — заменённый файл считается новой книгой (решение Александра, 29.09.2026).
         */
        const val KEEP_METADATA_ON_FILE_CHANGE = true
        const val DUPLICATE_SCAN_ENABLED = false
        const val DB_BACKUP_ENABLED = true
    }
}
