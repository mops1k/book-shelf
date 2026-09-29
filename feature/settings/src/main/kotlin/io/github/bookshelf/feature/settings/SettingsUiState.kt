package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.SettingsKeys
import io.github.bookshelf.core.datastore.model.ThemeMode

/** Разделы настроек (docs/eink-ux.md §6.8) — каждый открывается отдельным экраном. */
enum class SettingsSectionKind {
    /** Плотность, сетка/список, обложки, сортировка и группировка по умолчанию. */
    APPEARANCE,

    /** Режим обновления экрана, полный refresh, инверсия обложек, тёмная тема, подсветка. */
    EINK_SCREEN,

    /** Источники, автоскан, исключения, приоритет метаданных. */
    LIBRARY_SCAN,

    /** Список читалок, читалка по умолчанию для формата, пользовательские профили. */
    READERS,

    /** Каталоги OPDS, авторизация, загрузки. */
    OPDS,

    /** Размер кэша обложек, очистка, экспорт и импорт библиотеки. */
    STORAGE,

    /** Версия, лицензии, диагностика e-ink и вендора. */
    ABOUT,
}

/** Строка раздела настроек: заголовок и подпись текущего значения. */
data class SettingsSectionUi(
    /** К какому разделу ведёт строка. */
    val kind: SettingsSectionKind,
    /** Название раздела. */
    val title: String,
    /** Короткая подпись того, что внутри. */
    val summary: String,
)

/** Читалка в списке настроек (UI-модель, а не полный профиль). */
data class ReaderItemUi(
    /** `reader_profiles.id`. */
    val id: String,
    /** Название приложения-читалки. */
    val name: String,
    /** Имя пакета. */
    val packageName: String,
    /** true — читалка по умолчанию хотя бы для одного формата. */
    val isDefault: Boolean = false,
)

/**
 * Источник книг в настройках (UI-модель).
 *
 * @param displayName имя папки, как её показал системный выборщик.
 * @param uri `content://`-URI дерева; именно по нему идёт сканирование.
 * @param booksCount сколько книг нашлось в папке последним сканом.
 */
data class SourceItemUi(
    /** `sources.id`. */
    val id: String,
    /** Имя папки на устройстве. */
    val displayName: String,
    /** URI дерева SAF — по нему сканер читает содержимое. */
    val uri: String,
    /** Книг найдено последним сканом; `null` — скан ещё не выполнялся. */
    val booksCount: Int? = null,
    /**
     * Сохранено ли право записи в папку.
     *
     * `false` значит, что скачивание в неё отклонит провайдер: пользователь должен увидеть это
     * в настройках, а не в виде непонятной ошибки при загрузке книги.
     */
    val hasWriteAccess: Boolean = true,
)

/** Разрешение, которое приложение может запросить у пользователя. */
data class PermissionItemUi(
    /** Системное имя разрешения. */
    val permission: String,
    /** Что разрешение разрешает — по-русски, без системных терминов. */
    val title: String,
    /** Зачем оно именно нам. */
    val subtitle: String,
    /** Выдано ли оно сейчас. */
    val isGranted: Boolean,
)

/** Диагностика e-ink для раздела «О приложении» (docs/eink-ux.md §8.5). */
data class EinkDiagnosticsUi(
    /**
     * Доступны ли команды вендора: ими управляет блок «Экран» в настройках.
     *
     * Раньше на этом месте стоял признак «устройство с бумагой», и два разных вопроса — «какой
     * экран» и «что мы умеем» — отвечались одним значением: Bigme с настоящей бумагой выглядел
     * обычным экраном только потому, что вендорского контроллера для него нет.
     */
    val hasVendorCommands: Boolean = false,
    /** Подпись вендора: «Onyx Boox», «Bigme», «Неизвестное устройство». */
    val vendorLabel: String = "Неизвестное устройство",
    /** Тип экрана словами: «обычный экран», «электронная бумага, цветная». */
    val screenLabel: String = "обычный экран",
    /** Доступен быстрый режим обновления. */
    val supportsFastMode: Boolean = false,
    /** Управляемая подсветка. */
    val supportsFrontLight: Boolean = false,
)

/**
 * Настройки в виде UI-модели.
 *
 * Значения — подписи и примитивы: экран настроек показывает «Обновление экрана: Полное
 * (медленнее, но без призраков)», а не сырое значение enum. После Sprint 5 модель собирается
 * маппингом из `AppSettings` (`core:datastore`) рядом с экраном.
 */
data class AppSettingsUi(
    /** Плотность интерфейса: Крупный / Обычный / Плотный. */
    val densityLabel: String = "Крупный",
    /** Режим отображения полки по умолчанию. */
    val viewModeLabel: String = "Сетка",
    /** Вид полки по умолчанию: сетка/список/компактно либо обзор по авторам/сериям. */
    val viewPreset: ShelfViewPreset = ShelfViewPreset.GRID,
    /**
     * Сортировка полки по умолчанию — то, чем полка открывается (`LibraryViewModel`).
     *
     * Начальное значение дублирует `SettingsKeys.Defaults.SORT_ORDER`: до первой эмиссии настроек
     * показывать нечего, но расходиться с документированным умолчанием эти значения не должны.
     */
    val sortOrder: SortOrder = SortOrder.TITLE,
    /** Обратный порядок для сортировки по умолчанию. */
    val sortDescending: Boolean = false,
    /** Группировка полки по умолчанию: заголовки-разделители внутри списка. */
    val grouping: Grouping = Grouping.NONE,
    /** Показывать обложки на полке. */
    val showCovers: Boolean = true,
    /** Масштаб шрифта интерфейса в процентах: 100/115/130/150. */
    val fontScalePercent: Int = 100,
    /** Засечный шрифт для заголовков книг. */
    val serifTitles: Boolean = false,
    /** Режим обновления экрана: Авто / Полный / Быстрый. */
    val refreshModeLabel: String = "Авто",
    /** Полный refresh при смене экрана. */
    val fullRefreshOnScreenChange: Boolean = true,
    /** Полный refresh при открытии и закрытии диалога. */
    val fullRefreshOnDialog: Boolean = true,
    /** Полный refresh при смене страницы. */
    val fullRefreshOnPage: Boolean = true,
    /** Инвертировать обложки при отрисовке (файл кэша не перезаписывается). */
    val invertCovers: Boolean = false,
    /**
     * Тема как её выбрал пользователь: светлая / тёмная / системная.
     *
     * Здесь именно выбор, а не готовое «тёмно/светло»: `SYSTEM` разрешается при отрисовке
     * (`MainActivity`), и булево потеряло бы этот вариант при первом же открытии экрана.
     */
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    /** Полный GC каждые N частичных обновлений (`EinkController.setGcInterval`). */
    val gcInterval: Int = 10,
    /** Уровень подсветки 0..100; `null` — пользователь её не настраивал. */
    val frontLightLevel: Int? = null,
    /** Клавиши громкости листают страницы (на устройствах без Page Up/Down). */
    val volumeKeysAsPageTurn: Boolean = false,
    /** Отключить все анимации (на e-ink анимаций быть не должно). */
    val animationsDisabled: Boolean = true,
    /** Запускать скан при старте приложения. */
    val scanOnStart: Boolean = true,
    /** Ограничение глубины обхода дерева. */
    val scanDepthLimit: Int = 8,
    /** Пропускать скрытые папки (`.*`). */
    val skipHiddenFolders: Boolean = true,
    /** Приоритет метаданных: встроенные / имя файла. */
    val metadataPriorityLabel: String = "Встроенные",
    /** Всегда спрашивать читалку, а не открывать по умолчанию. */
    val askReaderAlways: Boolean = false,
    /** Лимит дискового кэша обложек, МБ: 100 / 200 / 500 (`CoverCleanupWorker`). */
    val coverCacheLimitMb: Int = 200,
    /** Синхронизация прогресса чтения с читалкой (FBReader): по умолчанию выключена. */
    val isFbReaderProgressEnabled: Boolean = false,
    /** Как часто опрашивать читалку, минут; варианты ограничены `ProgressSyncInterval`. */
    val progressSyncIntervalMinutes: Int = SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES,
    /** Разрешена ли временная копия книги для читалки, которая не открывает ссылку. */
    val isTempExportEnabled: Boolean = SettingsKeys.Defaults.TEMP_EXPORT_ENABLED,
    /** Сохранять ли данные книги (оценку, статус, полки, прогресс), когда её файл заменили. */
    val isKeepMetadataOnFileChange: Boolean = SettingsKeys.Defaults.KEEP_METADATA_ON_FILE_CHANGE,
    /** Лимит временной копии книги из облачного источника, МБ: 50 / 100 / 200. */
    val tempCopyLimitMb: Int = 100,
    /** Шаблон имени скачанного файла (`opds.downloadFileNameTemplate`). */
    val downloadFileNameTemplate: String = SettingsKeys.Defaults.DOWNLOAD_FILE_NAME_TEMPLATE,
    /** Возраст кэша фидов в минутах (`opds.feedCacheMaxAgeMinutes`). */
    val feedCacheMaxAgeMinutes: Int = SettingsKeys.Defaults.FEED_CACHE_MAX_AGE_MINUTES,
)

/**
 * Состояние экрана настроек.
 *
 * На этапе каркаса заполнено значением по умолчанию: репозиториев и `EinkController`
 * не вызываем. После Sprint 5 [settings] собирается из `AppSettings`, [installedReaders] —
 * из `ReaderRepository.installedReaders()`, [diagnostics] — из `EinkController`.
 */
data class SettingsUiState(
    /** Разделы настроек в порядке отображения. */
    val sections: List<SettingsSectionUi> = defaultSettingsSections(),
    /** Текущие значения настроек. */
    val settings: AppSettingsUi = AppSettingsUi(),
    /** Установленные читалки. */
    val installedReaders: List<ReaderItemUi> = emptyList(),
    /** Папки-источники, в которых приложение ищет книги. */
    val sources: List<SourceItemUi> = emptyList(),
    /** Идёт добавление или удаление папки. */
    val isSourceBusy: Boolean = false,
    /** Диагностика e-ink для раздела «О приложении». */
    val diagnostics: EinkDiagnosticsUi = EinkDiagnosticsUi(),
    /** Идёт загрузка настроек. */
    val isLoading: Boolean = false,
    /** Идёт пересканирование всех папок: база очищена, сканы запущены. */
    val isRescanning: Boolean = false,
    /** Показывается подтверждение сброса: «Сбросить настройки? Да/Нет». */
    val isConfirmingReset: Boolean = false,
    /** Идёт сброс настроек. */
    val isResetting: Boolean = false,
    /** Идёт очистка кэша обложек («Очистить кэш обложек» в «Хранилище и кэш»). */
    val isClearingCoverCache: Boolean = false,
    /** Результат очистки кэша обложек: «освобождено 12,4 МБ» или причина сбоя. */
    val coverCacheMessage: String? = null,
    /** Идёт проверка связи с читалкой (API-сервис FBReader). */
    val isCheckingReaderApi: Boolean = false,
    /** Результат проверки связи: «API отвечает…» или причина отказа. `null` — ещё не проверяли. */
    val readerApiStatus: String? = null,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)

/** Список разделов настроек в порядке отображения — чистые данные, без обращений к хранилищу. */
fun defaultSettingsSections(): List<SettingsSectionUi> = listOf(
    SettingsSectionUi(
        kind = SettingsSectionKind.APPEARANCE,
        title = "Внешний вид",
        summary = "Тема, плотность, масштаб шрифта и инверсия обложек",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.EINK_SCREEN,
        title = "Экран (e-ink)",
        summary = "Режим обновления, события полного кадра, подсветка и клавиши",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.LIBRARY_SCAN,
        title = "Библиотека и сканирование",
        summary = "Вид и порядок полки, лимиты диска, папки-источники и разрешения",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.READERS,
        title = "Читалки",
        summary = "Установленные читалки и читалка по умолчанию",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.OPDS,
        title = "Каталоги OPDS",
        summary = "Переход к каталогам: список, добавление по адресу, загрузки",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.STORAGE,
        title = "Хранилище и кэш",
        summary = "Очистка кэша обложек и экспорт библиотеки",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.ABOUT,
        title = "О приложении и диагностика",
        summary = "Версия, диагностика e-ink и системные разрешения",
    ),
)
