package io.github.bookshelf.core.datastore.model

import io.github.bookshelf.core.datastore.SettingsKeys

/**
 * Все настройки приложения одним неизменяемым объектом («E-ink UX» §6.8).
 *
 * Настройки сгруппированы по экранам настроек, а не свалены в плоский список: так их читает UI,
 * так же они раскладываются по ключам DataStore ([SettingsKeys]) и так же экспортируются в JSON.
 *
 * Значения по умолчанию — не «на глаз»: они зафиксированы в [SettingsKeys.Defaults] по
 * «E-ink UX» (плотность «Крупный», полный refresh на смену экрана/страницы/диалога, тап-зоны
 * 25/25, параллелизм парсеров 2, лимит временной копии 100 МБ, лимит кэша обложек 200 МБ,
 * очистка пропавших через 30 дней) и проверяются тестом `SettingsDefaultsTest`.
 *
 * Все настройки применяются без перезапуска («План работ», DoD Sprint 5): потребители обязаны
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

/** Внешний вид: тема, плотность, масштаб шрифта, обложки («E-ink UX» §2, §6.8). */
data class AppearanceSettings(
    val themeMode: ThemeMode = SettingsKeys.Defaults.THEME_MODE,
    val density: EinkDensityLike = SettingsKeys.Defaults.DENSITY,
    /** Масштаб шрифта UI в процентах: 100/115/130/150 («E-ink UX» §2.2). */
    val fontScale: Int = SettingsKeys.Defaults.FONT_SCALE,
    val invertCovers: Boolean = SettingsKeys.Defaults.INVERT_COVERS,
    val textOnlyMode: Boolean = SettingsKeys.Defaults.TEXT_ONLY_MODE,
)

/** Экран (e-ink): режим обновления, события полного refresh, подсветка, тап-зоны («E-ink UX» §4). */
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

/** Полка: сетка/список, сортировка, группировка, скан и лимиты («E-ink UX» §6.2, §6.8). */
data class LibrarySettings(
    val gridColumns: Int = SettingsKeys.Defaults.GRID_COLUMNS,
    /** Вид полки по умолчанию: чем открывается библиотека (сетка … обзор по сериям). */
    val viewPreset: ShelfViewPreset = SettingsKeys.Defaults.VIEW_PRESET,
    /** Разовый бэкфилл жанров уже запускался (см. `MetadataBackfill`). */
    val metadataBackfillDone: Boolean = SettingsKeys.Defaults.METADATA_BACKFILL_DONE,
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
    /** Полка съёмного носителя в списке полок (карта памяти, флешка). */
    val showRemovableShelf: Boolean = SettingsKeys.Defaults.SHOW_REMOVABLE_SHELF,
    /**
     * Лимит дискового кэша обложек, МБ («Архитектура» §5).
     *
     * Поле добавлено в группу «Полка», хотя в перечне из задания его не было: без него настройка
     * «Занято кэшем / Очистить» и `CoverCacheCleaner.evictToLimit` не имеют источника значения.
     */
    val coverCacheLimitMb: Int = SettingsKeys.Defaults.COVER_CACHE_LIMIT_MB,
)

/** Читалки: выбор по умолчанию и интеграция с FBReader («Читалки», ADR-005). */
data class ReaderSettings(
    /** Формат (`fb2`, `epub`, …) → id профиля читалки. */
    val defaultReaderByFormat: Map<String, String> = emptyMap(),
    /** Формат → id профиля, которым открывали последний раз (для «спросить, если сменился»). */
    val lastUsedReaderByFormat: Map<String, String> = emptyMap(),
    val enableFbReaderProgress: Boolean = SettingsKeys.Defaults.ENABLE_FBREADER_PROGRESS,
    /** Прогресс из KOReader: sidecar-файл `<книга>.sdr/metadata.<расширение>.lua` рядом с книгой. */
    val enableKoReaderProgress: Boolean = SettingsKeys.Defaults.ENABLE_KOREADER_PROGRESS,
    /** Прогресс из CoolReader: его база `/sdcard/.cr3/cr3db.sqlite` (нужен доступ ко всей памяти). */
    val enableCoolReaderProgress: Boolean = SettingsKeys.Defaults.ENABLE_COOLREADER_PROGRESS,
    /** Прогресс из PocketBook: его база `/sdcard/.pocketbook/books.db` (нужен доступ ко всей памяти). */
    val enablePocketBookProgress: Boolean = SettingsKeys.Defaults.ENABLE_POCKETBOOK_PROGRESS,
    val tempExportEnabled: Boolean = SettingsKeys.Defaults.TEMP_EXPORT_ENABLED,
    val progressSyncIntervalMinutes: Int = SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES,
)

/** OPDS: каталоги и загрузки («OPDS», ADR-006). */
data class OpdsSettings(
    val enableOpds: Boolean = SettingsKeys.Defaults.ENABLE_OPDS,
    val feedCacheMaxAgeMinutes: Int = SettingsKeys.Defaults.FEED_CACHE_MAX_AGE_MINUTES,
    /**
     * Шаблон относительного пути скачанного файла.
     *
     * Отдельной папки загрузок нет намеренно (решение Александра, 29.09.2026): книги складываются
     * в папку библиотеки, а папку конкретной книги выбирают на её карточке, когда источников
     * несколько. Поля `downloadFolderSourceId`/`downloadFolderRelativePath` удалены 05.10.2026:
     * их не читал ни один потребитель.
     */
    val downloadFileNameTemplate: String = SettingsKeys.Defaults.DOWNLOAD_FILE_NAME_TEMPLATE,
    val allowHttp: Boolean = SettingsKeys.Defaults.ALLOW_HTTP,
)

/** Продвинутые настройки: метаданные, дубликаты, бэкап БД («План работ» Sprint 9). */
data class AdvancedSettings(
    val keepMetadataOnFileChange: Boolean = SettingsKeys.Defaults.KEEP_METADATA_ON_FILE_CHANGE,
    val duplicateScanEnabled: Boolean = SettingsKeys.Defaults.DUPLICATE_SCAN_ENABLED,
    val dbBackupEnabled: Boolean = SettingsKeys.Defaults.DB_BACKUP_ENABLED,
)

/**
 * Тема интерфейса.
 *
 * [LIGHT] — инверсия «белый фон / чёрный текст» и значение по умолчанию: e-ink читается лучше
 * всего именно так («E-ink UX» §2.1).
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Плотность интерфейса («E-ink UX» §2.3).
 *
 * `Like` в имени — потому что это настройка слоя данных, а не тип дизайн-системы:
 * `:core:datastore` не зависит от Compose и не может импортировать `EinkDensity`.
 * Маппинг делает `:feature:settings`.
 */
enum class EinkDensityLike { LARGE, NORMAL, COMPACT }

/**
 * Режим обновления экрана («E-ink UX» §4).
 *
 * `Like` — по той же причине, что и у [EinkDensityLike]: `:core:eink` объявляет свой `RefreshMode`,
 * и слой данных не должен зависеть от него.
 */
enum class RefreshModeLike { AUTO, FULL, FAST }

/** Сортировка полки («E-ink UX» §6.2). */
enum class SortOrder { TITLE, AUTHOR, SERIES, ADDED_AT, LAST_OPENED, SIZE, PROGRESS, YEAR }

/** Группировка полки («E-ink UX» §6.2): заголовки-разделители внутри страницы. */
enum class Grouping { NONE, SERIES, AUTHOR, FOLDER, FORMAT }

/** Расписание автосканирования («План работ», Phase 3). */
enum class ScanSchedule { MANUAL, ON_START, DAILY, WEEKLY }
