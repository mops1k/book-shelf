package io.github.bookshelf.core.data.author

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Запись авторов книги: `authors` + `book_authors`.
 *
 * До этой задачи связи не писал никто: `authors` и `book_authors` оставались пустыми при 72 книгах
 * на устройстве, поэтому авторское измерение (фасет, фильтр, серии автора) существовало только на
 * денормализованной строке `books.authorsText` и не находило книгу, где автор записан вторым.
 *
 * Идентификатор автора детерминированный — `author:` + имя в нижнем регистре, как у тегов
 * (`BookTagWriter`): повторный разбор метаданных не плодит дубли и переживает смену регистра
 * в источнике.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookAuthorWriterTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var writer: BookAuthorWriter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writer = BookAuthorWriter(database.authorDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `authors and links are written in the order from the file`() = runBlocking<Unit> {
        givenBooks(COAUTHORED)

        writer.replace(COAUTHORED, "$ALEXANDER, $YURY")

        val refs = database.authorDao().getBookAuthors(COAUTHORED)
        assertThat(refs.map { it.authorId }).containsExactly(idOf(ALEXANDER), idOf(YURY)).inOrder()
        assertThat(refs.map { it.position }).containsExactly(0, 1).inOrder()
        assertThat(database.authorDao().getById(idOf(YURY))?.name).isEqualTo(YURY)
    }

    @Test
    fun `authors are split by comma and semicolon and repeats are dropped`() = runBlocking<Unit> {
        givenBooks(BOOK)

        // OPDS отдаёт авторов через «;», парсеры — через «,»: разбор один на всех.
        writer.replace(BOOK, "Александр Тарарев; Юрий Тарарев, александр тарарев")

        assertThat(database.authorDao().getBookAuthors(BOOK).map { it.authorId })
            .containsExactly(idOf(ALEXANDER), idOf(YURY))
    }

    @Test
    fun `sort name puts the last word first for sorting by surname`() = runBlocking<Unit> {
        givenBooks(BOOK)

        writer.replace(BOOK, ALEXANDER)

        assertThat(database.authorDao().getById(idOf(ALEXANDER))?.sortName).isEqualTo("Тарарев Александр")
    }

    @Test
    fun `the same author in different books is a single row`() = runBlocking<Unit> {
        givenBooks(BOOK, COAUTHORED)

        writer.replace(BOOK, ALEXANDER)
        writer.replace(COAUTHORED, "$ALEXANDER, $YURY")

        assertThat(database.authorDao().getBookAuthors(BOOK).map { it.authorId })
            .containsExactly(idOf(ALEXANDER))
        assertThat(database.authorDao().getById(idOf(ALEXANDER))?.bookCount).isEqualTo(2)
    }

    @Test
    fun `rewriting authors replaces the old links instead of appending`() = runBlocking<Unit> {
        givenBooks(BOOK)

        writer.replace(BOOK, "$ALEXANDER, $YURY")
        writer.replace(BOOK, YURY)

        // Правка авторов в карточке — замена набора: старые связи обязаны исчезнуть, иначе
        // фильтр продолжал бы находить книгу по автору, которого в ней уже нет.
        assertThat(database.authorDao().getBookAuthors(BOOK).map { it.authorId })
            .containsExactly(idOf(YURY))
        assertThat(database.authorDao().getById(idOf(ALEXANDER))?.bookCount).isEqualTo(0)
        assertThat(database.authorDao().getById(idOf(YURY))?.bookCount).isEqualTo(1)
    }

    @Test
    fun `book counts stay consistent for every author involved`() = runBlocking<Unit> {
        givenBooks(BOOK, COAUTHORED)

        writer.replace(BOOK, "$ALEXANDER, $YURY")
        writer.replace(COAUTHORED, YURY)

        assertThat(database.authorDao().getById(idOf(ALEXANDER))?.bookCount).isEqualTo(1)
        assertThat(database.authorDao().getById(idOf(YURY))?.bookCount).isEqualTo(2)
    }

    @Test
    fun `empty authors text clears the links`() = runBlocking<Unit> {
        givenBooks(BOOK)
        writer.replace(BOOK, ALEXANDER)

        writer.replace(BOOK, "")

        // Книга без авторов (TXT без метаданных) не должна остаться в чужом авторском измерении.
        assertThat(database.authorDao().getBookAuthors(BOOK)).isEmpty()
        assertThat(database.authorDao().getById(idOf(ALEXANDER))?.bookCount).isEqualTo(0)
    }

    @Test
    fun `repeated write with the same authors changes nothing`() = runBlocking<Unit> {
        givenBooks(BOOK)

        writer.replace(BOOK, "$ALEXANDER, $YURY")
        val before = database.authorDao().getBookAuthors(BOOK)
        writer.replace(BOOK, "$ALEXANDER, $YURY")

        // Идемпотентность — условие разового прохода по существующей библиотеке: повторный запуск
        // не должен ни дублировать связи, ни менять позиции.
        assertThat(database.authorDao().getBookAuthors(BOOK)).isEqualTo(before)
    }

    private suspend fun givenBooks(vararg ids: String) {
        database.sourceDao().insert(
            io.github.bookshelf.core.database.entity.SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = io.github.bookshelf.core.database.model.SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().upsertAll(ids.map { id -> book(id) })
    }

    private fun book(id: String) = BookEntity(
        id = id,
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$id",
        relativePath = "$id.fb2",
        fileName = "$id.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1000L,
        lastModified = 1000L,
        title = id,
        sortTitle = id,
        authorsText = "",
        addedAt = 1000L,
        updatedAt = 1000L,
        lastSeenAt = 1000L,
    )

    private companion object {
        const val SOURCE_ID = "source-1"
        const val BOOK = "book-1"
        const val COAUTHORED = "book-2"
        const val ALEXANDER = "Александр Тарарев"
        const val YURY = "Юрий Тарарев"

        fun idOf(name: String): String = AUTHOR_ID_PREFIX + name.lowercase()

        const val AUTHOR_ID_PREFIX = "author:"
    }
}
