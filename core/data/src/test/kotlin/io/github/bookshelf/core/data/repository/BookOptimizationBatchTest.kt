package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.TestAppDispatchers
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
 * Оптимизация всей библиотеки FB2 (`BookRepositoryImpl.optimizeAllFb2`).
 *
 * Проверяется то, что отличает пакетную работу от одиночной: перебираются только оптимизируемые
 * форматы, сбой одной книги не срывает остальные, а открытая читалка останавливает прогон —
 * и об этом честно сообщается, а не выдаётся за «оптимизировано всё».
 *
 * Фикстура настоящая: `book-small.fb2` собран из книги «Книга Уткина Мы тут жизнь живём».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookOptimizationBatchTest {

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
    fun `every fb2 book is optimized and savings are summed`() = runBlocking {
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = fixture())
        givenBook(id = SECOND_ID, uri = SECOND_URI, extension = "fb2", content = fixture())
        // Книга другого формата: её нельзя ни читать, ни заменять этим механизмом.
        givenBook(id = EPUB_ID, uri = EPUB_URI, extension = "epub", content = byteArrayOf(1, 2, 3))

        val batch = repository().optimizeAllFb2().getOrNull()

        assertThat(batch).isNotNull()
        assertThat(batch!!.total).isEqualTo(2)
        assertThat(batch.optimized).isEqualTo(2)
        assertThat(batch.savedBytes).isGreaterThan(0)
        // Заменены ровно две книги — FB2; EPUB не тронут.
        assertThat(opener.replacements).isEqualTo(2)
        assertThat(opener.files.getValue(EPUB_URI)).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a book without gain is counted as skipped`() = runBlocking {
        val already = checkNotNull(optimizer().optimize("book.fb2", fixture()).bytes)
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = already)

        val batch = repository().optimizeAllFb2().getOrNull()

        assertThat(batch!!.optimized).isEqualTo(0)
        assertThat(batch.skipped).isEqualTo(1)
        assertThat(batch.savedBytes).isEqualTo(0)
        assertThat(opener.replacements).isEqualTo(0)
    }

    @Test
    fun `the run stops when the reader is opened`() = runBlocking {
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = fixture())
        session.markOpened(FIRST_ID)

        val batch = repository().optimizeAllFb2().getOrNull()

        assertThat(batch!!.stoppedByReader).isTrue()
        assertThat(batch.optimized).isEqualTo(0)
        // Ни одной замены: работа уступает чтению целиком, а не наполовину.
        assertThat(opener.replacements).isEqualTo(0)
    }

    @Test
    fun `the run stops as soon as the reader is opened mid-way`() = runBlocking {
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = fixture())
        givenBook(id = SECOND_ID, uri = SECOND_URI, extension = "fb2", content = fixture())

        val batch = repository().optimizeAllFb2 { done, _ ->
            // Читалку открыли после первой книги: вторая обязана остаться нетронутой.
            if (done == 1) session.markOpened(FIRST_ID)
        }.getOrNull()

        assertThat(batch!!.stoppedByReader).isTrue()
        assertThat(batch.optimized).isEqualTo(1)
        assertThat(opener.replacements).isEqualTo(1)
    }

    @Test
    fun `progress is reported for every processed book`() = runBlocking {
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = fixture())
        givenBook(id = SECOND_ID, uri = SECOND_URI, extension = "fb2", content = fixture())
        val progress = mutableListOf<Pair<Int, Int>>()

        repository().optimizeAllFb2 { done, total -> progress += done to total }

        // Первое сообщение — «ещё ничего не сделано, но книг столько»: полоса прогресса обязана
        // получить общее число до первой книги, иначе она покажет пустоту.
        assertThat(progress).containsExactly(0 to 2, 1 to 2, 2 to 2).inOrder()
    }

    @Test
    fun `a failing book does not stop the others`() = runBlocking {
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = fixture())
        givenBook(id = SECOND_ID, uri = SECOND_URI, extension = "fb2", content = fixture())
        opener.failingUris = setOf(FIRST_URI)

        val batch = repository().optimizeAllFb2().getOrNull()

        assertThat(batch!!.failed).isEqualTo(1)
        assertThat(batch.optimized).isEqualTo(1)
        // Первая книга не изменилась (откат вернул исходные байты), вторая уменьшилась.
        assertThat(opener.files.getValue(FIRST_URI)).isEqualTo(fixture())
        assertThat(opener.files.getValue(SECOND_URI).size).isLessThan(fixture().size)
    }

    @Test
    fun `the size of the row is updated after a successful replacement`() = runBlocking {
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = fixture())

        val batch = repository().optimizeAllFb2().getOrNull()

        // Без этого полка и карточка показывали бы старый размер до следующего скана.
        assertThat(database.bookDao().getById(FIRST_ID)!!.sizeBytes).isEqualTo(batch!!.savedBytes.let {
            fixture().size.toLong() - it
        })
    }

    @Test
    fun `a book checked earlier is skipped, not read again`() = runBlocking {
        givenBook(id = FIRST_ID, uri = FIRST_URI, extension = "fb2", content = fixture())
        givenBook(
            id = SECOND_ID,
            uri = SECOND_URI,
            extension = "fb2",
            content = fixture(),
            optimizedAt = 1_000L,
        )

        val batch = repository().optimizeAllFb2().getOrNull()

        // В работу попадает только непроверенная книга: иначе повторное нажатие кнопки заново
        // пересобирало бы всю библиотеку.
        assertThat(batch!!.total).isEqualTo(1)
        assertThat(batch.alreadyOptimized).isEqualTo(1)
        assertThat(batch.optimized).isEqualTo(1)
        assertThat(opener.replacements).isEqualTo(1)
        // Второй файл даже не открывали.
        assertThat(opener.files.getValue(SECOND_URI)).isEqualTo(fixture())
    }

    @Test
    fun `a run with nothing left to check says so`() = runBlocking {
        givenBook(
            id = FIRST_ID,
            uri = FIRST_URI,
            extension = "fb2",
            content = fixture(),
            optimizedAt = 1_000L,
        )

        val batch = repository().optimizeAllFb2().getOrNull()

        assertThat(batch!!.total).isEqualTo(0)
        assertThat(batch.alreadyOptimized).isEqualTo(1)
        assertThat(opener.threads).isEmpty()
    }

    @Test
    fun `a library without fb2 reports zero candidates`() = runBlocking {
        givenBook(id = EPUB_ID, uri = EPUB_URI, extension = "epub", content = byteArrayOf(1, 2, 3))

        val batch = repository().optimizeAllFb2().getOrNull()

        assertThat(batch!!.total).isEqualTo(0)
        assertThat(batch.optimized).isEqualTo(0)
        assertThat(batch.stoppedByReader).isFalse()
        assertThat(opener.replacements).isEqualTo(0)
    }

    private fun optimizer() = DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer()))

    private fun repository() = BookRepositoryImpl(
        dispatchers = TestAppDispatchers(),
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
            bookCopyDao = database.bookCopyDao(),
        contentOpener = opener,
        fb2Optimizer = optimizer(),
    )

    private suspend fun givenBook(
        id: String,
        uri: String,
        extension: String,
        content: ByteArray,
        optimizedAt: Long? = null,
    ) {
        opener.files[uri] = content
        database.bookDao().insert(
            BookEntity(
                id = id,
                sourceId = "source-1",
                documentUri = uri,
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
                optimizedAt = optimizedAt,
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

    /** Удаление и запись файлов в этих тестах не нужны: книги только оптимизируются. */
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
        const val FIRST_ID = "doc:first"
        const val SECOND_ID = "doc:second"
        const val EPUB_ID = "doc:epub"
        const val FIRST_URI = "content://tree/books/document/primary%3ABooks%2Ffirst.fb2"
        const val SECOND_URI = "content://tree/books/document/primary%3ABooks%2Fsecond.fb2"
        const val EPUB_URI = "content://tree/books/document/primary%3ABooks%2Fthird.epub"
    }
}
