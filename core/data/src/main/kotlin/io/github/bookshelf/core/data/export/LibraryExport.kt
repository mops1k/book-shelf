package io.github.bookshelf.core.data.export

import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Collection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Экспорт библиотеки в JSON: книги со всеми полями карточки, полки и прогресс чтения.
 *
 * Зачем: до этого в настройках стояла строка «Экспорт библиотеки пока не поддерживается» —
 * обещание без действия. Файл нужен, чтобы перенести библиотеку на другое устройство и чтобы
 * можно было глазами сверить, что приложение видит те же книги, полки и прогресс.
 *
 * Формат версионирован (`version`): при изменении состава полей версия растёт, и читатель файла
 * может отличить старый формат от нового, а не молча понять его неправильно.
 */
@Serializable
data class LibraryExportFile(
    val version: Int = CURRENT_EXPORT_VERSION,
    val exportedAt: Long,
    val books: List<ExportedBook>,
    val collections: List<ExportedCollection>,
)

/** Книга в экспорте: всё, что показывает карточка, плюс жанры и прогресс чтения. */
@Serializable
data class ExportedBook(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authorsText: String,
    val authors: List<String> = emptyList(),
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val genres: List<String> = emptyList(),
    val extension: String,
    val relativePath: String = "",
    val sizeBytes: Long,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val pageCount: Int? = null,
    val readingStatus: String,
    val favorite: Boolean = false,
    val rating: Int? = null,
    val isMissing: Boolean = false,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    /** Доля прочитанного 0..1 — как в базе; `null` означает «прогресса нет». */
    val progressPercent: Float? = null,
)

/** Полка в экспорте: название, тип, правила умной полки и число книг. */
@Serializable
data class ExportedCollection(
    val id: String,
    val name: String,
    val type: String,
    val bookCount: Int = 0,
    val rulesJson: String? = null,
)

/** Текущая версия формата файла. */
const val CURRENT_EXPORT_VERSION: Int = 1

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Собирает файл экспорта.
 *
 * Функция чистых данных: книги, полки и карта «книга → жанры» приходят готовыми, поэтому её можно
 * проверить тестом без базы, а доступ к данным живёт в отдельном порте.
 */
fun exportLibrary(
    exportedAt: Long,
    books: List<Book>,
    collections: List<Collection>,
    genres: Map<String, List<String>> = emptyMap(),
): String = exportJson.encodeToString(
    LibraryExportFile(
        exportedAt = exportedAt,
        books = books.map { bookshelf -> bookshelf.toExported(genres[bookshelf.id].orEmpty()) },
        collections = collections.map { collection -> collection.toExported() },
    ),
)

private fun Book.toExported(genres: List<String>) = ExportedBook(
    id = id,
    title = title,
    subtitle = subtitle,
    authorsText = authorsText,
    authors = authors,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    genres = genres,
    extension = extension,
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    language = language,
    publishedYear = publishedYear,
    publisher = publisher,
    pageCount = pageCount,
    readingStatus = readingStatus.name,
    favorite = favorite,
    rating = rating,
    isMissing = isMissing,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    progressPercent = progressPercent,
)

private fun Collection.toExported() = ExportedCollection(
    id = id,
    name = name,
    type = type.name,
    bookCount = bookCount,
    rulesJson = rulesJson,
)
