package io.github.bookshelf.core.data.author

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
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
 * Разовый проход по существующей библиотеке: связи авторов для книг, у которых есть строка авторов,
 * но нет связей.
 *
 * Зачем: у Александра 72 книги, разобранные до этой задачи, — у них заполнен `books.authorsText`,
 * а `authors`/`book_authors` пусты. Без прохода авторское измерение по связям не нашло бы ни одной
 * книги из уже собранной библиотеки, то есть обновление сломало бы обзор авторов на устройстве.
 *
 * Проход идемпотентен: он выбирает только книги без связей, поэтому повторный запуск (и каждый
 * следующий старт приложения) не пишет ничего.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AuthorBackfillTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var backfill: AuthorBackfill

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backfill = AuthorBackfill(
            database = database,
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            authorWriter = BookAuthorWriter(database.authorDao()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `one pass fills the author links of the existing library`() = runBlocking<Unit> {
        givenLibrary()
        database.bookDao().upsertAll(
            listOf(
                book("pair", "$ALEXANDER, $YURY"),
                book("solo", YURY),
                book("anonymous", ""),
            ),
        )

        backfill.runOnce()

        assertThat(database.authorDao().getBookAuthors("pair").map { it.authorId })
            .containsExactly(idOf(ALEXANDER), idOf(YURY)).inOrder()
        assertThat(database.authorDao().getBookAuthors("solo").map { it.authorId })
            .containsExactly(idOf(YURY))
        // Книга без авторов остаётся без связей: выдумывать автора из имени файла нельзя.
        assertThat(database.authorDao().getBookAuthors("anonymous")).isEmpty()
        assertThat(database.authorDao().getById(idOf(YURY))?.bookCount).isEqualTo(2)
        assertThat(database.authorDao().getById(idOf(ALEXANDER))?.bookCount).isEqualTo(1)
    }

    @Test
    fun `after the pass there is nothing left to fill`() = runBlocking<Unit> {
        givenLibrary()

        backfill.runOnce()

        // Именно это условие делает проход «разовым»: выборка пуста, и повторный запуск
        // при следующем старте приложения не пишет ни одной строки.
        assertThat(database.authorDao().idsWithoutAuthors()).isEmpty()
    }

    @Test
    fun `second pass changes nothing`() = runBlocking<Unit> {
        givenLibrary()
        backfill.runOnce()
        val links = database.authorDao().getBookAuthors("pair")
        val author = database.authorDao().getById(idOf(ALEXANDER))

        backfill.runOnce()

        assertThat(database.authorDao().getBookAuthors("pair")).isEqualTo(links)
        assertThat(database.authorDao().getById(idOf(ALEXANDER))).isEqualTo(author)
    }

    @Test
    fun `a book added after the pass is filled by the next pass`() = runBlocking<Unit> {
        givenLibrary()
        backfill.runOnce()
        database.bookDao().insert(book("later", ALEXANDER))

        // Проход не «одноразовый флаг», а выборка по данным: книга, появившаяся позже (скан,
        // импорт), тоже получит связи, а не останется невидимой для фильтра по автору.
        backfill.runOnce()

        assertThat(database.authorDao().getBookAuthors("later").map { it.authorId })
            .containsExactly(idOf(ALEXANDER))
    }

    private suspend fun givenLibrary() {
        database.sourceDao().insert(SOURCE)
        database.bookDao().upsertAll(listOf(book("pair", "$ALEXANDER, $YURY")))
    }

    private fun book(id: String, authorsText: String) = BookEntity(
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
        authorsText = authorsText,
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
