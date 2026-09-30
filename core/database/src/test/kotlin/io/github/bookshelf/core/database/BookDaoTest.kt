package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
 * Дымовой тест инфраструктуры TDD: схема создаётся, вставка и чтение работают.
 *
 * Проверяется ровно то, без чего остальные DAO-тесты бессмысленны: in-memory БД на API 24
 * (минимальная поддерживаемая версия), вставка источника и книги, чтение обратно и поиск
 * по `documentUri` — ключевой запрос инкрементального сканирования.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookDaoTest {

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
    fun insertBookAndFindItByDocumentUri() = runBlocking {
        val source = SourceEntity(
            id = "source-1",
            uri = "content://com.android.externalstorage.documents/tree/books",
            displayName = "Books",
            kind = SourceKind.SAF_TREE,
        )
        val book = BookEntity(
            id = "book-1",
            sourceId = source.id,
            documentUri = "content://com.android.externalstorage.documents/tree/books/document/book-1",
            relativePath = "Петров/Тестовая книга.fb2",
            fileName = "Тестовая книга.fb2",
            extension = "fb2",
            mimeType = "application/fb2",
            sizeBytes = 1024L,
            lastModified = 1000L,
            title = "Тестовая книга",
            sortTitle = "Тестовая книга",
            authorsText = "Иван Петров",
            addedAt = 1000L,
            updatedAt = 1000L,
            lastSeenAt = 1000L,
        )

        database.sourceDao().insert(source)
        database.bookDao().upsertAll(listOf(book))

        val loadedById = database.bookDao().getById(book.id)
        assertThat(loadedById).isNotNull()
        assertThat(loadedById?.title).isEqualTo("Тестовая книга")

        val loadedByUri = database.bookDao().findByDocumentUri(book.documentUri)
        assertThat(loadedByUri).isNotNull()
        assertThat(loadedByUri?.id).isEqualTo(book.id)
    }

    @Test
    fun booksWithParseErrorsReturnsOnlyFailedBooks() {
        runBlocking {
            val dao = database.bookDao()
            database.sourceDao().insert(BookDaoTestFixtures.source("source-1"))
            dao.upsertAll(
                listOf(
                    BookDaoTestFixtures.book("ok-1", "source-1", title = "Разобралась"),
                    BookDaoTestFixtures.book(
                        id = "failed-1",
                        sourceId = "source-1",
                        title = "Проверка_1.docx",
                        parseState = ParseState.FAILED,
                        parseError = "Формат не поддерживается",
                    ),
                    BookDaoTestFixtures.book(
                        id = "failed-2",
                        sourceId = "source-1",
                        title = "Уткин.fb2",
                        parseState = ParseState.FAILED,
                        parseError = "FB2 внутри архива превышает допустимый размер XML",
                    ),
                ),
            )

            val failed = dao.booksWithParseErrors(limit = 200)

            // Отчёт сканирования показывает именно их: имя и причину, а не только число.
            assertThat(failed.map { entity -> entity.id }).containsExactly("failed-1", "failed-2")
            assertThat(failed.map { entity -> entity.parseError })
                .containsExactly(
                    "Формат не поддерживается",
                    "FB2 внутри архива превышает допустимый размер XML",
                )
        }
    }

    @Test
    fun booksWithParseErrorsRespectsTheLimit() {
        runBlocking {
            val dao = database.bookDao()
            database.sourceDao().insert(BookDaoTestFixtures.source("source-1"))
            dao.upsertAll(
                (1..5).map { index ->
                    BookDaoTestFixtures.book(
                        id = "failed-$index",
                        sourceId = "source-1",
                        title = "Книга $index",
                        parseState = ParseState.FAILED,
                    )
                },
            )

            // Список для человека, а не выгрузка: ограничение держится в запросе.
            assertThat(dao.booksWithParseErrors(limit = 2)).hasSize(2)
        }
    }
}
