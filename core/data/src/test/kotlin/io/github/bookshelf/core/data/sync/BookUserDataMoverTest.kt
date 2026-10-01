package io.github.bookshelf.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import io.github.bookshelf.core.database.model.ReadingStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Перенос данных человека с копии файла на книгу, которую она повторяет.
 *
 * Случай с устройства (01.10.2026): читалка создала копию книги внутри библиотеки, копия попала на
 * полку второй книгой, и прогресс чтения лёг на неё. Когда копия перестаёт быть книгой, её данные
 * нельзя выбрасывать: процент чтения, статус «читаю», избранное, оценка и полки — это то, что человек
 * накопил. Иначе копия исчезала бы вместе с историей чтения.
 *
 * Проверяются границы: своё не перетирается (более свежий прогресс оригинала, его «прочитано»),
 * пустое заполняется, полки объединяются, а «перенос книги на саму себя» ничего не делает.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookUserDataMoverTest {

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
    fun `progress of the copy is moved to the original`() = runBlocking<Unit> {
        givenBook(id = ORIGINAL_ID)
        givenBook(id = COPY_ID)
        database.readingProgressDao().upsert(progress(COPY_ID, percent = 0.25f, updatedAt = 2_000L))

        mover().move(fromBookId = COPY_ID, toBookId = ORIGINAL_ID)

        val moved = database.readingProgressDao().getByBook(ORIGINAL_ID)
        assertThat(moved?.percent).isWithin(0.0001f).of(0.25f)
        assertThat(moved?.source).isEqualTo("FBREADER_AIDL")
    }

    @Test
    fun `the newer progress of the original is kept`() = runBlocking<Unit> {
        givenBook(id = ORIGINAL_ID)
        givenBook(id = COPY_ID)
        database.readingProgressDao().upsert(progress(ORIGINAL_ID, percent = 0.5f, updatedAt = 3_000L))
        // Копию читали раньше: её процент устарел, и перетирать им свежий было бы потерей.
        database.readingProgressDao().upsert(progress(COPY_ID, percent = 0.25f, updatedAt = 2_000L))

        mover().move(fromBookId = COPY_ID, toBookId = ORIGINAL_ID)

        assertThat(database.readingProgressDao().getByBook(ORIGINAL_ID)?.percent)
            .isWithin(0.0001f).of(0.5f)
    }

    @Test
    fun `reading status is adopted only by an unread book`() = runBlocking<Unit> {
        givenBook(id = ORIGINAL_ID)
        givenBook(id = COPY_ID, status = ReadingStatus.READ)
        givenBook(id = FINISHED_ID, status = ReadingStatus.READ)
        givenBook(id = COPY_TWO_ID, status = ReadingStatus.READING)
        mover().move(fromBookId = COPY_ID, toBookId = ORIGINAL_ID)
        mover().move(fromBookId = COPY_TWO_ID, toBookId = FINISHED_ID)

        assertThat(database.bookDao().getById(ORIGINAL_ID)?.readingStatus)
            .isEqualTo(ReadingStatus.READ)
        // «Прочитано» не сбрасывается на «читаю»: человек мог открыть законченную книгу снова.
        assertThat(database.bookDao().getById(FINISHED_ID)?.readingStatus)
            .isEqualTo(ReadingStatus.READ)
    }

    @Test
    fun `favourite rating and open date are adopted when the original has none`() =
        runBlocking<Unit> {
            givenBook(id = ORIGINAL_ID)
            givenBook(
                id = COPY_ID,
                favorite = true,
                rating = 4,
                lastOpenedAt = 5_000L,
                openCount = 3,
            )

            mover().move(fromBookId = COPY_ID, toBookId = ORIGINAL_ID)

            val original = database.bookDao().getById(ORIGINAL_ID)
            assertThat(original?.favorite).isTrue()
            assertThat(original?.rating).isEqualTo(4)
            assertThat(original?.lastOpenedAt).isEqualTo(5_000L)
            assertThat(original?.openCount).isEqualTo(3)
        }

    @Test
    fun `the original keeps its own favourite and the newest open date`() = runBlocking<Unit> {
        givenBook(id = ORIGINAL_ID, favorite = true, rating = 5, lastOpenedAt = 9_000L, openCount = 1)
        givenBook(id = COPY_ID, rating = 2, lastOpenedAt = 5_000L, openCount = 2)

        mover().move(fromBookId = COPY_ID, toBookId = ORIGINAL_ID)

        val original = database.bookDao().getById(ORIGINAL_ID)
        assertThat(original?.favorite).isTrue()
        assertThat(original?.rating).isEqualTo(5)
        assertThat(original?.lastOpenedAt).isEqualTo(9_000L)
        assertThat(original?.openCount).isEqualTo(3)
    }

    @Test
    fun `collections of the copy include the original afterwards`() = runBlocking<Unit> {
        givenBook(id = ORIGINAL_ID)
        givenBook(id = COPY_ID)
        database.collectionDao().upsertAll(
            listOf(CollectionEntity(id = COLLECTION_ID, name = "Читаю", createdAt = 1_000L)),
        )
        database.collectionDao().insertBooks(
            listOf(CollectionBookCrossRef(collectionId = COLLECTION_ID, bookId = COPY_ID)),
        )

        mover().move(fromBookId = COPY_ID, toBookId = ORIGINAL_ID)

        val ids = database.collectionDao().getBooks(COLLECTION_ID).map { ref -> ref.bookId }
        assertThat(ids).contains(ORIGINAL_ID)
        // Копия остаётся в полке до своего удаления: чистку связей делает вызывающий.
        assertThat(ids).contains(COPY_ID)
    }

    @Test
    fun `moving a book onto itself changes nothing`() = runBlocking<Unit> {
        givenBook(id = ORIGINAL_ID, openCount = 1)
        database.readingProgressDao().upsert(progress(ORIGINAL_ID, percent = 0.4f, updatedAt = 2_000L))

        mover().move(fromBookId = ORIGINAL_ID, toBookId = ORIGINAL_ID)

        assertThat(database.bookDao().getById(ORIGINAL_ID)?.openCount).isEqualTo(1)
        assertThat(database.readingProgressDao().getByBook(ORIGINAL_ID)?.percent)
            .isWithin(0.0001f).of(0.4f)
    }

    private fun mover() = BookUserDataMover(
        bookDao = database.bookDao(),
        progressDao = database.readingProgressDao(),
        collectionDao = database.collectionDao(),
    )

    private fun progress(bookId: String, percent: Float, updatedAt: Long) = ReadingProgressEntity(
        bookId = bookId,
        profileId = null,
        percent = percent,
        locatorJson = null,
        updatedAt = updatedAt,
        source = "FBREADER_AIDL",
    )

    private suspend fun givenBook(
        id: String,
        status: ReadingStatus = ReadingStatus.UNREAD,
        favorite: Boolean = false,
        rating: Int? = null,
        lastOpenedAt: Long? = null,
        openCount: Int = 0,
    ) {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().insert(
            BookEntity(
                id = id,
                sourceId = SOURCE_ID,
                documentUri = "content://doc/$id",
                relativePath = "Автор/$id.fb2",
                fileName = "$id.fb2",
                extension = "fb2",
                mimeType = "application/fb2",
                sizeBytes = 1_000L,
                lastModified = 1_000L,
                title = id,
                sortTitle = id,
                authorsText = "Автор",
                addedAt = 1_000L,
                updatedAt = 1_000L,
                lastSeenAt = 1_000L,
                readingStatus = status,
                favorite = favorite,
                rating = rating,
                lastOpenedAt = lastOpenedAt,
                openCount = openCount,
            ),
        )
    }

    private companion object {
        const val SOURCE_ID = "source-1"
        const val ORIGINAL_ID = "doc:original"
        const val COPY_ID = "doc:copy"
        const val COPY_TWO_ID = "doc:copy-2"
        const val FINISHED_ID = "doc:finished"
        const val COLLECTION_ID = "collection-1"
    }
}
