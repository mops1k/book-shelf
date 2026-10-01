package io.github.bookshelf.core.database.model

/**
 * Копия книги вместе с названием книги, которую она повторяет.
 *
 * Заголовок нужен экрану «Копии книг на устройстве»: человек должен видеть, копия какой книги лежит
 * на диске, а не безликий адрес документа.
 */
data class BookCopyWithTitle(
    val copyDocumentUri: String,
    val bookId: String,
    val title: String,
    val authorsText: String,
    val sizeBytes: Long,
)
