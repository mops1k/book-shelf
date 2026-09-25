package io.github.bookshelf.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.bookshelf.core.database.model.BookmarkKind

/**
 * Профили внешних читалок, прогресс чтения и закладки (docs/architecture.md §3.3,
 * `docs/reader-integration.md`).
 *
 * Профили хранятся в БД, но их JSON-представление (presets) живёт в `:core:datastore`;
 * модуль БД про формат JSON ничего не знает и хранит его строкой.
 */

/** Профиль внешней читалки: как открывать файл и что она умеет. */
@Entity(tableName = "reader_profiles", indices = [Index(value = ["packageName"])])
data class ReaderProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    /** Явная Activity, если читалка не имеет корректного `MAIN`/`VIEW`-фильтра. */
    val activityName: String? = null,
    /** Поддерживаемые MIME-типы строкой (список через разделитель). */
    val mimeTypes: String,
    /** Читалка принимает `content://` напрямую, без копии файла. */
    val supportsContentUri: Boolean = true,
    /** Extras для `Intent` в виде JSON; `null` — без extras. */
    val extrasJson: String? = null,
    /** Встроенный preset, который нельзя удалить. */
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true,
    /** Больше — выше в списке выбора. */
    val priority: Int = 0,
)

/** Читалка по умолчанию для формата (расширения). */
@Entity(tableName = "format_defaults")
data class FormatDefaultEntity(
    /** Формат (расширение) — PK. */
    @PrimaryKey val format: String,
    val profileId: String,
)

/** Персональный выбор читалки для конкретной книги; приоритетнее дефолта формата. */
@Entity(tableName = "book_reader_override")
data class BookReaderOverrideEntity(
    @PrimaryKey val bookId: String,
    val profileId: String,
)

/**
 * Прогресс чтения книги.
 *
 * Одна актуальная запись на книгу; `profileId` фиксирует, какая читалка отдала прогресс,
 * `source` — канал получения (например, AIDL FBReader или ручная отметка).
 */
@Entity(tableName = "reading_progress", indices = [Index(value = ["profileId"])])
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val profileId: String? = null,
    /** Доля прочитанного, 0.0..1.0. */
    val percent: Float = 0f,
    /** Позиция в формате читалки (JSON); `null` — неизвестна. */
    val locatorJson: String? = null,
    val updatedAt: Long,
    /** Канал получения прогресса; например `MANUAL` или `FBREADER_AIDL`. */
    val source: String = "MANUAL",
)

/** Закладка, заметка или выделение. */
@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId"])])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val kind: BookmarkKind,
    /** Позиция/выделение в формате читалки (JSON). */
    val payloadJson: String,
    val note: String? = null,
    val createdAt: Long,
)
