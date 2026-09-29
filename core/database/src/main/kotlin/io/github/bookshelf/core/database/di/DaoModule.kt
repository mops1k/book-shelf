package io.github.bookshelf.core.database.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.dao.AuthorDao
import io.github.bookshelf.core.database.dao.BookCleanupDao
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.BookReaderOverrideDao
import io.github.bookshelf.core.database.dao.BookmarkDao
import io.github.bookshelf.core.database.dao.CollectionDao
import io.github.bookshelf.core.database.dao.CoverCacheEntryDao
import io.github.bookshelf.core.database.dao.FacetDao
import io.github.bookshelf.core.database.dao.FormatDefaultDao
import io.github.bookshelf.core.database.dao.MetadataOverrideDao
import io.github.bookshelf.core.database.dao.OpdsCatalogDao
import io.github.bookshelf.core.database.dao.OpdsDownloadDao
import io.github.bookshelf.core.database.dao.OpdsFeedCacheDao
import io.github.bookshelf.core.database.dao.ReaderProfileDao
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.database.dao.ScanRunDao
import io.github.bookshelf.core.database.dao.SearchDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.dao.TagDao

/**
 * DAO-провайдеры: единственный экземпляр базы отдаётся всем, кто её читает.
 *
 * Отдельный модуль от [DatabaseModule] не ради красоты: у объекта `@Provides` есть предел по числу
 * функций (detekt `TooManyFunctions`), и держать сборку базы вместе с двумя десятками провайдеров
 * значило бы упереться в него при каждом новом DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideBookDao(database: BookshelfDatabase): BookDao = database.bookDao()

    @Provides
    fun provideBookCleanupDao(database: BookshelfDatabase): BookCleanupDao = database.bookCleanupDao()

    @Provides
    fun provideMetadataOverrideDao(database: BookshelfDatabase): MetadataOverrideDao =
        database.metadataOverrideDao()

    @Provides
    fun provideSourceDao(database: BookshelfDatabase): SourceDao = database.sourceDao()

    @Provides
    fun provideScanRunDao(database: BookshelfDatabase): ScanRunDao = database.scanRunDao()

    @Provides
    fun provideFacetDao(database: BookshelfDatabase): FacetDao = database.facetDao()

    @Provides
    fun provideSearchDao(database: BookshelfDatabase): SearchDao = database.searchDao()

    @Provides
    fun provideAuthorDao(database: BookshelfDatabase): AuthorDao = database.authorDao()

    @Provides
    fun provideCollectionDao(database: BookshelfDatabase): CollectionDao = database.collectionDao()

    @Provides
    fun provideTagDao(database: BookshelfDatabase): TagDao = database.tagDao()

    @Provides
    fun provideReaderProfileDao(database: BookshelfDatabase): ReaderProfileDao = database.readerProfileDao()

    @Provides
    fun provideFormatDefaultDao(database: BookshelfDatabase): FormatDefaultDao = database.formatDefaultDao()

    @Provides
    fun provideBookReaderOverrideDao(database: BookshelfDatabase): BookReaderOverrideDao =
        database.bookReaderOverrideDao()

    @Provides
    fun provideReadingProgressDao(database: BookshelfDatabase): ReadingProgressDao = database.readingProgressDao()

    @Provides
    fun provideBookmarkDao(database: BookshelfDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideOpdsCatalogDao(database: BookshelfDatabase): OpdsCatalogDao = database.opdsCatalogDao()

    @Provides
    fun provideOpdsDownloadDao(database: BookshelfDatabase): OpdsDownloadDao = database.opdsDownloadDao()

    @Provides
    fun provideOpdsFeedCacheDao(database: BookshelfDatabase): OpdsFeedCacheDao = database.opdsFeedCacheDao()

    @Provides
    fun provideCoverCacheEntryDao(database: BookshelfDatabase): CoverCacheEntryDao = database.coverCacheEntryDao()
}
