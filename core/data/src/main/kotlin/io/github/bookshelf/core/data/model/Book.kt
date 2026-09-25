package io.github.bookshelf.core.data.model

/**
 * Доменная модель книги для UI (docs/architecture.md §3.1).
 *
 * Это **не** Room-entity: feature-модули видят только `:core:data` и не знают про БД. Отсюда
 * следствия:
 * * `authors: List<String>` уже развёрнуты из связи `book_authors` (в entity лежит только
 *   денормализованный `authorsText`);
 * * `progressPercent` — из таблицы `reading_progress`, а не из `books`;
 * * enum'ы — свои ([ReadingStatus]), а не entity-перечисления (см. [MetadataOrigin], [ParseState]).
 *
 * @param authorsText денормализованная строка авторов «Фамилия И. О., …» для быстрой отрисовки;
 * @param coverPath путь к файлу в кэше обложек (docs/architecture.md §5) или `null`;
 * @param extension расширение без точки в нижнем регистре;
 * @param isMissing файл исчез, но запись сохранена (история чтения и коллекции не теряются);
 * @param progressPercent прогресс чтения 0..100, если читалка его отдаёт.
 */
data class Book(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authorsText: String,
    val authors: List<String> = emptyList(),
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val coverPath: String? = null,
    val extension: String,
    val sizeBytes: Long,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val description: String? = null,
    val pageCount: Int? = null,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val favorite: Boolean = false,
    val rating: Int? = null,
    val isMissing: Boolean = false,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val progressPercent: Float? = null,
)
