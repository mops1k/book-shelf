package io.github.bookshelf.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Файл-копия книги, созданная не нами («Архитектура» §3.1).
 *
 * Зачем таблица: читалка (и не только она) кладёт байт-в-байт копию книги внутрь нашей библиотеки —
 * `Books/<дата>/<название>.fb2.zip` (FBReader, 30.09–01.10.2026). Копия — тот же файл, поэтому
 * книгой она не считается: строки в `books` у неё нет, а связь с оригиналом хранится здесь.
 * Без этого в полке появлялась вторая книга (92 вместо 91), а прогресс чтения уходил на копию.
 *
 * Копия хранится отдельной таблицей, а не столбцом в `books`: тогда запросы полки, фасетов и поиска
 * не меняются вовсе — копии в них просто не попадают, потому что их нет среди книг.
 *
 * Каскад по `bookId` обязателен: связь с удалённой книгой — мусор. Файл копии удаляется отдельно,
 * потому что база про файлы ничего не решает.
 *
 * @param copyDocumentUri адрес документа копии в дереве SAF — он же первичный ключ.
 * @param bookId книга, чьё содержимое повторяет копия.
 * @param sourceId источник копии: по нему скан подчищает связи, которых больше нет.
 * @param sizeBytes размер копии на момент распознавания.
 * @param lastModified время изменения файла на момент распознавания.
 * @param contentHash отпечаток содержимого (SHA-256): по нему копия опознаётся, если файл переехал.
 */
@Entity(
    tableName = "book_copies",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["sourceId"])],
)
data class BookCopyEntity(
    @PrimaryKey val copyDocumentUri: String,
    val bookId: String,
    val sourceId: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val contentHash: String? = null,
)
