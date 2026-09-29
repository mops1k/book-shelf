package io.github.bookshelf.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.model.CollectionType
import io.github.bookshelf.core.database.model.ReadingStatus
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AdvancedSettings
import io.github.bookshelf.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Данные книги, когда её файл заменили (`BookFileChangeHandler` + [BookUserDataCleaner]).
 *
 * Случай узкий, но дорогой: если файл по тому же пути заменили, при выключенном сохранении книга
 * считается новой и всё, что человек про неё сказал, должно уйти — оценка, избранное, статус
 * чтения, прогресс, полки, ручные правки названия. Забыть часть опаснее всего: останется
 * «полустёртая» книга, где одно относится к прежнему файлу, а другое к новому.
 *
 * Обратная сторона не менее важна: при включённом сохранении (умолчание) не должно пропасть
 * ничего — иначе настройка была бы ловушкой.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookFileChangeHandlerTest {

    private lateinit var database: BookshelfDatabase
    private val settings = FakeSettingsRepository()

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
    fun `with saving enabled nothing is lost`() = runBlocking {
        givenBookWithUserData()
        settings.keep = true

        handler().onFileChanged(BOOK_ID)

        val book = database.bookDao().getById(BOOK_ID)!!
        assertThat(book.rating).isEqualTo(4)
        assertThat(book.favorite).isTrue()
        assertThat(book.readingStatus).isEqualTo(ReadingStatus.READING)
        assertThat(database.readingProgressDao().getByBook(BOOK_ID)).isNotNull()
        assertThat(database.collectionDao().getBooks(COLLECTION_ID)).hasSize(1)
        assertThat(database.metadataOverrideDao().getByBookId(BOOK_ID)).isNotNull()
    }

    @Test
    fun `with saving disabled everything the reader said is cleared`() = runBlocking {
        givenBookWithUserData()
        settings.keep = false

        val result = handler().onFileChanged(BOOK_ID)

        assertThat(result.getOrNull()).isNotNull()
        val book = database.bookDao().getById(BOOK_ID)!!
        assertThat(book.rating).isNull()
        assertThat(book.favorite).isFalse()
        assertThat(book.readingStatus).isEqualTo(ReadingStatus.UNREAD)
        assertThat(database.readingProgressDao().getByBook(BOOK_ID)).isNull()
        assertThat(database.collectionDao().getBooks(COLLECTION_ID)).isEmpty()
        assertThat(database.metadataOverrideDao().getByBookId(BOOK_ID)).isNull()
        // Для человека это новая книга: дата добавления тоже новая.
        assertThat(book.addedAt).isEqualTo(NOW)
    }

    @Test
    fun `clearing one book does not touch another`() = runBlocking {
        givenBookWithUserData()
        database.bookDao().insert(otherBook())
        settings.keep = false

        handler().onFileChanged(BOOK_ID)

        val other = database.bookDao().getById(OTHER_ID)!!
        assertThat(other.rating).isEqualTo(5)
        assertThat(other.readingStatus).isEqualTo(ReadingStatus.READ)
    }

    @Test
    fun `a failure in the middle rolls the whole reset back`() = runBlocking {
        givenBookWithUserData()
        // Падение на третьем шаге (удаление прогресса): без транзакции оценка и статус уже
        // изменились бы, и книга осталась бы полустёртой — статус новый, данные прежние.
        val failing = FailingReadingProgressDao(database.readingProgressDao())

        val result = cleaner(progressDao = failing).clear(BOOK_ID)

        assertThat(result.getOrNull()).isNull()
        val book = database.bookDao().getById(BOOK_ID)!!
        assertThat(book.rating).isEqualTo(4)
        assertThat(book.readingStatus).isEqualTo(ReadingStatus.READING)
        assertThat(book.favorite).isTrue()
        assertThat(database.readingProgressDao().getByBook(BOOK_ID)).isNotNull()
    }

    private fun cleaner(
        progressDao: io.github.bookshelf.core.database.dao.ReadingProgressDao =
            database.readingProgressDao(),
    ) = BookUserDataCleaner(
        database = database,
        cleanupDao = database.bookCleanupDao(),
        overrideDao = database.metadataOverrideDao(),
        readingProgressDao = progressDao,
        collectionDao = database.collectionDao(),
        clock = object : AppClock {
            override fun nowMillis(): Long = NOW
        },
    )

    /** Прогресс читается как обычно, а удаление падает: проверяется откат всей очистки. */
    private class FailingReadingProgressDao(
        private val delegate: io.github.bookshelf.core.database.dao.ReadingProgressDao,
    ) : io.github.bookshelf.core.database.dao.ReadingProgressDao {

        override suspend fun upsert(progress: ReadingProgressEntity) = delegate.upsert(progress)

        override fun observeByBook(bookId: String) = delegate.observeByBook(bookId)

        override suspend fun getByBook(bookId: String) = delegate.getByBook(bookId)

        override suspend fun deleteByBook(bookId: String): Unit = error("База занята")
    }

    private fun handler() = BookFileChangeHandler(
        settings = settings,
        cleaner = BookUserDataCleaner(
            database = database,
            cleanupDao = database.bookCleanupDao(),
            overrideDao = database.metadataOverrideDao(),
            readingProgressDao = database.readingProgressDao(),
            collectionDao = database.collectionDao(),
            clock = object : AppClock {
                override fun nowMillis(): Long = NOW
            },
        ),
    )

    private suspend fun givenBookWithUserData() {
        database.bookDao().insert(
            BookEntity(
                id = BOOK_ID,
                sourceId = "source-1",
                documentUri = "content://documents/book.fb2",
                relativePath = "Автор/Книга.fb2",
                fileName = "Книга.fb2",
                extension = "fb2",
                mimeType = "application/fb2",
                sizeBytes = 1_000L,
                lastModified = 1_000L,
                title = "Книга",
                sortTitle = "Книга",
                authorsText = "Автор",
                addedAt = 1L,
                updatedAt = 1L,
                lastSeenAt = 1L,
                readingStatus = ReadingStatus.READING,
                favorite = true,
                rating = 4,
            ),
        )
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = BOOK_ID,
                percent = 0.25f,
                source = "FBREADER_AIDL",
                updatedAt = 1L,
            ),
        )
        database.collectionDao().upsertAll(
            listOf(
                CollectionEntity(
                    id = COLLECTION_ID,
                    name = "Избранное",
                    type = CollectionType.MANUAL,
                    createdAt = 1L,
                ),
            ),
        )
        database.collectionDao().insertBooks(
            listOf(CollectionBookCrossRef(collectionId = COLLECTION_ID, bookId = BOOK_ID)),
        )
        database.metadataOverrideDao().upsert(
            MetadataOverrideEntity(bookId = BOOK_ID, title = "Моё название", updatedAt = 1L),
        )
    }

    private fun otherBook() = BookEntity(
        id = OTHER_ID,
        sourceId = "source-1",
        documentUri = "content://documents/other.fb2",
        relativePath = "Автор/Другая.fb2",
        fileName = "Другая.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 2_000L,
        lastModified = 2_000L,
        title = "Другая",
        sortTitle = "Другая",
        authorsText = "Автор",
        addedAt = 1L,
        updatedAt = 1L,
        lastSeenAt = 1L,
        rating = 5,
        readingStatus = ReadingStatus.READ,
    )

    private class FakeSettingsRepository : SettingsRepository {

        var keep: Boolean = true

        override val settings: Flow<AppSettings> = flowOf(AppSettings())

        override suspend fun current(): AppSettings = AppSettings(
            advanced = AdvancedSettings(keepMetadataOnFileChange = keep),
        )

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

        override suspend fun resetToDefaults() = Unit
    }

    private companion object {
        const val BOOK_ID = "book-1"
        const val OTHER_ID = "book-2"
        const val COLLECTION_ID = "collection-1"
        const val NOW = 5_000L
    }
}
