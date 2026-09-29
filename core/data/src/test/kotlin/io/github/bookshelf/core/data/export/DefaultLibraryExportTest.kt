package io.github.bookshelf.core.data.export

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.repository.impl.CollectionRepositoryImpl
import io.github.bookshelf.core.data.tag.BookTagWriter
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Сборка файла экспорта из настоящей базы: книги, жанры, полки и прогресс.
 *
 * До этого порта экспорт умел только складывать уже готовые доменные объекты в JSON, а взять их
 * из библиотеки было неоткуда — в настройках на месте кнопки стояло обещание. Поэтому проверяется
 * именно путь от данных до файла: авторы берутся из связей `book_authors` (а не из строки
 * `authorsText`), жанры — из `book_tags`/`tags`, прогресс — из `reading_progress`, полки — с типом,
 * правилами и числом книг.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class DefaultLibraryExportTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var context: Context
    private lateinit var export: DefaultLibraryExport

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        export = DefaultLibraryExport(
            context = context,
            database = database,
            collections = CollectionRepositoryImpl(
                dao = database.collectionDao(),
                bookDao = database.bookDao(),
                clock = FIXED_CLOCK,
            ),
            clock = FIXED_CLOCK,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `snapshot takes authors from links, genres from tags and progress from the reader table`() =
        runBlocking<Unit> {
            givenTwoBooks()

            val content = export.snapshot()

            val solaris = content.books.single { book -> book.id == SOLARIS }
            // В строке книги авторов нет вовсе: единственный источник — связи `book_authors`.
            assertThat(solaris.authorsText).isEmpty()
            assertThat(solaris.authors).containsExactly("Станислав Лем")
            assertThat(solaris.progressPercent).isEqualTo(0.42f)
            assertThat(content.genres[SOLARIS]).containsExactly("sf_heroic", "sf_space")

            val coauthored = content.books.single { book -> book.id == COAUTHORED }
            // Порядок авторов — как в файле книги (позиция связи), а не по алфавиту.
            assertThat(coauthored.authors)
                .containsExactly("Александр Тарарев", "Юрий Тарарев")
                .inOrder()
            assertThat(coauthored.progressPercent).isNull()
            assertThat(content.genres[COAUTHORED]).containsExactly("detective")
        }

    @Test
    fun `books go into the file in a stable order`() = runBlocking<Unit> {
        givenTwoBooks()

        // Порядок строк базы не определён, а файл у человека должен быть сравнимым: две выгрузки
        // подряд не должны отличаться перестановкой книг.
        assertThat(export.snapshot().books.map { book -> book.title })
            .containsExactly("Атом", "Солярис")
            .inOrder()
    }

    @Test
    fun `book without author links falls back to the authors string`() = runBlocking<Unit> {
        givenSource()
        database.bookDao().upsertAll(listOf(book(id = ORPHAN, title = "Без связей", authorsText = "Иван Петров")))

        // Связи пишет разбор метаданных; книга, попавшая в базу до него, не должна остаться
        // в выгрузке без авторов.
        assertThat(export.snapshot().books.single().authors).containsExactly("Иван Петров")
    }

    @Test
    fun `snapshot keeps manual and smart shelves with type, rules and book counts`() = runBlocking<Unit> {
        givenTwoBooks()

        val shelves = export.snapshot().collections.associateBy { collection -> collection.name }

        assertThat(shelves.keys).containsExactly("Космос", "Читаю")
        assertThat(shelves.getValue("Читаю").type.name).isEqualTo("MANUAL")
        assertThat(shelves.getValue("Читаю").bookCount).isEqualTo(1)
        assertThat(shelves.getValue("Космос").type.name).isEqualTo("SMART")
        assertThat(shelves.getValue("Космос").rulesJson).contains("sf_space")
        // Число книг умной полки считается по правилам, а не по связям: их у неё нет вовсе.
        assertThat(shelves.getValue("Космос").bookCount).isEqualTo(1)
    }

    @Test
    fun `exportTo writes the same file the snapshot describes`() = runBlocking<Unit> {
        givenTwoBooks()
        val written = ByteArrayOutputStream()
        val destination = Uri.parse("content://test/bookshelf-export.json")
        shadowOf(context.contentResolver).registerOutputStream(destination, written)

        val summary = export.exportTo(destination).getOrNull()

        assertThat(summary).isEqualTo(LibraryExportSummary(books = 2, collections = 2))
        val parsed = Json.decodeFromString<LibraryExportFile>(written.toString("UTF-8"))
        assertThat(parsed.version).isEqualTo(CURRENT_EXPORT_VERSION)
        assertThat(parsed.exportedAt).isEqualTo(NOW)
        assertThat(parsed.books.map { book -> book.id }).containsExactly(SOLARIS, COAUTHORED)
        assertThat(parsed.books.single { book -> book.id == SOLARIS }.genres)
            .containsExactly("sf_heroic", "sf_space")
        assertThat(parsed.books.single { book -> book.id == SOLARIS }.progressPercent).isEqualTo(0.42f)
        assertThat(parsed.collections.map { collection -> collection.name })
            .containsExactly("Читаю", "Космос")
    }

    /**
     * Две книги: одна с одним автором и прогрессом, вторая — соавторство.
     *
     * Жанры и авторы записаны теми же компонентами, что и в приложении (`BookTagWriter`,
     * `BookAuthorWriter`): если формат связей изменится, тест это заметит.
     */
    private suspend fun givenTwoBooks() {
        givenSource()
        database.bookDao().upsertAll(
            listOf(
                book(id = SOLARIS, title = "Солярис", authorsText = ""),
                book(id = COAUTHORED, title = "Атом", authorsText = "Александр Тарарев, Юрий Тарарев"),
            ),
        )
        BookAuthorWriter(database.authorDao()).replace(SOLARIS, "Станислав Лем")
        BookAuthorWriter(database.authorDao()).replace(COAUTHORED, "Александр Тарарев, Юрий Тарарев")
        BookTagWriter(database.tagDao()).replace(SOLARIS, listOf("sf_space", "sf_heroic"))
        BookTagWriter(database.tagDao()).replace(COAUTHORED, listOf("detective"))
        database.readingProgressDao().upsert(
            ReadingProgressEntity(bookId = SOLARIS, percent = 0.42f, updatedAt = NOW),
        )
        database.collectionDao().upsertAll(
            listOf(
                CollectionEntity(id = MANUAL_SHELF, name = "Читаю", position = 0, createdAt = NOW),
                CollectionEntity(
                    id = SMART_SHELF,
                    name = "Космос",
                    type = io.github.bookshelf.core.database.model.CollectionType.SMART,
                    position = 1,
                    createdAt = NOW,
                    rulesJson = "{\"tags\":[\"sf_space\"]}",
                ),
            ),
        )
        database.collectionDao().insertBooks(
            listOf(CollectionBookCrossRef(collectionId = MANUAL_SHELF, bookId = SOLARIS)),
        )
    }

    private suspend fun givenSource() {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = io.github.bookshelf.core.database.model.SourceKind.SAF_TREE,
            ),
        )
    }

    private fun book(id: String, title: String, authorsText: String) = BookEntity(
        id = id,
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$id",
        relativePath = "$id.fb2",
        fileName = "$id.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1024L,
        lastModified = NOW,
        title = title,
        sortTitle = title,
        authorsText = authorsText,
        addedAt = NOW,
        updatedAt = NOW,
        lastSeenAt = NOW,
    )

    private companion object {
        const val SOURCE_ID = "source-1"
        const val SOLARIS = "book-solaris"
        const val COAUTHORED = "book-atom"
        const val ORPHAN = "book-orphan"
        const val MANUAL_SHELF = "shelf-manual"
        const val SMART_SHELF = "shelf-smart"
        const val NOW = 1_790_000_000_000L

        /** Время фиксировано: и `exportedAt`, и имя файла в тесте должны быть предсказуемы. */
        val FIXED_CLOCK = object : AppClock {
            override fun nowMillis(): Long = NOW
        }
    }
}
