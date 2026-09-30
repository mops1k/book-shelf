package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.fb2opt.DefaultFb2Optimizer
import io.github.bookshelf.core.fb2opt.Fb2ImageOptimizer
import io.github.bookshelf.core.fb2opt.JpegEntropyOptimizer
import io.github.bookshelf.core.testing.fake.FakeBookContentOpener
import io.github.bookshelf.core.testing.fake.FakeReaderSession
import io.github.bookshelf.core.testing.fake.FakeScanRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Оптимизация книги FB2 (`BookRepositoryImpl.optimizeBook`).
 *
 * Проверяется то, из-за чего оптимизация может испортить библиотеку: замена файла при отсутствии
 * выигрыша, работа не с тем форматом и — главное — сбой записи. Файл в SAF заменяется записью в тот
 * же документ в режиме усечения, поэтому при обрыве записи книга осталась бы битой, если бы
 * вызывающий не вернул исходные байты.
 *
 * Фикстура настоящая: `book-small.fb2` собран из книги «Книга Уткина Мы тут жизнь живём»
 * (шапка, описание, раздел с текстом и картинка взяты как есть).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookOptimizationTest {

    private lateinit var database: BookshelfDatabase
    private val opener = FakeBookContentOpener()
    private val session = FakeReaderSession()

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
    fun `an fb2 book is replaced by a smaller file`() = runBlocking {
        givenBook(extension = "fb2", content = fixture())

        val result = repository().optimizeBook(BOOK_ID).getOrNull()

        assertThat(result).isNotNull()
        assertThat(result!!.changed).isTrue()
        assertThat(result.savedBytes).isGreaterThan(0)
        assertThat(opener.files.getValue(URI)!!.size).isEqualTo(result.afterBytes)
    }

    @Test
    fun `a book of another format is refused and left alone`() = runBlocking {
        val content = fixture()
        givenBook(extension = "epub", content = content)

        val result = repository().optimizeBook(BOOK_ID)

        assertThat(result.errorOrNull()?.message).contains("только книги FB2")
        // Файл не тронут: ни одной замены.
        assertThat(opener.replacements).isEqualTo(0)
        assertThat(opener.files.getValue(URI)!!.size).isEqualTo(content.size)
    }

    @Test
    fun `a book that cannot shrink is not rewritten`() = runBlocking {
        val optimized = checkNotNull(
            DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer()))
                .optimize("book.fb2", fixture()).bytes,
        )
        givenBook(extension = "fb2", content = optimized)

        val result = repository().optimizeBook(BOOK_ID).getOrNull()

        assertThat(result).isNotNull()
        assertThat(result!!.changed).isFalse()
        // Замены нет: переписывать файл без выигрыша незачем.
        assertThat(opener.replacements).isEqualTo(0)
    }

    @Test
    fun `a failed write restores the original bytes`() = runBlocking {
        val content = fixture()
        givenBook(extension = "fb2", content = content)
        opener.failingUris = setOf(URI)

        val result = repository().optimizeBook(BOOK_ID)

        assertThat(result.getOrNull()).isNull()
        // Две попытки записи: неудачная замена и откат исходными байтами.
        assertThat(opener.replacements).isEqualTo(2)
        assertThat(opener.files.getValue(URI)).isEqualTo(content)
    }

    @Test
    fun `optimization waits while the reader is open`() = runBlocking {
        givenBook(extension = "fb2", content = fixture())
        session.markOpened(BOOK_ID)

        val result = repository().optimizeBook(BOOK_ID)

        assertThat(result.errorOrNull()?.message).contains("Открыта читалка")
        assertThat(opener.replacements).isEqualTo(0)
    }

    private fun repository() = BookRepositoryImpl(
        bookDao = database.bookDao(),
        overrideDao = database.metadataOverrideDao(),
        readingProgressDao = database.readingProgressDao(),
        sourceRepository = FakeSourceRepository(),
        scanRepository = FakeScanRepository(),
        readerSession = session,
        duplicateFinder = DuplicateFinder(NoContentHashes),
        fileRemover = NoFileRemoval,
        clock = FakeAppClock(),
        authorWriter = BookAuthorWriter(database.authorDao()),
        bookCleanupDao = database.bookCleanupDao(),
        contentOpener = opener,
        fb2Optimizer = DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer())),
    )

    private suspend fun givenBook(extension: String, content: ByteArray) {
        opener.files[URI] = content
        database.bookDao().insert(
            BookEntity(
                id = BOOK_ID,
                sourceId = "source-1",
                documentUri = URI,
                relativePath = "Автор/Книга.$extension",
                fileName = "Книга.$extension",
                extension = extension,
                mimeType = "application/$extension",
                sizeBytes = content.size.toLong(),
                lastModified = 1_000L,
                title = "Книга",
                sortTitle = "Книга",
                authorsText = "Автор",
                addedAt = 1_000L,
                updatedAt = 1_000L,
                lastSeenAt = 1_000L,
            ),
        )
    }

    private fun fixture(): ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/book-small.fb2"),
    ) { "нет фикстуры book-small.fb2" }.use { stream -> stream.readBytes() }

    /** Хеши содержимого в этих тестах не нужны: дубликаты не ищутся. */
    private object NoContentHashes : io.github.bookshelf.core.storage.files.ContentHasher {
        override fun contentHash(documentUri: String): String? = null
    }

    /** Удаление и запись файлов в этих тестах не нужны: книга только оптимизируется. */
    private object NoFileRemoval : io.github.bookshelf.core.storage.saf.SafTreeWriter {

        override suspend fun write(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            bytes: ByteArray,
            mimeType: String,
        ): io.github.bookshelf.core.common.error.AppResult<String> = unused()

        override suspend fun writeStream(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            mimeType: String,
            onProgress: suspend (Long, Long) -> Unit,
            block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
        ): io.github.bookshelf.core.common.error.AppResult<String> = unused()

        override suspend fun delete(documentUri: String): io.github.bookshelf.core.common.error.AppResult<Unit> =
            unused()

        private fun unused(): Nothing = error("работа с файлами в этом тесте не нужна")
    }

    private companion object {
        const val BOOK_ID = "doc:book"
        const val URI = "content://tree/books/document/primary%3ABooks%2Fbook.fb2"
    }
}
