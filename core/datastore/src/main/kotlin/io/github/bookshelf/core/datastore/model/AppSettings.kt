package io.github.bookshelf.core.datastore.model

import io.github.bookshelf.core.datastore.SettingsKeys

/**
 * Все настройки приложения одним неизменяемым объектом (docs/eink-ux.md §6.8).
 *
 * Настройки сгруппированы по экранам настроек, а не свалены в плоский список: так их читает UI,
 * так же они раскладываются по ключам DataStore ([SettingsKeys]) и так же экспортируются в JSON.
 *
 * Значения по умолчанию — не «на глаз»: они зафиксированы в [SettingsKeys.Defaults] по
 * docs/eink-ux.md (плотность «Крупный», полный refresh на смену экрана/страницы/диалога, тап-зоны
 * 25/25, параллелизм парсеров 2, лимит временной копии 100 МБ, лимит кэша обложек 200 МБ,
 * очистка пропавших через 30 дней) и проверяются тестом `SettingsDefaultsTest`.
 *
 * Все настройки применяются без перезапуска (docs/roadmap.md, DoD Sprint 5): потребители обязаны
 * подписываться на [io.github.bookshelf.core.datastore.SettingsRepository.settings], а не читать
 * значения один раз при старте.
 */
data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val eink: EinkSettings = EinkSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val reader: ReaderSettings = ReaderSettings(),
    val opds: OpdsSettings = OpdsSettings(),
    val advanced: AdvancedSettings = AdvancedSettings(),
)

/** Внешний вид: тема, плотность, масштаб шрифта, обложки (docs/eink-ux.md §2, §6.8). */
data class AppearanceSettings(
    val themeMode: ThemeMode = SettingsKeys.Defaults.THEME_MODE,
    val density: EinkDensityLike = SettingsKeys.Defaults.DENSITY,
    /** Масштаб шрифта UI в процентах: 100/115/130/150 (docs/eink-ux.md §2.2). */
    val fontScale: Int = SettingsKeys.Defaults.FONT_SCALE,
    val invertCovers: Boolean = SettingsKeys.Defaults.INVERT_COVERS,
    val textOnlyMode: Boolean = SettingsKeys.Defaults.TEXT_ONLY_MODE,
)

/** Экран (e-ink): режим обновления, события полного refresh, подсветка, тап-зоны (docs/eink-ux.md §4). */
data class EinkSettings(
    val refreshMode: RefreshModeLike = SettingsKeys.Defaults.REFRESH_MODE,
    val refreshOnScreenChange: Boolean = SettingsKeys.Defaults.REFRESH_ON_SCREEN_CHANGE,
    val refreshOnDialog: Boolean = SettingsKeys.Defaults.REFRESH_ON_DIALOG,
    val refreshOnPageChange: Boolean = SettingsKeys.Defaults.REFRESH_ON_PAGE_CHANGE,
    /** Полный GC каждые N частичных обновлений (`EinkController.setGcInterval`). */
    val gcInterval: Int = SettingsKeys.Defaults.GC_INTERVAL,
    /** Уровень подсветки 0..100; `null` — пользователь её не настраивал / устройства нет. */
    val frontLightLevel: Int? = null,
    val volumeKeysAsPageTurn: Boolean = SettingsKeys.Defaults.VOLUME_KEYS_AS_PAGE_TURN,
    val tapZoneLeftPercent: Int = SettingsKeys.Defaults.TAP_ZONE_LEFT_PERCENT,
    val tapZoneRightPercent: Int = SettingsKeys.Defaults.TAP_ZONE_RIGHT_PERCENT,
)

/** Полка: сетка/список, сортировка, группировка, скан и лимиты (docs/eink-ux.md §6.2, §6.8). */
data class LibrarySettings(
    val gridColumns: Int = SettingsKeys.Defaults.GRID_COLUMNS,
    val rowsPerPage: Int = SettingsKeys.Defaults.ROWS_PER_PAGE,
    val sortOrder: SortOrder = SettingsKeys.Defaults.SORT_ORDER,
    val sortDescending: Boolean = SettingsKeys.Defaults.SORT_DESCENDING,
    val grouping: Grouping = SettingsKeys.Defaults.GROUPING,
    val showMissing: Boolean = SettingsKeys.Defaults.SHOW_MISSING,
    val autoScanOnStart: Boolean = SettingsKeys.Defaults.AUTO_SCAN_ON_START,
    val scanSchedule: ScanSchedule = SettingsKeys.Defaults.SCAN_SCHEDULE,
    val missingCleanupDays: Int = SettingsKeys.Defaults.MISSING_CLEANUP_DAYS,
    val metadataParallelism: Int = SettingsKeys.Defaults.METADATA_PARALLELISM,
    val tempCopyLimitMb: Int = SettingsKeys.Defaults.TEMP_COPY_LIMIT_MB,
    /**
     * Лимит дискового кэша обложек, МБ (docs/architecture.md §5).
     *
     * Поле добавлено в группу «Полка», хотя в перечне из задания его не было: без него настройка
     * «Занято кэшем / Очистить» и `CoverCacheCleaner.evictToLimit` не имеют источника значения.
     */
    val coverCacheLimitMb: Int = SettingsKeys.Defaults.COVER_CACHE_LIMIT_MB,
)

/** Читалки: выбор по умолчанию и интеграция с FBReader (docs/reader-integration.md, ADR-005). */
data class ReaderSettings(
    /** Формат (`fb2`, `epub`, …) → id профиля читалки. */
    val defaultReaderByFormat: Map<String, String> = emptyMap(),
    /** Формат → id профиля, которым открывали последний раз (для «спросить, если сменился»). */
    val lastUsedReaderByFormat: Map<String, String> = emptyMap(),
    val enableFbReaderProgress: Boolean = SettingsKeys.Defaults.ENABLE_FBREADER_PROGRESS,
    val tempExportEnabled: Boolean = SettingsKeys.Defaults.TEMP_EXPORT_ENABLED,
)

/** OPDS: каталоги и загрузки (docs/opds.md, ADR-006). */
data class OpdsSettings(
    val enableOpds: Boolean = SettingsKeys.Defaults.ENABLE_OPDS,
    val feedCacheMaxAgeMinutes: Int = SettingsKeys.Defaults.FEED_CACHE_MAX_AGE_MINUTES,
    val downloadFolderSourceId: String? = null,
    val downloadFolderRelativePath: String? = null,
    val downloadFileNameTemplate: String = SettingsKeys.Defaults.DOWNLOAD_FILE_NAME_TEMPLATE,
    val allowHttp: Boolean = SettingsKeys.Defaults.ALLOW_HTTP,
)

/** Продвинутые настройки: метаданные, дубликаты, бэкап БД (docs/roadmap.md Sprint 9). */
data class AdvancedSettings(
    val keepMetadataOnFileChange: Boolean = SettingsKeys.Defaults.KEEP_METADATA_ON_FILE_CHANGE,
    val duplicateScanEnabled: Boolean = SettingsKeys.Defaults.DUPLICATE_SCAN_ENABLED,
    val dbBackupEnabled: Boolean = SettingsKeys.Defaults.DB_BACKUP_ENABLED,
)

/**
 * Тема интерфейса.
 *
 * [LIGHT] — инверсия «белый фон / чёрный текст» и значение по умолчанию: e-ink читается лучше
 * всего именно так (docs/eink-ux.md §2.1).
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Плотность интерфейса (docs/eink-ux.md §2.3).
 *
 * `Like` в имени — потому что это настройка слоя данных, а не тип дизайн-системы:
 * `:core:datastore` не зависит от Compose и не может импортировать `EinkDensity`.
 * Маппинг делает `:feature:settings`.
 */
enum class EinkDensityLike { LARGE, NORMAL, COMPACT }

/**
 * Режим обновления экрана (docs/eink-ux.md §4).
 *
 * `Like` — по той же причине, что и у [EinkDensityLike]: `:core:eink` объявляет свой `RefreshMode`,
 * и слой данных не должен зависеть от него.
 */
enum class RefreshModeLike { AUTO, FULL, FAST }

/** Сортировка полки (docs/eink-ux.md §6.2). */
enum class SortOrder { TITLE, AUTHOR, SERIES, ADDED_AT, LAST_OPENED, SIZE, PROGRESS, YEAR }

/** Группировка полки (docs/eink-ux.md §6.2): заголовки-разделители внутри страницы. */
enum class Grouping { NONE, SERIES, AUTHOR, FOLDER, FORMAT }

/** Расписание автосканирования (docs/roadmap.md, Phase 3). */
enum class ScanSchedule { MANUAL, ON_START, DAILY, WEEKLY }
