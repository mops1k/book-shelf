package io.github.bookshelf.core.data.book

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.tag.BookTagWriter
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.ParseState
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Запись результата разбора метаданных: строка книги, жанры и связи авторов — одним шагом.
 *
 * Разбор метаданных — это путь, по которому авторы попадают в библиотеку после скана: скан создаёт
 * книгу с пустой строкой авторов, а `books.authorsText` заполняет разбор. Если бы связи авторов
 * писались где-то отдельно, скан и разбор расходились бы: полка показывала автора, а фильтр по нему
 * не находил ничего — так и было до этой задачи.
 *
 * Сканы и импорт OPDS сюда не входят: они пишут книгу своими компонентами (`ScanSourceWorker`,
 * `OpdsDownloadWorker`), а разбор после них вызывает тот же [BookAuthorWriter].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ParsedBookWriterTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var writer: ParsedBookWriter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writer = ParsedBookWriter(
            bookDao = database.bookDao(),
            tagWriter = BookTagWriter(database.tagDao()),
            authorWriter = BookAuthorWriter(database.authorDao()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `parse result is written together with genres and author links`() = runBlocking<Unit> {
        database.sourceDao().insert(SOURCE)
        // Так книгу создаёт скан: заголовок из имени файла, авторов ещё нет.
        val scanned = book("book-1", title = "book-1", authorsText = "", parseState = ParseState.PENDING)
        database.bookDao().insert(scanned)

        writer.store(
            updated = scanned.copy(
                title = "Война и мир",
                sortTitle = "Война и мир",
                authorsText = "$ALEXANDER, $YURY",
                parseState = ParseState.OK,
            ),
            tags = listOf("sf_space"),
        )

        val stored = database.bookDao().getById("book-1")
        assertThat(stored?.title).isEqualTo("Война и мир")
        assertThat(stored?.parseState).isEqualTo(ParseState.OK)
        assertThat(database.authorDao().getBookAuthors("book-1").map { it.authorId })
            .containsExactly(idOf(ALEXANDER), idOf(YURY)).inOrder()
        assertThat(database.tagDao().getBookTags("book-1").map { it.tagId }).containsExactly("tag:sf_space")
    }

    @Test
    fun `authors written by import are replaced on a repeated parse`() = runBlocking<Unit> {
        database.sourceDao().insert(SOURCE)
        val imported = book("book-2", title = "Сборник", authorsText = YURY, parseState = ParseState.PENDING)
        database.bookDao().insert(imported)

        // OPDS-запись пишет авторов из фида; разбор файла уточняет состав — и он должен победить.
        writer.store(updated = imported.copy(authorsText = "$ALEXANDER, $YURY"), tags = emptyList())

        assertThat(database.authorDao().getBookAuthors("book-2").map { it.authorId })
            .containsExactly(idOf(ALEXANDER), idOf(YURY)).inOrder()
    }

    private fun book(
        id: String,
        title: String,
        authorsText: String,
        parseState: ParseState = ParseState.OK,
    ) = BookEntity(
        id = id,
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$id",
        relativePath = "$title.fb2",
        fileName = "$title.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1000L,
        lastModified = 1000L,
        title = title,
        sortTitle = title,
        authorsText = authorsText,
        parseState = parseState,
        addedAt = 1000L,
        updatedAt = 1000L,
        lastSeenAt = 1000L,
    )

    private companion object {
        const val SOURCE_ID = "source-1"
        const val ALEXANDER = "Александр Тарарев"
        const val YURY = "Юрий Тарарев"

        fun idOf(name: String): String = AUTHOR_ID_PREFIX + name.lowercase()

        const val AUTHOR_ID_PREFIX = "author:"

        val SOURCE = SourceEntity(
            id = SOURCE_ID,
            uri = "content://com.android.externalstorage.documents/tree/books",
            displayName = "Books",
            kind = SourceKind.SAF_TREE,
        )
    }
}
