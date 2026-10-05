package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.readerlink.mime.ReaderFormatCategory

import io.github.bookshelf.core.designsystem.text.AppText

import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.SettingsKeys
import io.github.bookshelf.core.datastore.model.ThemeMode

/** Разделы настроек («E-ink UX» §6.8) — каждый открывается отдельным экраном. */
enum class SettingsSectionKind {
    /** Плотность, сетка/список, обложки, сортировка и группировка по умолчанию. */
    APPEARANCE,

    /** Режим обновления экрана, события полного кадра и подсветка: только там, где есть вендорские команды. */
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
    /** Название раздела. Ресурс, не текст: строки живут в `res/values` (ru) и `values-en`. */
    val titleRes: Int,
    /** Короткая подпись того, что внутри. Ресурс, не текст. */
    val summaryRes: Int,
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
    /** Форматы, которые эта читалка умеет открывать: по ним строится выбор «формат → читалка». */
    val supportedExtensions: Set<String> = emptySet(),
)

/**
 * Строка категории на первом уровне экрана «Читалки».
 *
 * @param formats сколько форматов в категории
 * @param bound у скольких из них уже выбрана читалка
 */
data class ReaderCategoryUi(
    val category: ReaderFormatCategory,
    val formats: Int,
    val bound: Int,
)

/** Уровень экрана «Читалки»: категории → форматы → читалки формата. */
sealed interface ReadersLevelUi {

    /** Первый уровень: книги, документы, манги, другое. */
    data object Categories : ReadersLevelUi

    /** Второй уровень: форматы выбранной категории. */
    data class Formats(val category: ReaderFormatCategory) : ReadersLevelUi

    /** Третий уровень: читалки, которые умеют выбранный формат. */
    data class ReadersOfFormat(val extension: String) : ReadersLevelUi
}

/**
 * Строка «формат → читалка»: чем открывается формат.
 *
 * Александр (01.10.2026): «надо сделать, чтобы пользователь мог выбрать, какому формату какую
 * читалку привязать, если читалка поддерживает конечно этот формат». Список вариантов уже
 * ограничен поддерживаемыми форматами, поэтому в строке нет читалок, которые формат не умеют.
 */
data class FormatReaderUi(
    /** Расширение формата: `fb2`, `epub`, `pdf`. */
    val extension: String,
    /** Категория формата: книги, документы, манги, другое. */
    val category: ReaderFormatCategory = ReaderFormatCategory.OTHER,
    /** Подпись текущей читалки; `null` — формат ни за кем не закреплён. */
    val readerName: String?,
    /** id текущей читалки; `null` — формат ни за кем не закреплён. */
    val readerId: String?,
    /** Сколько читалок умеют этот формат: если ноль, строку показывать незачем. */
    val candidates: Int,
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
    /** Что разрешение разрешает — словами, без системных терминов. Ресурс, не текст. */
    val titleRes: Int,
    /** Зачем оно именно нам. Ресурс, не текст. */
    val subtitleRes: Int,
    /** Выдано ли оно сейчас. */
    val isGranted: Boolean,
)

/** Диагностика e-ink для раздела «О приложении» («E-ink UX» §8.5). */
data class EinkDiagnosticsUi(
    /**
     * Доступны ли команды вендора: ими управляет блок «Экран» в настройках.
     *
     * Раньше на этом месте стоял признак «устройство с бумагой», и два разных вопроса — «какой
     * экран» и «что мы умеем» — отвечались одним значением: Bigme с настоящей бумагой выглядел
     * обычным экраном только потому, что вендорского контроллера для него нет.
     */
    val hasVendorCommands: Boolean = false,
    /**
     * Имя производителя: «Onyx Boox», «Bigme». `null` — вендор не определён, и экран показывает
     * переводимое «неизвестное устройство» из своих ресурсов.
     */
    val vendorLabel: String? = null,
    /** Тип экрана словами: ресурс выбирает [EinkScreen.text], показывает экран. */
    val screenLabel: AppText = AppText(R.string.settings_screen_normal),
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
    /** Плотность: значение настройки, а не её подпись. */
    val density: EinkDensityLike = EinkDensityLike.LARGE,
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
    /** Полка съёмного носителя: карта памяти или флешка отдельной полкой в списке полок. */
    val showRemovableShelf: Boolean = true,
    /** Группировка полки по умолчанию: заголовки-разделители внутри списка. */
    val grouping: Grouping = Grouping.NONE,
    /** Показывать обложки на полке. */
    val showCovers: Boolean = true,
    /** Масштаб шрифта интерфейса в процентах: 100/115/130/150. */
    val fontScalePercent: Int = 100,
    /** Засечный шрифт для заголовков книг. */
    val serifTitles: Boolean = false,
    /** Режим обновления экрана: Авто / Полный / Быстрый. */
    /** Режим обновления экрана: значение настройки, а не её подпись. */
    val refreshMode: RefreshModeLike = RefreshModeLike.AUTO,
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
    /** Всегда спрашивать читалку, а не открывать по умолчанию. */
    val askReaderAlways: Boolean = false,
    /** Лимит дискового кэша обложек, МБ: 100 / 200 / 500 (`CoverCleanupWorker`). */
    val coverCacheLimitMb: Int = 200,
    /** Синхронизация прогресса чтения с читалкой (FBReader): по умолчанию выключена. */
    val isFbReaderProgressEnabled: Boolean = false,
    /** Прогресс из KOReader: sidecar-файл рядом с книгой; по умолчанию выключен. */
    val isKoReaderProgressEnabled: Boolean = false,
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
    /** Строки «формат → читалка»: чем открывается каждый формат. */
    val readerFormats: List<FormatReaderUi> = emptyList(),
    /** Категории форматов: первый уровень экрана «Читалки». */
    val readerCategories: List<ReaderCategoryUi> = emptyList(),
    /** Где сейчас человек на экране «Читалки». */
    val readersLevel: ReadersLevelUi = ReadersLevelUi.Categories,
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
    /** Результат очистки кэша обложек: «освобождено 12,4 МБ» или причина сбоя — переводимый текст. */
    val coverCacheMessage: AppText? = null,
    /** Идёт проверка связи с читалкой (API-сервис FBReader). */
    val isCheckingReaderApi: Boolean = false,
    /** Результат проверки связи: «API отвечает…» или причина отказа. `null` — ещё не проверяли. */
    val readerApiStatus: AppText? = null,
    /** Текст ошибки для `EinkErrorBanner`: ресурс, а не готовая строка («TDD» §5). */
    val error: AppText? = null,
)

/**
 * Список разделов настроек в порядке отображения — чистые данные, без обращений к хранилищу.
 *
 * @param opdsEnabled сборка с OPDS (`ENABLE_OPDS`): без него раздела каталогов нет вовсе —
 *   экранов и переключателей без потребителя в интерфейсе не держим (ADR-006).
 * @param einkEnabled есть ли у устройства вендорские команды e-ink (`EinkController.
 *   hasVendorCommands`). Без них раздел «Экран (e-ink)» состоял бы из одних неработающих
 *   переключателей, и в списке его нет (решение Александра, 05.10.2026). Переключатель «Клавиши
 *   громкости листают страницы» работает и без вендорского контроллера, поэтому он живёт
 *   в «Внешнем виде», а не здесь.
 */
fun defaultSettingsSections(
    opdsEnabled: Boolean = true,
    einkEnabled: Boolean = true,
): List<SettingsSectionUi> = listOf(
    SettingsSectionUi(
        kind = SettingsSectionKind.APPEARANCE,
        titleRes = R.string.settings_section_appearance,
        summaryRes = R.string.settings_section_appearance_summary,
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.EINK_SCREEN,
        titleRes = R.string.settings_section_eink,
        summaryRes = R.string.settings_section_eink_summary,
    ).takeIf { einkEnabled },
    SettingsSectionUi(
        kind = SettingsSectionKind.LIBRARY_SCAN,
        titleRes = R.string.settings_section_library,
        summaryRes = R.string.settings_section_library_summary,
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.READERS,
        titleRes = R.string.settings_section_readers,
        summaryRes = R.string.settings_section_readers_summary,
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.OPDS,
        titleRes = R.string.settings_section_opds,
        summaryRes = R.string.settings_section_opds_summary,
    ).takeIf { opdsEnabled },
    SettingsSectionUi(
        kind = SettingsSectionKind.STORAGE,
        titleRes = R.string.settings_section_storage,
        summaryRes = R.string.settings_section_storage_summary,
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.ABOUT,
        titleRes = R.string.settings_section_about,
        summaryRes = R.string.settings_section_about_summary,
    ),
).filterNotNull()
