package io.github.bookshelf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.bookshelf.core.database.model.MetadataSource
import io.github.bookshelf.core.database.model.ParseState
import io.github.bookshelf.core.database.model.ReadingStatus

/**
 * Центральная таблица библиотеки — книга.
 *
 * Состав полей повторяет `docs/architecture.md` §3.1 один в один: это денормализованная карточка
 * для быстрой отрисовки полки без JOIN'ов. `id` — UUID и не зависит от пути, поэтому запись
 * переживает перемещение файла и восстановление источника (`isMissing`).
 *
 * `documentUri` уникален: инкрементальный скан ищет книгу именно по нему (§4.3).
 */
// Длинный конструктор — прямое отражение схемы из docs/architecture.md §3.1; дробить её нельзя.
@Suppress("LongParameterList")
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["documentUri"], unique = true),
        Index(value = ["sourceId", "relativePath"]),
        Index(value = ["title"]),
        Index(value = ["sortTitle"]),
        Index(value = ["authorsText"]),
        Index(value = ["seriesName"]),
        Index(value = ["addedAt"]),
        Index(value = ["lastOpenedAt"]),
        Index(value = ["isMissing"]),
        Index(value = ["parseState"]),
    ],
)
data class BookEntity(
    /** PK (UUID), не зависит от пути к файлу. */
    @PrimaryKey val id: String,
    /** FK → `sources.id`. */
    val sourceId: String,
    /** `content://` или `file://`; уникален (индекс выше). */
    val documentUri: String,
    /** Путь внутри источника: нужен для группировки по папкам. */
    val relativePath: String,
    val fileName: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    /** `SHA-256(size + mtime + первые 64 КБ)` — быстрый ключ изменений (§3.1). */
    val quickHash: String? = null,
    /** Полный хеш; считается лениво, только для поиска дубликатов. */
    val contentHash: String? = null,
    val title: String,
    /** Заголовок для сортировки: без ведущих «А/An/The». */
    val sortTitle: String,
    /** Авторы строкой через запятую — денормализация для полки. */
    val authorsText: String,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val description: String? = null,
    val pageCount: Int? = null,
    /** Путь к обложке в дисковом кэше `filesDir/covers`. */
    val coverPath: String? = null,
    val coverUpdatedAt: Long? = null,
    /** Откуда взяты метаданные; `MANUAL` парсер не перезаписывает. */
    val metadataSource: MetadataSource = MetadataSource.NONE,
    val parseState: ParseState = ParseState.PENDING,
    /** Диагностика разбора; показывается в карточке по «подробнее». */
    val parseError: String? = null,
    val addedAt: Long,
    val updatedAt: Long,
    /** Когда файл последний раз видели при сканировании (§4.3). */
    val lastSeenAt: Long,
    val lastOpenedAt: Long? = null,
    val openCount: Int = 0,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val favorite: Boolean = false,
    /** 0..5; `null` — без оценки. */
    val rating: Int? = null,
    /** Файл исчез, но запись сохранена (§4.3). */
    val isMissing: Boolean = false,
    val missingSince: Long? = null,
)

/**
 * FTS4-таблица полнотекстового поиска по книгам (docs/architecture.md §3.2).
 *
 * Внешнее содержимое: `contentEntity = BookEntity::class`, поэтому строки синхронизируются
 * триггерами Room, а сама таблица не дублирует данные. Связь с `books` идёт по `rowid`
 * (у `BookEntity` строковый PK, собственного `rowid`-столбца нет), поэтому JOIN в `SearchDao`
 * выглядит как `books.rowid = books_fts.rowid`.
 *
 * Токенизатор `unicode61` корректно работает с кириллицей, `remove_diacritics=2` — с латиницей.
 * Форма записи — `tokenizer` + `tokenizerArgs`: так Room генерирует FTS4-совместимое
 * `tokenize=unicode61 "remove_diacritics=2"` (проверено по исходникам `androidx.room.Fts4` 2.6.1,
 * где `tokenizerArgs` существует начиная с этой версии).
 *
 * STUB(Sprint 4): проверить поведение на API 24 — системный SQLite там старше 3.27,
 * `remove_diacritics=2` может не поддерживаться (неизвестные аргументы FTS4 игнорирует,
 * то есть деградация безопасная, но проверить тестом с диакритикой нужно).
 */
@Fts4(
    contentEntity = BookEntity::class,
    tokenizer = "unicode61",
    tokenizerArgs = ["remove_diacritics=2"],
)
@Entity(tableName = "books_fts")
data class BookFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    val title: String,
    val authorsText: String,
    val seriesName: String?,
    val fileName: String,
    val description: String?,
)
