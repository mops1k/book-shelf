package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Поведение внешних ключей в боевом пути («Архитектура» §3.1).
 *
 * Отдельный файл от `BookshelfDatabaseMigrationTest` не из-за логики, а из-за окружения: там база
 * открывается через `MigrationTestHelper` как «сырая» `SupportSQLiteDatabase`, где
 * `PRAGMA foreign_keys = 0`, и каскад не срабатывает. Здесь открывается настоящая база Room, у
 * которой внешние ключи включены, — то есть ровно то, что получит пользователь.
 *
 * Что проверяется: ручная правка не должна пережить удаление книги. Иначе после удаления и
 * восстановления того же `documentUri` пользователь получил бы правки к прежнему содержимому.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MetadataOverrideCascadeTest {

    private lateinit var database: BookshelfDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun override_is_deleted_together_with_its_book() = runBlocking {
        database.sourceDao().insert(
            SourceEntity(
                id = "source-1",
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().insert(BookDaoTestFixtures.book(id = "book-1", sourceId = "source-1"))
        val overrides = database.metadataOverrideDao()
        overrides.upsert(MetadataOverrideEntity(bookId = "book-1", title = "Моя редакция", updatedAt = 1000L))
        assertThat(overrides.getByBookId("book-1")).isNotNull()

        database.bookDao().deleteById("book-1")

        assertThat(overrides.getByBookId("book-1")).isNull()
    }

    @Test
    fun override_survives_a_plain_metadata_edit() = runBlocking {
        database.sourceDao().insert(
            SourceEntity(
                id = "source-1",
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().insert(BookDaoTestFixtures.book(id = "book-1", sourceId = "source-1"))
        val overrides = database.metadataOverrideDao()
        overrides.upsert(MetadataOverrideEntity(bookId = "book-1", title = "Моя редакция", updatedAt = 1000L))

        database.bookDao().update(
            BookDaoTestFixtures.book(id = "book-1", sourceId = "source-1", title = "Новое имя файла"),
        )

        assertThat(overrides.getByBookId("book-1")?.title).isEqualTo("Моя редакция")
    }
}
