package io.github.bookshelf.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.dao.AuthorDao
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
import io.github.bookshelf.core.database.migration.ALL_MIGRATIONS
import javax.inject.Singleton

/**
 * DI-граф модуля БД: единственный экземпляр [BookshelfDatabase] на приложение
 * и все DAO из него.
 *
 * `fallbackToDestructiveMigration()` здесь сознательно **не** вызывается: пользовательская
 * библиотека не должна теряться при обновлении (docs/architecture.md §3.4). Вместо этого
 * подключается массив [ALL_MIGRATIONS].
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Открыть БД приложения.
     *
     * Контекст приложения, а не Activity: база живёт дольше любого экрана.
     */
    // addMigrations принимает vararg — без spread-оператора массив не передать.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideBookshelfDatabase(@ApplicationContext context: Context): BookshelfDatabase =
        Room.databaseBuilder(context, BookshelfDatabase::class.java, BookshelfDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideBookDao(database: BookshelfDatabase): BookDao = database.bookDao()

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
