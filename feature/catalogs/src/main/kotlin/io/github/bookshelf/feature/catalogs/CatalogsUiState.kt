package io.github.bookshelf.feature.catalogs

import io.github.bookshelf.core.data.model.DownloadState

/** Какой из четырёх экранов модуля OPDS показан сейчас. */
enum class CatalogsMode {
    /** Список добавленных каталогов. */
    LIST,

    /** Форма ручного добавления каталога. */
    ADD_FORM,

    /** Браузер фида: хлебные крошки, обложки записей, поиск. */
    FEED,

    /** Экран загрузок. */
    DOWNLOADS,
}

/** Строка списка каталогов (UI-модель). */
data class CatalogUi(
    /** `opds_catalogs.id`. */
    val id: String,
    /** Название, как его задал пользователь. */
    val title: String,
    /** URL каталога. */
    val url: String,
    /** Требуется авторизация Basic/Digest. */
    val requiresAuth: Boolean = false,
    /** Подпись «Последний раз: 12.03». */
    val lastOpenedLabel: String? = null,
    /** false — каталог недоступен (ошибка сети или авторизации). */
    val isAvailable: Boolean = true,
)

/** Запись фида OPDS (UI-модель). */
data class FeedEntryUi(
    /** `dc:identifier`/`atom:id` записи. */
    val id: String,
    /** Название книги. */
    val title: String,
    /** Авторы одной строкой. */
    val authors: String,
    /** Путь к уже скачанной обложке в кэше; `null` — заглушка с названием. */
    val coverPath: String? = null,
    /** URL обложки из фида — нужен загрузчику (Sprint 8). */
    val coverUrl: String? = null,
    /** Состояние загрузки этой записи; `null` — книгу ещё не качали. */
    val download: EntryDownloadUi? = null,
    /** Форматы, доступные к скачиванию: `FB2`, `EPUB`. */
    val formats: List<String> = emptyList(),
    /** Подпись размера файла: «1,2 МБ». */
    val sizeLabel: String? = null,
    /** Язык записи. */
    val language: String? = null,
    /**
     * Раздел каталога, а не книга.
     *
     * Раньше признак не был вынесен в UI, и карточка под каждым пунктом рисовала «Скачать»,
     * а нажатие на раздел пыталось ставить в очередь загрузку. Теперь раздел открывается, а
     * кнопка «Скачать» есть только у книг.
     */
    val isSection: Boolean = false,
    /** Адрес вложенного фида — для разделов; `null` у книг. */
    val href: String? = null,
)

/** Текущий фид OPDS (UI-модель). */
data class FeedUi(
    /** Заголовок фида. */
    val title: String,
    /** Хлебные крошки от корня каталога (`rel="up"`/`start`). */
    val breadcrumbs: List<String> = emptyList(),
    /** Записи: книги или разделы навигации. */
    val entries: List<FeedEntryUi> = emptyList(),
    /** true — навигационный фид (разделы), false — acquisition (книги). */
    val isNavigation: Boolean = false,
    /** Есть ли следующая страница фида (`rel="next"`). */
    val hasNextPage: Boolean = false,
)

/** Строка списка загрузок (UI-модель). */
data class DownloadUi(
    /** `opds_downloads.id`. */
    val id: String,
    /** `atom:id` записи фида: по нему состояние загрузки находится в карточке записи. */
    val entryId: String,
    /** Состояние прогона загрузки — из него считаются подписи. */
    val state: DownloadState,
    /** Название книги. */
    val title: String,
    /** Подпись состояния: «В очереди», «Скачивается», «Готово», «Ошибка». */
    val stateLabel: String,
    /** Прогресс в процентах, 0..100. */
    val progressPercent: Int = 0,
    /** true — загрузка активна: строка показывает статичный индикатор-полосу. */
    val isActive: Boolean = false,
    /** `books.id` — появляется после постобработки завершённой загрузки. */
    val bookId: String? = null,
    /** Причина ошибки, если загрузка не удалась. */
    val error: String? = null,
)

/**
 * Состояние загрузки записи для карточки фида.
 *
 * Раньше после «Скачать» карточка записи молча закрывалась, и на месте — в списке книг — не было
 * ни признака, что загрузка началась, ни дороги к очереди (жалоба Александра, 28.09.2026).
 */
data class EntryDownloadUi(
    /** Подпись состояния: «Загрузка началась: в очереди», «Загрузка идёт: 45%», «Скачано». */
    val label: String,
    /** Прогресс в процентах, 0..100. */
    val progressPercent: Int = 0,
    /** Загрузка ещё идёт: повторно качать не нужно. */
    val isActive: Boolean = false,
    /** Загрузка завершена: файл уже на устройстве. */
    val isDone: Boolean = false,
)

/**
 * Поля формы ручного добавления каталога (docs/opds.md §3.1).
 *
 * Пароль живёт только в форме: в БД уходит его alias в шифрованном хранилище,
 * сам пароль не логируется и не попадает в экспорт настроек.
 */
data class AddCatalogFormUi(
    /** Название каталога — как его увидит пользователь. */
    val title: String = "",
    /** URL каталога; обязателен, схема проверяется. */
    val url: String = "",
    /** Логин Basic/Digest, если каталог его требует. */
    val username: String = "",
    /** Пароль; хранится в шифрованном виде, в БД — только alias. */
    val password: String = "",
    /** Открывать каталог сразу после добавления. */
    val openImmediately: Boolean = false,
)

/**
 * Состояние модуля OPDS.
 *
 * На этапе каркаса заполнено значением по умолчанию: сети нет вообще (единственный сетевой
 * модуль — `core:opds`, и он не вызывается). После Sprint 8 [catalogs], [currentFeed]
 * и [downloads] приходят из `OpdsRepository`.
 */
data class CatalogsUiState(
    /** Какой экран модуля показан. */
    val mode: CatalogsMode = CatalogsMode.LIST,
    /** Добавленные каталоги. */
    val catalogs: List<CatalogUi> = emptyList(),
    /** Открытый фид; `null` — фид ещё не загружен. */
    val currentFeed: FeedUi? = null,
    /** Очередь и история загрузок. */
    val downloads: List<DownloadUi> = emptyList(),
    /** Поля формы добавления каталога. */
    val addForm: AddCatalogFormUi = AddCatalogFormUi(),
    /** Поисковый запрос внутри фида (OpenSearch). */
    val feedQuery: String = "",
    /** Идёт сетевой запрос. */
    val isLoading: Boolean = false,
    /**
     * Идёт догрузка следующей страницы фида.
     *
     * Отдельно от [isLoading]: при догрузке список уже виден, и индикатор ставится в его конец,
     * а не поверх всего экрана — иначе пользователь решил бы, что экран перезагружается.
     */
    val isLoadingMore: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)
