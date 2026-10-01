package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Книги того же размера — единственные, чьё содержимое может совпасть с новым файлом.
 *
 * Зачем запрос: решать «это копия известной книги?» дешевле всего по размеру, а содержимое читать
 * только для совпавших. Так полное чтение файлов не случается там, где совпадения быть не может
 * (уникальный размер), а порядок книг задаёт, какая из одинаковых считается оригиналом: самая
 * ранняя по дате добавления, при равенстве — с меньшим путём (решение Александра 01.10.2026).
 *
 * Пустые файлы исключены: у них нет содержимого, и сравнивать их нельзя (нулевой размер совпадает
 * у всех битых книг сразу).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class KnownBookCopyQueryTest {

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
    fun `only books of the same size are returned`() = runBlocking<Unit> {
        givenBook(id = "doc:same", size = 514_015L)
        givenBook(id = "doc:other", size = 588_846L)

        val candidates = database.bookCopyDao().findBooksBySize(514_015L)

        assertThat(candidates.map { book -> book.id }).containsExactly("doc:same")
    }

    @Test
    fun `the earliest book comes first`() = runBlocking<Unit> {
        givenBook(id = "doc:copy", size = 514_015L, addedAt = 2_000L, path = "2026-09-30/Книга.fb2.zip")
        givenBook(
            id = "doc:original",
            size = 514_015L,
            addedAt = 1_000L,
            path = "Тармашев/Древний/Книга.fb2.zip",
        )

        val candidates = database.bookCopyDao().findBooksBySize(514_015L)

        // Первый в списке становится оригиналом: копия читалки добавлена позже книги пользователя.
        assertThat(candidates.first().id).isEqualTo("doc:original")
    }

    @Test
    fun `empty files are never candidates`() = runBlocking<Unit> {
        givenBook(id = "doc:broken", size = 0L)

        assertThat(database.bookCopyDao().findBooksBySize(0L)).isEmpty()
    }

    @Test
    fun `a remembered fingerprint is written to the book`() = runBlocking<Unit> {
        givenBook(id = "doc:original", size = 514_015L)

        database.bookCopyDao().rememberContentHash("doc:original", "hash-1")

        assertThat(database.bookDao().getById("doc:original")?.contentHash).isEqualTo("hash-1")
    }

    private suspend fun givenBook(
        id: String,
        size: Long,
        addedAt: Long = 1_000L,
        path: String? = null,
    ) {
        database.sourceDao().insert(BookDaoTestFixtures.source(id = SOURCE_ID))
        val book = BookDaoTestFixtures.book(
            id = id,
            sourceId = SOURCE_ID,
            sizeBytes = size,
            addedAt = addedAt,
        )
        database.bookDao().insert(
            path?.let { book.copy(relativePath = it, fileName = it.substringAfterLast('/')) } ?: book,
        )
    }

    private companion object {
        const val SOURCE_ID = "src-1"
    }
}
