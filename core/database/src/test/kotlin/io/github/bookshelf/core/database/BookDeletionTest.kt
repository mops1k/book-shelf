package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.entity.BookReaderOverrideEntity
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.BookmarkEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.database.entity.CoverCacheEntryEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.entity.TagEntity
import io.github.bookshelf.core.database.model.BookmarkKind
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Удаление книги вместе со связями.
 *
 * Решение Александра (28.09.2026): физически удалённая с устройства книга исчезает с полки, а не
 * остаётся «пропавшей». Проверяется то, что легко сделать наполовину: `ON DELETE CASCADE` в схеме
 * объявлен только у `metadata_overrides`, а у полок, тегов, прогресса, закладок и кэша обложек
 * внешних ключей нет вовсе — простое `DELETE FROM books` оставило бы висячие строки, и полка
 * показывала бы несуществующие книги.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookDeletionTest {

    private lateinit var database: BookshelfDatabase

    @Before
    fun setUp() {
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleting a book removes its links`() = runBlocking {
        givenBookWithLinks()
        assertThat(database.searchDao().search(SEARCH_QUERY, LIMIT).first()).hasSize(1)

        database.bookCleanupDao().deleteWithLinks(listOf(BOOK_ID))

        assertThat(database.bookDao().getById(BOOK_ID)).isNull()
        assertThat(database.tagDao().getBookTags(BOOK_ID)).isEmpty()
        assertThat(database.collectionDao().getBooks(SHELF_ID)).isEmpty()
        assertThat(database.readingProgressDao().getByBook(BOOK_ID)).isNull()
        assertThat(database.bookReaderOverrideDao().getByBook(BOOK_ID)).isNull()
        assertThat(database.bookmarkDao().countByBook(BOOK_ID)).isEqualTo(0)
        assertThat(database.coverCacheEntryDao().getByBookId(BOOK_ID)).isNull()
        // Удалённая книга не должна находиться поиском: библиотека после синхронизации не
        // показывает и не ищет то, чего нет на диске.
        assertThat(database.searchDao().search(SEARCH_QUERY, LIMIT).first()).isEmpty()
    }

    @Test
    fun `deleting an empty list changes nothing`() = runBlocking {
        givenBookWithLinks()

        database.bookCleanupDao().deleteWithLinks(emptyList())

        // Пустой `IN ()` в SQLite — синтаксическая ошибка, поэтому вызов обязан выходить раньше.
        assertThat(database.bookDao().getById(BOOK_ID)).isNotNull()
    }

    private fun openDatabase(): BookshelfDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun givenBookWithLinks() {
        database.sourceDao().insert(SOURCE)
        database.bookDao().upsertAll(listOf(book()))
        database.tagDao().upsertAll(listOf(TagEntity(id = TAG_ID, name = "sf")))
        database.tagDao().insertBookTags(listOf(BookTagCrossRef(bookId = BOOK_ID, tagId = TAG_ID)))
        database.collectionDao().upsertAll(
            listOf(CollectionEntity(id = SHELF_ID, name = "Полка", createdAt = UPDATED_AT)),
        )
        database.collectionDao().insertBooks(
            listOf(CollectionBookCrossRef(collectionId = SHELF_ID, bookId = BOOK_ID)),
        )
        database.readingProgressDao().upsert(
            ReadingProgressEntity(bookId = BOOK_ID, updatedAt = UPDATED_AT),
        )
        database.bookReaderOverrideDao().upsert(
            BookReaderOverrideEntity(bookId = BOOK_ID, profileId = "fbreader"),
        )
        database.bookmarkDao().upsert(
            BookmarkEntity(
                id = BOOKMARK_ID,
                bookId = BOOK_ID,
                kind = BookmarkKind.POSITION,
                payloadJson = "{}",
                createdAt = UPDATED_AT,
            ),
        )
        database.coverCacheEntryDao().upsert(
            CoverCacheEntryEntity(bookId = BOOK_ID, lastAccessedAt = UPDATED_AT),
        )
    }

    private fun book() = BookDaoTestFixtures.book(
        id = BOOK_ID,
        sourceId = SOURCE_ID,
        title = "Солярис",
        author = "Станислав Лем",
    )

    private companion object {
        const val BOOK_ID = "doc:solaris"
        const val SOURCE_ID = "source-1"
        const val SHELF_ID = "shelf-1"
        const val TAG_ID = "tag:sf"
        const val BOOKMARK_ID = "bookmark-1"
        const val SEARCH_QUERY = "солярис*"
        const val LIMIT = 10
        const val UPDATED_AT = 1_790_000_000_000L

        val SOURCE = SourceEntity(
            id = SOURCE_ID,
            uri = "content://com.android.externalstorage.documents/tree/books",
            displayName = "Books",
            kind = SourceKind.SAF_TREE,
        )
    }
}
