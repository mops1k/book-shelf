package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.data.model.MetadataOrigin
import io.github.bookshelf.core.data.model.MetadataOverride
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.repository.impl.MetadataOverrideRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity
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
 * Хранение ручных правок метаданных («Архитектура» §3.1, §4.4).
 *
 * Главное проверяемое свойство — **правка не должна попадать в `books`**. Если бы она туда
 * попала, пересканирование изменённого файла затерло бы её, и вся отдельная таблица была бы
 * бессмысленной.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MetadataOverrideRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: MetadataOverrideRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MetadataOverrideRepositoryImpl(database.metadataOverrideDao())
    }

    /** Источник и книга нужны до проверки правки: внешний ключ требует существующей книги. */
    private suspend fun givenBook() {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().insert(book())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun save_then_observe_returns_the_same_patch() = runBlocking {
        givenBook()
        repository.save(override(title = "Моя редакция", series = "Классика")).assertSuccess()

        val stored = repository.observe(BOOK_ID).first()

        assertThat(stored?.patch?.title).isEqualTo("Моя редакция")
        assertThat(stored?.patch?.seriesName).isEqualTo("Классика")
        assertThat(stored?.origin).isEqualTo(MetadataOrigin.MANUAL)
    }

    @Test
    fun save_does_not_touch_the_books_table() = runBlocking {
        givenBook()
        repository.save(override(title = "Моя редакция"))

        val titleInBooks = database.bookDao().getById(BOOK_ID)?.title

        assertThat(titleInBooks).isEqualTo("Исходное название")
    }

    @Test
    fun second_save_replaces_the_first() = runBlocking {
        givenBook()
        repository.save(override(title = "Первая правка"))
        repository.save(override(title = "Вторая правка"))

        val stored = repository.observe(BOOK_ID).first()

        assertThat(repository.observe(BOOK_ID).first()?.patch?.title).isEqualTo("Вторая правка")
        assertThat(stored?.patch?.title).isEqualTo("Вторая правка")
    }

    @Test
    fun clear_returns_the_book_to_file_metadata() = runBlocking {
        givenBook()
        repository.save(override(title = "Моя редакция"))

        repository.clear(BOOK_ID).assertSuccess()

        assertThat(repository.observe(BOOK_ID).first()).isNull()
    }

    @Test
    fun observe_unknown_book_is_null_not_failure() = runBlocking {
        assertThat(repository.observe("нет-такой-книги").first()).isNull()
    }

    @Test
    fun save_for_unknown_book_fails_instead_of_creating_orphan() = runBlocking {
        val result = repository.save(override(bookId = "нет-такой-книги", title = "Правка"))

        assertThat(result.isFailure).isTrue()
        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun clear_for_unknown_book_is_a_no_op() = runBlocking {
        val result = repository.clear("нет-такой-книги")

        assertThat(result.isSuccess).isTrue()
    }

    private fun AppResult<Unit>.assertSuccess() {
        assertThat(isSuccess).isTrue()
    }

    private fun override(
        bookId: String = BOOK_ID,
        title: String? = null,
        series: String? = null,
    ) = MetadataOverride(
        bookId = bookId,
        patch = MetadataPatch(title = title, seriesName = series),
        updatedAt = UPDATED_AT,
    )

    private fun book() = BookEntity(
        id = BOOK_ID,
        sourceId = SOURCE_ID,
        documentUri = "content://com.android.externalstorage.documents/tree/books/document/$BOOK_ID",
        relativePath = "Толстой/Анна Каренина.fb2",
        fileName = "Анна Каренина.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1024L,
        lastModified = 1000L,
        title = "Исходное название",
        sortTitle = "Исходное название",
        authorsText = "Лев Толстой",
        addedAt = 1000L,
        updatedAt = 1000L,
        lastSeenAt = 1000L,
    )

    private companion object {
        const val BOOK_ID = "book-1"
        const val SOURCE_ID = "source-1"
        const val UPDATED_AT = 5000L
    }
}
