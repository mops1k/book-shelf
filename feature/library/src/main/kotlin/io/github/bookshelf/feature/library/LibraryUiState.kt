package io.github.bookshelf.feature.library

import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.feature.library.model.ViewMode

/**
 * Карточка книги на полке (UI-модель).
 *
 * Собирается из доменной `Book` рядом с экраном: экран не должен видеть поля Room-сущности,
 * а сетка не должна зависеть от изменений в `core:data`.
 */
data class BookCardUi(
    /** `books.id` — ключ для навигации и `key` в ленивой сетке. */
    val id: String,
    /** Название книги; при `parseState = FAILED` — заголовок из имени файла. */
    val title: String,
    /** Авторы одной строкой: «Лем, Станислав». */
    val authors: String,
    /** Путь к файлу обложки в кэше; `null` — рисуется заглушка с названием. */
    val coverPath: String? = null,
    /** Статус чтения — из `books.readingStatus`. */
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    /** Прогресс чтения в процентах; `null` — читалка его не отдаёт (Sprint 7). */
    val progressPercent: Int? = null,
)

/**
 * Активные чипы фильтров над полкой (UI-модель).
 *
 * Строки, а не доменные сущности: полке нужны только подписи чипов, а фасеты
 * с числами книг считает экран фильтров.
 */
data class FilterChipsUi(
    /** Подписи выбранных авторов. */
    val authors: List<String> = emptyList(),
    /** Подписи выбранных серий. */
    val series: List<String> = emptyList(),
    /** Выбранные форматы: `FB2`, `EPUB`, … */
    val formats: List<String> = emptyList(),
    /** Выбранные статусы чтения. */
    val statuses: List<ReadingStatus> = emptyList(),
    /** Выбранные теги. */
    val tags: List<String> = emptyList(),
) {
    /** Сколько фильтров выбрано — для подписи «Фильтры (2)». */
    val activeCount: Int
        get() = authors.size + series.size + formats.size + statuses.size + tags.size

    /** Есть ли что сбрасывать. */
    val isEmpty: Boolean get() = activeCount == 0
}

/**
 * Состояние экрана полки.
 *
 * На этапе каркаса заполнено только значение по умолчанию: запросов к БД нет.
 * После Sprint 4 [books] и [total] приходят из `BookRepository.page(LibraryQuery)`
 * как `PageResult<Book>`, а сортировка, группировка и фильтры — поля `LibraryQuery`.
 */
data class LibraryUiState(
    /** Книги текущей страницы. */
    val books: List<BookCardUi> = emptyList(),
    /** Всего книг по текущему запросу (для подписи «Стр. N/M · K книг»). */
    val total: Int = 0,
    /** Номер текущей страницы, нумерация с нуля. */
    val page: Int = 0,
    /** Размер страницы: берётся из плотности интерфейса (docs/eink-ux.md §2.3). */
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    /** Режим отображения: сетка, список или компактный список. */
    val mode: ViewMode = ViewMode.GRID,
    /** Порядок сортировки — доменный enum, чтобы не дублировать его в UI. */
    val sort: SortOrder = SortOrder.AUTHOR,
    /** Группировка с заголовками-разделителями. */
    val grouping: Grouping = Grouping.NONE,
    /** Чипы активных фильтров над полкой. */
    val activeFilters: FilterChipsUi = FilterChipsUi(),
    /** Идёт загрузка страницы. */
    val isLoading: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
) {
    /** Всего страниц при текущем размере страницы. */
    val totalPages: Int get() = pageCount(total, pageSize)

    /** Доступна ли предыдущая страница. */
    val hasPrevious: Boolean get() = hasPreviousPage(page)

    /** Доступна ли следующая страница. */
    val hasNext: Boolean get() = hasNextPage(page, total, pageSize)
}
