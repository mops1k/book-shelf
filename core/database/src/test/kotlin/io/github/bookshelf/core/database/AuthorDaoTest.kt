package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.dao.AuthorDao
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Связи «книга — автор»: то, на чём держится всё авторское измерение.
 *
 * Автор раньше жил только строкой `books.authorsText`, и таблицы `authors`/`book_authors` были
 * пустыми. Теперь приложение пишет связи само, поэтому здесь проверяется механика записи:
 * порядок авторов из файла, стабильные id, счётчики книг и выборка для разового прохода.
 *
 * Идентификаторы авторов детерминированные (`author:` + имя в нижнем регистре) — как у тегов:
 * повторный разбор метаданных не плодит дубли и переживает смену регистра в источнике.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AuthorDaoTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var authors: AuthorDao
    private lateinit var books: BookDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        authors = database.authorDao()
        books = database.bookDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `links keep the order of authors from the file`() = runBlocking {
        books.insert(BookDaoTestFixtures.book(id = "b1", sourceId = "s1", author = "$ALEXANDER, $YURY"))
        givenAuthors(ALEXANDER, YURY)

        authors.insertBookAuthors(
            listOf(
                BookAuthorCrossRef(bookId = "b1", authorId = idOf(YURY), position = 0),
                BookAuthorCrossRef(bookId = "b1", authorId = idOf(ALEXANDER), position = 1),
            ),
        )

        // Позиция — порядок из файла: по нему карточка показывает «Юрий Тарарев, Александр Тарарев»,
        // а не алфавит (иначе порядок авторов менялся бы сам по себе).
        assertThat(authors.getBookAuthors("b1").map { ref -> ref.authorId })
            .containsExactly(idOf(YURY), idOf(ALEXANDER))
            .inOrder()
    }

    @Test
    fun `books with an authors line but without links are found for the one-time pass`() = runBlocking<Unit> {
        books.upsertAll(
            listOf(
                BookDaoTestFixtures.book(id = "linked", sourceId = "s1", author = ALEXANDER),
                BookDaoTestFixtures.book(id = "unlinked", sourceId = "s1", author = YURY),
                // Без авторов вовсе: проходу такие книги не нужны, они и не должны его заводить.
                BookDaoTestFixtures.book(id = "anonymous", sourceId = "s1", author = ""),
            ),
        )
        authors.insertBookAuthors(listOf(BookAuthorCrossRef(bookId = "linked", authorId = idOf(ALEXANDER))))

        // Это условие и есть «один разовость»: после прохода выборка пуста, и повторный запуск
        // ничего не пишет.
        assertThat(authors.idsWithoutAuthors()).containsExactly("unlinked")
    }

    @Test
    fun `authors are read by a batch of ids`() = runBlocking<Unit> {
        givenAuthors(ALEXANDER, YURY)

        val found = authors.getByIds(listOf(idOf(ALEXANDER), idOf(YURY), idOf("Никого Нет")))

        assertThat(found.map { it.name }).containsExactly(ALEXANDER, YURY)
    }

    @Test
    fun `book counts are refreshed only for the authors involved`() = runBlocking {
        books.upsertAll(
            listOf(
                BookDaoTestFixtures.book(id = "b1", sourceId = "s1", author = "$ALEXANDER, $YURY"),
                BookDaoTestFixtures.book(id = "b2", sourceId = "s1", author = YURY),
                BookDaoTestFixtures.book(id = "b3", sourceId = "s1", author = TOLSTOY),
            ),
        )
        givenAuthors(ALEXANDER, YURY, TOLSTOY)
        authors.insertBookAuthors(
            listOf(
                BookAuthorCrossRef(bookId = "b1", authorId = idOf(ALEXANDER), position = 0),
                BookAuthorCrossRef(bookId = "b1", authorId = idOf(YURY), position = 1),
                BookAuthorCrossRef(bookId = "b2", authorId = idOf(YURY), position = 0),
                BookAuthorCrossRef(bookId = "b3", authorId = idOf(TOLSTOY)),
            ),
        )

        authors.refreshBookCounts(listOf(idOf(ALEXANDER), idOf(YURY)))

        // Счётчик — денормализация для списков авторов. Точечное обновление, а не пересчёт всей
        // таблицы: правка одной книги не должна трогать строки чужих авторов.
        assertThat(authors.getById(idOf(ALEXANDER))?.bookCount).isEqualTo(1)
        assertThat(authors.getById(idOf(YURY))?.bookCount).isEqualTo(2)
        assertThat(authors.getById(idOf(TOLSTOY))?.bookCount).isEqualTo(0)
    }

    @Test
    fun `book count of a new author is set by the refresh`() = runBlocking {
        books.insert(BookDaoTestFixtures.book(id = "b1", sourceId = "s1", author = ALEXANDER))
        givenAuthors(ALEXANDER)
        authors.insertBookAuthors(listOf(BookAuthorCrossRef(bookId = "b1", authorId = idOf(ALEXANDER))))

        authors.refreshBookCounts(listOf(idOf(ALEXANDER)))

        assertThat(authors.getById(idOf(ALEXANDER))?.bookCount).isEqualTo(1)
    }

    @Test
    fun `all authors are listed by sort name`() = runBlocking<Unit> {
        authors.upsertAll(
            listOf(
                AuthorEntity(id = idOf(YURY), name = YURY, sortName = "Тарарев Юрий"),
                AuthorEntity(id = idOf(ALEXANDER), name = ALEXANDER, sortName = "Тарарев Александр"),
            ),
        )

        assertThat(authors.getByIds(listOf(idOf(YURY), idOf(ALEXANDER))).map { it.sortName })
            .containsExactly("Тарарев Юрий", "Тарарев Александр")
    }

    private suspend fun givenAuthors(vararg names: String) {
        authors.upsertAll(
            names.map { name ->
                AuthorEntity(id = idOf(name), name = name, sortName = name.substringAfterLast(' ') + " " + name)
            },
        )
    }

    private companion object {
        const val ALEXANDER = "Александр Тарарев"
        const val YURY = "Юрий Тарарев"
        const val TOLSTOY = "Лев Толстой"

        /** Тот же идентификатор, что строит приложение: `author:` + имя в нижнем регистре. */
        fun idOf(name: String): String = "author:" + name.lowercase()
    }
}
