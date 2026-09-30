package io.github.bookshelf.feature.bookdetails

import io.github.bookshelf.core.data.model.ReadingStatus

/**
 * Состояние карточки книги (docs/eink-ux.md §6.4).
 *
 * Все поля — примитивы и подписи: экран не должен зависеть от формы доменной `Book`,
 * поэтому метаданные приходят сюда уже приведёнными к строкам (размер, дата, серия).
 * После Sprint 4 заполняется из `BookRepository.observe(bookId)`.
 */
/** Полка в списке выбора (UI-модель). */
data class CollectionItemUi(
    /** `collections.id`. */
    val id: String,
    /** Название полки. */
    val name: String,
    /** Уже лежит ли книга на этой полке. */
    val containsBook: Boolean = false,
)

data class BookDetailsUiState(
    /** `books.id` открытой книги. */
    val bookId: String = "",
    /** Название; при `parseState = FAILED` — заголовок из имени файла. */
    val title: String = "",
    /** Авторы одной строкой. */
    val authors: String = "",
    /** Серия и номер: «Плоский мир #5». */
    val series: String? = null,
    /**
     * Имя серии без номера — ключ фасета серий.
     *
     * Отдельно от [series]: в карточке показывается подпись с номером («Приключения Флинкса #3»),
     * а в фильтр серии уходит её имя — иначе переход по серии не нашёл бы ни одной книги.
     */
    val seriesName: String? = null,
    /** Год издания. */
    val publishedYear: Int? = null,
    /** Язык книги. */
    val language: String? = null,
    /** Издательство. */
    val publisher: String? = null,
    /** ISBN, если он есть в метаданных. */
    val isbn: String? = null,
    /** Число страниц из метаданных. */
    val pageCount: Int? = null,
    /** Размер файла подписью: «2,3 МБ». */
    val sizeLabel: String? = null,
    /** Дата добавления подписью. */
    val addedAtLabel: String? = null,
    /** Аннотация; на e-ink сворачиваемая. */
    val description: String? = null,
    /** Показывается список полок для выбора — по кнопке «В полку». */
    val isPickingCollection: Boolean = false,
    /** Полки, в которые книгу можно положить. */
    val collections: List<CollectionItemUi> = emptyList(),
    /** Путь внутри источника: «Лем/Солярис.fb2» (без имени папки). */
    val relativePath: String = "",
    /** Имя папки-источника: «Books». Пусто — источник ещё не прочитан. */
    val sourceName: String = "",
    /** Путь к обложке в кэше; `null` — заглушка с названием. */
    val coverPath: String? = null,
    /** Статус чтения из `books.readingStatus`. */
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    /** Книга в избранном. */
    val isFavorite: Boolean = false,
    /** Причина ошибки разбора файла (`parseState = FAILED`). */
    val parseError: String? = null,
    /** Идёт загрузка карточки. */
    val isLoading: Boolean = false,
    /** Показывается подтверждение удаления книги с устройства. */
    val isConfirmingDelete: Boolean = false,
    /** Идёт удаление файла и записи. */
    val isDeleting: Boolean = false,
    /**
     * Книга в формате, который оптимизируется без потери данных (FB2 и FB2.ZIP).
     *
     * Кнопка показывается только для них: обещать оптимизацию EPUB или PDF, которых механизм не
     * касается, значило бы дать кнопку-обманку.
     */
    val canOptimize: Boolean = false,
    /** Идёт оптимизация файла: работа тяжёлая, и повторное нажатие во время неё ничего не делает. */
    val isOptimizing: Boolean = false,
    /**
     * Результат последней оптимизации словами: «Сэкономлено 2,2 МБ…».
     *
     * Отдельно от [error]: успех в плашке ошибки читался бы как сбой. Держится до следующей
     * команды — карточка перечитывает книгу после замены файла (меняется размер), и сообщение
     * обязано это пережить.
     */
    val optimizationMessage: String? = null,
    /**
     * Книга удалена: экран закрывается, возвращаться на карточку некуда.
     *
     * Отдельный признак, а не `bookId == ""`: по нему экран делает переход, и он же отличает
     * «удалили» от «книгу не нашли при открытии».
     */
    val isDeleted: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)
