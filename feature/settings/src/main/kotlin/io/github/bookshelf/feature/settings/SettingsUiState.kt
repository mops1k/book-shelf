package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.datastore.model.ShelfViewPreset

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
    /** Устройство с электронной бумагой. */
    val isEinkDevice: Boolean = false,
    /** Подпись вендора: «Onyx Boox», «Rockchip (Meebook)», «Универсальный». */
    val vendorLabel: String = "Универсальный",
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
    /** Тёмная тема: белое на чёрном. */
    val darkTheme: Boolean = false,
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
    /** Размер дискового кэша обложек. */
    val coverCacheSizeLabel: String = "128 МБ",
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
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)

/** Список разделов настроек в порядке отображения — чистые данные, без обращений к хранилищу. */
fun defaultSettingsSections(): List<SettingsSectionUi> = listOf(
    SettingsSectionUi(
        kind = SettingsSectionKind.APPEARANCE,
        title = "Внешний вид",
        summary = "Плотность, сетка или список, обложки, сортировка по умолчанию",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.EINK_SCREEN,
        title = "Экран (e-ink)",
        summary = "Режим обновления, полный refresh, инверсия обложек, тёмная тема, подсветка",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.LIBRARY_SCAN,
        title = "Библиотека и сканирование",
        summary = "Источники, автоскан, исключения, приоритет метаданных",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.READERS,
        title = "Читалки",
        summary = "Читалка по умолчанию для формата, профили, «Проверить»",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.OPDS,
        title = "Каталоги OPDS",
        summary = "Список каталогов, авторизация, загрузки",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.STORAGE,
        title = "Хранилище и кэш",
        summary = "Размер кэша обложек, очистка, экспорт и импорт библиотеки",
    ),
    SettingsSectionUi(
        kind = SettingsSectionKind.ABOUT,
        title = "О приложении и диагностика",
        summary = "Версия, лицензии, «приложение работает полностью офлайн», вендор e-ink",
    ),
)
