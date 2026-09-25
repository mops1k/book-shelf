package io.github.bookshelf.feature.bookdetails

import io.github.bookshelf.core.data.model.ReadingStatus

/**
 * Состояние карточки книги (docs/eink-ux.md §6.4).
 *
 * Все поля — примитивы и подписи: экран не должен зависеть от формы доменной `Book`,
 * поэтому метаданные приходят сюда уже приведёнными к строкам (размер, дата, серия).
 * После Sprint 4 заполняется из `BookRepository.observe(bookId)`.
 */
data class BookDetailsUiState(
    /** `books.id` открытой книги. */
    val bookId: String = "",
    /** Название; при `parseState = FAILED` — заголовок из имени файла. */
    val title: String = "",
    /** Авторы одной строкой. */
    val authors: String = "",
    /** Серия и номер: «Плоский мир #5». */
    val series: String? = null,
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
    /** `content://`-URI документа — по нему книга открывается в читалке. */
    val documentUri: String = "",
    /** Путь внутри источника: «Books/Лем/Солярис.fb2». */
    val relativePath: String = "",
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
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)
