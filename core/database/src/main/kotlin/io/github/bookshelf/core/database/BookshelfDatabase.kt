package io.github.bookshelf.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.bookshelf.core.database.convert.Converters
import io.github.bookshelf.core.database.dao.AuthorDao
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.BookReaderOverrideDao
import io.github.bookshelf.core.database.dao.BookmarkDao
import io.github.bookshelf.core.database.dao.CollectionDao
import io.github.bookshelf.core.database.dao.CoverCacheEntryDao
import io.github.bookshelf.core.database.dao.FacetDao
import io.github.bookshelf.core.database.dao.FormatDefaultDao
import io.github.bookshelf.core.database.dao.OpdsCatalogDao
import io.github.bookshelf.core.database.dao.OpdsDownloadDao
import io.github.bookshelf.core.database.dao.OpdsFeedCacheDao
import io.github.bookshelf.core.database.dao.ReaderProfileDao
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.database.dao.ScanRunDao
import io.github.bookshelf.core.database.dao.SearchDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.dao.MetadataOverrideDao
import io.github.bookshelf.core.database.dao.TagDao
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.BookFtsEntity
import io.github.bookshelf.core.database.entity.BookReaderOverrideEntity
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.BookmarkEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.database.entity.CoverCacheEntryEntity
import io.github.bookshelf.core.database.entity.FormatDefaultEntity
import io.github.bookshelf.core.database.entity.OpdsCatalogEntity
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import io.github.bookshelf.core.database.entity.OpdsFeedCacheEntity
import io.github.bookshelf.core.database.entity.ReaderProfileEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.entity.ScanRunEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.entity.TagEntity

/**
 * База данных приложения: все таблицы, DAO и конвертеры (docs/architecture.md §3).
 *
 * Модуль ничего не знает ни про парсеры, ни про SAF: связывает их репозиторий в `:core:data`.
 *
 * * `exportSchema = true` — JSON-схема версии уезжает в `core/database/schemas`, из неё
 *   `MigrationTestHelper` проверяет миграции в CI (docs/architecture.md §3.4).
 * * `version = 1` — стартовая схема; каждое изменение — миграция в `migration/Migrations.kt`.
 * * деструктивный fallback в release запрещён: `fallbackToDestructiveMigration()` не вызывается
 *   даже как «временное решение».
 */
@Database(
    entities = [
        BookEntity::class,
        BookFtsEntity::class,
        AuthorEntity::class,
        BookAuthorCrossRef::class,
        CollectionEntity::class,
        CollectionBookCrossRef::class,
        TagEntity::class,
        BookTagCrossRef::class,
        SourceEntity::class,
        ScanRunEntity::class,
        CoverCacheEntryEntity::class,
        ReaderProfileEntity::class,
        FormatDefaultEntity::class,
        BookReaderOverrideEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        OpdsCatalogEntity::class,
        OpdsDownloadEntity::class,
        OpdsFeedCacheEntity::class,
        MetadataOverrideEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BookshelfDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun sourceDao(): SourceDao

    abstract fun scanRunDao(): ScanRunDao

    abstract fun facetDao(): FacetDao

    abstract fun searchDao(): SearchDao

    abstract fun authorDao(): AuthorDao

    abstract fun collectionDao(): CollectionDao

    abstract fun tagDao(): TagDao

    abstract fun readerProfileDao(): ReaderProfileDao

    abstract fun formatDefaultDao(): FormatDefaultDao

    abstract fun bookReaderOverrideDao(): BookReaderOverrideDao

    abstract fun readingProgressDao(): ReadingProgressDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun metadataOverrideDao(): MetadataOverrideDao

    abstract fun opdsCatalogDao(): OpdsCatalogDao

    abstract fun opdsDownloadDao(): OpdsDownloadDao

    abstract fun opdsFeedCacheDao(): OpdsFeedCacheDao

    abstract fun coverCacheEntryDao(): CoverCacheEntryDao

    companion object {
        /** Имя файла БД во внутренней памяти приложения. */
        const val NAME: String = "bookshelf.db"
    }
}
