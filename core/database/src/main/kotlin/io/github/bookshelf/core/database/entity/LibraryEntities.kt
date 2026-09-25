package io.github.bookshelf.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.bookshelf.core.database.model.CollectionType
import io.github.bookshelf.core.database.model.ScanRunStatus
import io.github.bookshelf.core.database.model.SourceKind

/**
 * Авторы и связи «книга — автор», коллекции, теги, источники, прогоны сканирования
 * и лёгкая таблица LRU для кэша обложек.
 *
 * Состав повторяет `docs/architecture.md` §3.3.
 */

/** Автор. `bookCount` — денормализованный счётчик для экрана автора и фасетов. */
@Entity(tableName = "authors", indices = [Index(value = ["name"]), Index(value = ["sortName"])])
data class AuthorEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Имя для сортировки: без ведущих «А/An/The». */
    val sortName: String,
    val bookCount: Int = 0,
)

/**
 * Связь многие-ко-многим «книга — автор».
 *
 * `position` сохраняет порядок авторов так, как они перечислены в файле.
 */
@Entity(
    tableName = "book_authors",
    primaryKeys = ["bookId", "authorId"],
    indices = [Index(value = ["authorId"])],
)
data class BookAuthorCrossRef(
    val bookId: String,
    val authorId: String,
    val position: Int = 0,
)

/** Коллекция (полка). */
@Entity(tableName = "collections", indices = [Index(value = ["type"])])
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: CollectionType = CollectionType.MANUAL,
    /** Ключ иконки в дизайн-системе; `null` — иконка по умолчанию. */
    val iconKey: String? = null,
    /** Порядок полок в списке. */
    val position: Int = 0,
    val createdAt: Long,
    /** JSON-правила умной полки; `null` для ручной (docs/architecture.md §3.3). */
    val rulesJson: String? = null,
)

/** Связь «коллекция — книга». */
@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    indices = [Index(value = ["bookId"])],
)
data class CollectionBookCrossRef(
    val collectionId: String,
    val bookId: String,
    val position: Int = 0,
    val addedAt: Long = 0L,
)

/** Тег (жанр/метка). */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

/** Связь «книга — тег». */
@Entity(
    tableName = "book_tags",
    primaryKeys = ["bookId", "tagId"],
    indices = [Index(value = ["tagId"])],
)
data class BookTagCrossRef(
    val bookId: String,
    val tagId: String,
)

/**
 * Источник библиотеки.
 *
 * `accessLost` — persistable-разрешение SAF потеряно: скан по источнику не падает, UI показывает
 * «Папка недоступна — выбрать заново», книги помечаются `isMissing` (docs/architecture.md §4.1).
 */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String,
    val kind: SourceKind,
    val enabled: Boolean = true,
    /** Обходить вложенные папки. */
    val recursive: Boolean = true,
    /** Glob-шаблоны исключений; хранятся одной строкой через `Converters`. */
    val excludeGlobs: List<String>? = null,
    val lastScanAt: Long? = null,
    val documentCount: Int = 0,
    val accessLost: Boolean = false,
)

/**
 * Прогон сканирования источника.
 *
 * `cursorDocumentId` — последний обработанный `documentId`: с него продолжается прерванный скан
 * (Android 15 ограничивает `dataSync` шестью часами, docs/architecture.md §4.5).
 */
@Entity(
    tableName = "scan_runs",
    indices = [Index(value = ["sourceId"]), Index(value = ["status"])],
)
data class ScanRunEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: ScanRunStatus = ScanRunStatus.RUNNING,
    val cursorDocumentId: String? = null,
    val filesSeen: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val failedCount: Int = 0,
    val error: String? = null,
)

/**
 * Лёгкая запись о файле обложки для LRU-очистки дискового кэша (docs/architecture.md §5).
 *
 * Хранится отдельно от `books`, чтобы eviction не трогал тяжёлую центральную таблицу.
 */
@Entity(tableName = "cover_cache")
data class CoverCacheEntryEntity(
    @PrimaryKey val bookId: String,
    val lastAccessedAt: Long,
    val sizeBytes: Long = 0L,
)
