package io.github.bookshelf.core.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.bookshelf.core.datastore.model.AdvancedSettings
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.AppearanceSettings
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.EinkSettings
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.datastore.model.OpdsSettings
import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ScanSchedule
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.model.ThemeMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Преобразование «ключи DataStore ↔ [AppSettings]» («E-ink UX» §6.8).
 *
 * Вынесено из репозитория намеренно: правил здесь больше, чем поместится в класс, а detekt
 * ограничивает число функций. Побочная выгода — правила читаются как таблица, а не как код
 * вперемешку с вводом-выводом.
 *
 * Два правила, общие для всех полей:
 *
 * 1. **Отсутствие ключа — это умолчание, а не ошибка.** Чтение всегда с `?: Defaults…`, поэтому
 *    добавление новой настройки не требует миграции: у пользователей просто появится дефолт.
 * 2. **Мусор в значении не роняет приложение.** Enum разбирается через безопасный
 *    [enumNameOrDefault]: если в файле оказалось незнакомое имя (другая версия приложения,
 *    ручная правка), берётся документированное умолчание. То же с JSON карт читалок.
 *
 * Запись всегда полная, а не «только изменившиеся ключи»: чтение и запись выполняются внутри
 * одной транзакции `edit`, поэтому частичная запись не дала бы выигрыша, зато могла бы оставить
 * ключ от предыдущего значения при смене типа. Удаление `null`-значений — явное: пустой `Map`
 * пишется как `{}`, а `frontLightLevel = null` удаляет ключ, чтобы чтение вернуло `null`.
 */
internal object SettingsCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(preferences: Preferences): AppSettings = AppSettings(
        appearance = appearance(preferences),
        eink = eink(preferences),
        library = library(preferences),
        reader = reader(preferences),
        opds = opds(preferences),
        advanced = advanced(preferences),
    )

    fun encode(target: MutablePreferences, settings: AppSettings) {
        with(settings.appearance) {
            target[SettingsKeys.THEME_MODE] = themeMode.name
            target[SettingsKeys.DENSITY] = density.name
            target[SettingsKeys.FONT_SCALE] = fontScale
            target[SettingsKeys.INVERT_COVERS] = invertCovers
            target[SettingsKeys.TEXT_ONLY_MODE] = textOnlyMode
        }
        with(settings.eink) {
            target[SettingsKeys.REFRESH_MODE] = refreshMode.name
            target[SettingsKeys.REFRESH_ON_SCREEN_CHANGE] = refreshOnScreenChange
            target[SettingsKeys.REFRESH_ON_DIALOG] = refreshOnDialog
            target[SettingsKeys.REFRESH_ON_PAGE_CHANGE] = refreshOnPageChange
            target[SettingsKeys.GC_INTERVAL] = gcInterval
            // null — значит «подсветка не настраивалась»: ключ удаляется, а не пишется нулём.
            if (frontLightLevel == null) {
                target.remove(SettingsKeys.FRONT_LIGHT_LEVEL)
            } else {
                target[SettingsKeys.FRONT_LIGHT_LEVEL] = frontLightLevel
            }
            target[SettingsKeys.VOLUME_KEYS_AS_PAGE_TURN] = volumeKeysAsPageTurn
            target[SettingsKeys.TAP_ZONE_LEFT_PERCENT] = tapZoneLeftPercent
            target[SettingsKeys.TAP_ZONE_RIGHT_PERCENT] = tapZoneRightPercent
        }
        encodeLibrary(target = target, library = settings.library)

        with(settings.reader) {
            target[SettingsKeys.DEFAULT_READER_BY_FORMAT] = json.encodeToString(defaultReaderByFormat)
            target[SettingsKeys.LAST_USED_READER_BY_FORMAT] = json.encodeToString(lastUsedReaderByFormat)
            target[SettingsKeys.ENABLE_FBREADER_PROGRESS] = enableFbReaderProgress
            target[SettingsKeys.ENABLE_KOREADER_PROGRESS] = enableKoReaderProgress
            target[SettingsKeys.ENABLE_COOLREADER_PROGRESS] = enableCoolReaderProgress
            target[SettingsKeys.TEMP_EXPORT_ENABLED] = tempExportEnabled
            target[SettingsKeys.PROGRESS_SYNC_INTERVAL_MINUTES] = progressSyncIntervalMinutes
        }
        with(settings.opds) {
            target[SettingsKeys.ENABLE_OPDS] = enableOpds
            target[SettingsKeys.FEED_CACHE_MAX_AGE_MINUTES] = feedCacheMaxAgeMinutes
            target[SettingsKeys.DOWNLOAD_FILE_NAME_TEMPLATE] = downloadFileNameTemplate
            target[SettingsKeys.ALLOW_HTTP] = allowHttp
        }
        with(settings.advanced) {
            target[SettingsKeys.KEEP_METADATA_ON_FILE_CHANGE] = keepMetadataOnFileChange
            target[SettingsKeys.DUPLICATE_SCAN_ENABLED] = duplicateScanEnabled
            target[SettingsKeys.DB_BACKUP_ENABLED] = dbBackupEnabled
        }
    }

    private fun appearance(preferences: Preferences) = AppearanceSettings(
        themeMode = enumNameOrDefault(
            preferences[SettingsKeys.THEME_MODE],
            SettingsKeys.Defaults.THEME_MODE,
        ),
        density = enumNameOrDefault(
            preferences[SettingsKeys.DENSITY],
            SettingsKeys.Defaults.DENSITY,
        ),
        fontScale = preferences[SettingsKeys.FONT_SCALE] ?: SettingsKeys.Defaults.FONT_SCALE,
        invertCovers = preferences[SettingsKeys.INVERT_COVERS] ?: SettingsKeys.Defaults.INVERT_COVERS,
        textOnlyMode = preferences[SettingsKeys.TEXT_ONLY_MODE] ?: SettingsKeys.Defaults.TEXT_ONLY_MODE,
    )

    private fun eink(preferences: Preferences) = EinkSettings(
        refreshMode = enumNameOrDefault(
            preferences[SettingsKeys.REFRESH_MODE],
            SettingsKeys.Defaults.REFRESH_MODE,
        ),
        refreshOnScreenChange = preferences[SettingsKeys.REFRESH_ON_SCREEN_CHANGE]
            ?: SettingsKeys.Defaults.REFRESH_ON_SCREEN_CHANGE,
        refreshOnDialog = preferences[SettingsKeys.REFRESH_ON_DIALOG]
            ?: SettingsKeys.Defaults.REFRESH_ON_DIALOG,
        refreshOnPageChange = preferences[SettingsKeys.REFRESH_ON_PAGE_CHANGE]
            ?: SettingsKeys.Defaults.REFRESH_ON_PAGE_CHANGE,
        gcInterval = positiveIntOrDefault(
            preferences[SettingsKeys.GC_INTERVAL],
            SettingsKeys.Defaults.GC_INTERVAL,
        ),
        // Отсутствие ключа — единственный признак «не настраивалось»; значение же ограничиваем,
        // потому что уходит в вендорский API подсветки.
        frontLightLevel = percentOrNull(preferences[SettingsKeys.FRONT_LIGHT_LEVEL]),
        volumeKeysAsPageTurn = preferences[SettingsKeys.VOLUME_KEYS_AS_PAGE_TURN]
            ?: SettingsKeys.Defaults.VOLUME_KEYS_AS_PAGE_TURN,
        tapZoneLeftPercent = preferences[SettingsKeys.TAP_ZONE_LEFT_PERCENT]
            ?: SettingsKeys.Defaults.TAP_ZONE_LEFT_PERCENT,
        tapZoneRightPercent = preferences[SettingsKeys.TAP_ZONE_RIGHT_PERCENT]
            ?: SettingsKeys.Defaults.TAP_ZONE_RIGHT_PERCENT,
    )

    private fun library(preferences: Preferences) = LibrarySettings(
        gridColumns = positiveIntOrDefault(
            preferences[SettingsKeys.GRID_COLUMNS],
            SettingsKeys.Defaults.GRID_COLUMNS,
        ),
        rowsPerPage = positiveIntOrDefault(
            preferences[SettingsKeys.ROWS_PER_PAGE],
            SettingsKeys.Defaults.ROWS_PER_PAGE,
        ),
        metadataBackfillDone = preferences[SettingsKeys.METADATA_BACKFILL_DONE]
            ?: SettingsKeys.Defaults.METADATA_BACKFILL_DONE,
        viewPreset = enumNameOrDefault(
            preferences[SettingsKeys.VIEW_PRESET],
            SettingsKeys.Defaults.VIEW_PRESET,
        ),
        sortOrder = enumNameOrDefault(
            preferences[SettingsKeys.SORT_ORDER],
            SettingsKeys.Defaults.SORT_ORDER,
        ),
        sortDescending = preferences[SettingsKeys.SORT_DESCENDING] ?: SettingsKeys.Defaults.SORT_DESCENDING,
        grouping = enumNameOrDefault(
            preferences[SettingsKeys.GROUPING],
            SettingsKeys.Defaults.GROUPING,
        ),
        showMissing = preferences[SettingsKeys.SHOW_MISSING] ?: SettingsKeys.Defaults.SHOW_MISSING,
        showRemovableShelf = preferences[SettingsKeys.SHOW_REMOVABLE_SHELF]
            ?: SettingsKeys.Defaults.SHOW_REMOVABLE_SHELF,
        autoScanOnStart = preferences[SettingsKeys.AUTO_SCAN_ON_START]
            ?: SettingsKeys.Defaults.AUTO_SCAN_ON_START,
        scanSchedule = enumNameOrDefault(
            preferences[SettingsKeys.SCAN_SCHEDULE],
            SettingsKeys.Defaults.SCAN_SCHEDULE,
        ),
        missingCleanupDays = nonNegativeIntOrDefault(
            preferences[SettingsKeys.MISSING_CLEANUP_DAYS],
            SettingsKeys.Defaults.MISSING_CLEANUP_DAYS,
        ),
        metadataParallelism = positiveIntOrDefault(
            preferences[SettingsKeys.METADATA_PARALLELISM],
            SettingsKeys.Defaults.METADATA_PARALLELISM,
        ),
        tempCopyLimitMb = nonNegativeIntOrDefault(
            preferences[SettingsKeys.TEMP_COPY_LIMIT_MB],
            SettingsKeys.Defaults.TEMP_COPY_LIMIT_MB,
        ),
        coverCacheLimitMb = nonNegativeIntOrDefault(
            preferences[SettingsKeys.COVER_CACHE_LIMIT_MB],
            SettingsKeys.Defaults.COVER_CACHE_LIMIT_MB,
        ),
    )

    private fun reader(preferences: Preferences) = ReaderSettings(
        defaultReaderByFormat = decodeFormatMap(preferences[SettingsKeys.DEFAULT_READER_BY_FORMAT]),
        lastUsedReaderByFormat = decodeFormatMap(preferences[SettingsKeys.LAST_USED_READER_BY_FORMAT]),
        enableFbReaderProgress = preferences[SettingsKeys.ENABLE_FBREADER_PROGRESS]
            ?: SettingsKeys.Defaults.ENABLE_FBREADER_PROGRESS,
        enableKoReaderProgress = preferences[SettingsKeys.ENABLE_KOREADER_PROGRESS]
            ?: SettingsKeys.Defaults.ENABLE_KOREADER_PROGRESS,
        enableCoolReaderProgress = preferences[SettingsKeys.ENABLE_COOLREADER_PROGRESS]
            ?: SettingsKeys.Defaults.ENABLE_COOLREADER_PROGRESS,
        tempExportEnabled = preferences[SettingsKeys.TEMP_EXPORT_ENABLED]
            ?: SettingsKeys.Defaults.TEMP_EXPORT_ENABLED,
        progressSyncIntervalMinutes = positiveIntOrDefault(
            preferences[SettingsKeys.PROGRESS_SYNC_INTERVAL_MINUTES],
            SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES,
        ),
    )

    private fun opds(preferences: Preferences) = OpdsSettings(
        enableOpds = preferences[SettingsKeys.ENABLE_OPDS] ?: SettingsKeys.Defaults.ENABLE_OPDS,
        feedCacheMaxAgeMinutes = nonNegativeIntOrDefault(
            preferences[SettingsKeys.FEED_CACHE_MAX_AGE_MINUTES],
            SettingsKeys.Defaults.FEED_CACHE_MAX_AGE_MINUTES,
        ),
        downloadFileNameTemplate = preferences[SettingsKeys.DOWNLOAD_FILE_NAME_TEMPLATE]
            ?: SettingsKeys.Defaults.DOWNLOAD_FILE_NAME_TEMPLATE,
        allowHttp = preferences[SettingsKeys.ALLOW_HTTP] ?: SettingsKeys.Defaults.ALLOW_HTTP,
    )

    private fun advanced(preferences: Preferences) = AdvancedSettings(
        keepMetadataOnFileChange = preferences[SettingsKeys.KEEP_METADATA_ON_FILE_CHANGE]
            ?: SettingsKeys.Defaults.KEEP_METADATA_ON_FILE_CHANGE,
        duplicateScanEnabled = preferences[SettingsKeys.DUPLICATE_SCAN_ENABLED]
            ?: SettingsKeys.Defaults.DUPLICATE_SCAN_ENABLED,
        dbBackupEnabled = preferences[SettingsKeys.DB_BACKUP_ENABLED]
            ?: SettingsKeys.Defaults.DB_BACKUP_ENABLED,
    )

    /**
     * Имя константы enum'а или [fallback], если имя неизвестно.
     *
     * Безопасность обязательна: `enumValueOf` бросает исключение на мусоре, а файл настроек мог
     * быть записан другой версией приложения, где enum'а было больше.
     */
    private inline fun <reified T : Enum<T>> enumNameOrDefault(name: String?, fallback: T): T =
        if (name == null) fallback else runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    /**
     * Число, которое обязано быть положительным, или [fallback].
     *
     * Это не перестраховка, а защита от падения. `metadataParallelism` уходит в
     * `Executors.newFixedThreadPool(...)`, который на нуле бросает `IllegalArgumentException`
     * (проверено на JDK 21), а ноль в `gcInterval` превратил бы полный GC в «после каждого
     * обновления». Файл настроек доступен пользователю для правки, поэтому мусор в нём —
     * реальность, а не теория.
     */
    private fun positiveIntOrDefault(value: Int?, fallback: Int): Int =
        if (value == null || value <= 0) fallback else value

    /** Неотрицательное число: отрицательный лимит или срок не имеют смысла. */
    private fun nonNegativeIntOrDefault(value: Int?, fallback: Int): Int =
        if (value == null || value < 0) fallback else value

    /** Уровень подсветки приводится к диапазону вендорского API, а не к умолчанию. */
    private fun percentOrNull(value: Int?): Int? = value?.coerceIn(0, PERCENT_MAX)

    /** Карта «формат → профиль» из JSON; любой мусор читается как пустая карта. */
    private fun decodeFormatMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    /** Верхняя граница процента: столько же принимает `EinkController.setFrontLight`. */
    private const val PERCENT_MAX = 100
}


/**
 * Настройки библиотеки в DataStore.
 *
 * Вынесено из `encode` отдельной функцией: та перевалила за предел длины detekt. Ключи и порядок
 * записи те же — блок виден целиком и читается как таблица.
 */
private fun encodeLibrary(target: MutablePreferences, library: LibrarySettings) {
    with(library) {
            target[SettingsKeys.GRID_COLUMNS] = gridColumns
            target[SettingsKeys.VIEW_PRESET] = viewPreset.name
            target[SettingsKeys.METADATA_BACKFILL_DONE] = metadataBackfillDone
            target[SettingsKeys.ROWS_PER_PAGE] = rowsPerPage
            target[SettingsKeys.SORT_ORDER] = sortOrder.name
            target[SettingsKeys.SORT_DESCENDING] = sortDescending
            target[SettingsKeys.GROUPING] = grouping.name
            target[SettingsKeys.SHOW_MISSING] = showMissing
            target[SettingsKeys.AUTO_SCAN_ON_START] = autoScanOnStart
            target[SettingsKeys.SCAN_SCHEDULE] = scanSchedule.name
            target[SettingsKeys.MISSING_CLEANUP_DAYS] = missingCleanupDays
            target[SettingsKeys.METADATA_PARALLELISM] = metadataParallelism
            target[SettingsKeys.SHOW_REMOVABLE_SHELF] = showRemovableShelf
            target[SettingsKeys.TEMP_COPY_LIMIT_MB] = tempCopyLimitMb
            target[SettingsKeys.COVER_CACHE_LIMIT_MB] = coverCacheLimitMb
    }
}
