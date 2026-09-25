package io.github.bookshelf.core.data.mapper

import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataOrigin
import io.github.bookshelf.core.data.model.ParseState
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.paging.PageRequest
import io.github.bookshelf.core.database.entity.BookEntity

/**
 * Маппинг entity ↔ доменная модель (docs/architecture.md §2, §3.1).
 *
 * Правила, которые нельзя нарушать:
 * * **никаких entity-типов в публичных API** `:core:data`: наружу уходят только
 *   [Book]/[ReadingStatus]/… (feature-модули не видят `:core:database`);
 * * перечисления конвертируются **по `name`**, а не по `ordinal`: добавление нового значения в
 *   середину enum-а в БД не должно сдвигать уже сохранённые записи;
 * * неизвестное значение не роняет приложение, а откатывается к безопасному дефолту
 *   (`UNREAD` / `PENDING` / `NONE`) — битая или старая запись лучше падения.
 *
 * Ожидаемый контракт `BookEntity` (docs/architecture.md §3.1): поля `id`, `title`, `authorsText`,
 * `seriesName`, `seriesIndex`, `coverPath`, `extension`, `sizeBytes`, `language`, `publishedYear`,
 * `publisher`, `description`, `pageCount`, `readingStatus`, `favorite`, `rating`, `isMissing`,
 * `addedAt`, `lastOpenedAt`, а также `metadataSource`/`parseState`. Если имена в `:core:database`
 * разойдутся — правится только этот файл, остальной `:core:data` не затрагивается.
 */

/**
 * Собрать доменную модель из entity.
 *
 * [authors] передаётся отдельно: в `books` лежит только `authorsText`, полный список — результат
 * соединения с `book_authors` (docs/architecture.md §3.3).
 */
fun BookEntity.toDomain(authors: List<String>): Book = bookToDomain(
    id = id,
    title = title,
    authorsText = authorsText,
    extension = extension,
    sizeBytes = sizeBytes,
    addedAt = addedAt,
    authors = authors,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    coverPath = coverPath,
    language = language,
    publishedYear = publishedYear,
    publisher = publisher,
    description = description,
    pageCount = pageCount,
    readingStatusName = readingStatus.name,
    favorite = favorite,
    rating = rating,
    isMissing = isMissing,
    lastOpenedAt = lastOpenedAt,
    // Прогресс чтения живёт в отдельной таблице reading_progress, а не в books.
    progressPercent = null,
)

/**
 * Чистое ядро маппинга «поля строки `books` → [Book]».
 *
 * Вынесено отдельно, чтобы его можно было проверить тестом без поднятия `:core:database`
 * (см. `BookMappersTest`), а entity-расширение осталось тонким адаптером.
 *
 * Обязательные поля [`Book`] идут без значений по умолчанию — их нельзя «забыть». Порог
 * `LongParameterList` не нарушен: detekt считает только параметры без значений по умолчанию.
 */
internal fun bookToDomain(
    id: String,
    title: String,
    authorsText: String,
    extension: String,
    sizeBytes: Long,
    addedAt: Long,
    subtitle: String? = null,
    authors: List<String> = emptyList(),
    seriesName: String? = null,
    seriesIndex: Double? = null,
    coverPath: String? = null,
    language: String? = null,
    publishedYear: Int? = null,
    publisher: String? = null,
    description: String? = null,
    pageCount: Int? = null,
    readingStatusName: String = ReadingStatus.UNREAD.name,
    favorite: Boolean = false,
    rating: Int? = null,
    isMissing: Boolean = false,
    lastOpenedAt: Long? = null,
    progressPercent: Float? = null,
): Book = Book(
    id = id,
    title = title,
    subtitle = subtitle,
    authorsText = authorsText,
    authors = authors,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    coverPath = coverPath,
    extension = extension,
    sizeBytes = sizeBytes,
    language = language,
    publishedYear = publishedYear,
    publisher = publisher,
    description = description,
    pageCount = pageCount,
    readingStatus = readingStatusFromName(readingStatusName),
    favorite = favorite,
    rating = rating,
    isMissing = isMissing,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    progressPercent = progressPercent,
)

/** Статус чтения из имени entity-значения; неизвестное → [ReadingStatus.UNREAD]. */
fun readingStatusFromName(name: String?): ReadingStatus =
    ReadingStatus.entries.firstOrNull { it.name == name } ?: ReadingStatus.UNREAD

/** Состояние разбора из имени entity-значения; неизвестное → [ParseState.PENDING] (переразберём). */
fun parseStateFromName(name: String?): ParseState =
    ParseState.entries.firstOrNull { it.name == name } ?: ParseState.PENDING

/** Источник метаданных из имени entity-значения; неизвестное → [MetadataOrigin.NONE]. */
fun metadataOriginFromName(name: String?): MetadataOrigin =
    MetadataOrigin.entries.firstOrNull { it.name == name } ?: MetadataOrigin.NONE

/** Имя entity-значения для статуса чтения (обратное преобразование). */
fun ReadingStatus.toEntityName(): String = name

/** Имя entity-значения для состояния разбора. */
fun ParseState.toEntityName(): String = name

/** Имя entity-значения для источника метаданных. */
fun MetadataOrigin.toEntityName(): String = name

/**
 * Пагинация запроса полки → запрос страницы для DAO.
 *
 * Фильтры и сортировка сюда не входят: их применяет DAO по тем же [LibraryQuery] (`BookQuery`,
 * `sort`, `descending`, `grouping`); здесь только арифметика `page * pageSize`.
 */
fun LibraryQuery.toPageRequest(): PageRequest = PageRequest(offset = page * pageSize, limit = pageSize)
