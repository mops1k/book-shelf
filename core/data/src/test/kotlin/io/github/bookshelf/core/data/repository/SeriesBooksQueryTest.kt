package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import io.github.bookshelf.core.fb2opt.DefaultFb2Optimizer
import io.github.bookshelf.core.fb2opt.Fb2ImageOptimizer
import io.github.bookshelf.core.fb2opt.JpegEntropyOptimizer
import io.github.bookshelf.core.storage.files.ContentHasher
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import io.github.bookshelf.core.testing.fake.FakeBookContentOpener
import io.github.bookshelf.core.testing.fake.FakeReaderSession
import io.github.bookshelf.core.testing.fake.FakeScanRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Книги серии для карточки книги.
 *
 * Требование Александра (08.10.2026): в карточке — стрелки «назад/вперёд» по книгам той же серии.
 * Порядок соседей обязан совпадать со списком серии на полке: с номером по возрастанию, без номера —
 * по году и названию (`BookPageSql.SERIES_ORDER`). Проверяется на настоящем SQLite, а не на строке
 * запроса: ошибка в порядке видна только на данных.
 *
 * Отдельный класс, а не ещё один тест в `BookRepositoryTest`: тот уже на пределе detekt `LargeClass`,
 * и выносить туда новую проверку значило бы раздувать его дальше.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SeriesBooksQueryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: BookRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepositoryImpl(
            dispatchers = TestAppDispatchers(),
            bookDao = database.bookDao(),
            overrideDao = database.metadataOverrideDao(),
            readingProgressDao = database.readingProgressDao(),
            sourceRepository = FakeSourceRepository(),
            scanRepository = FakeScanRepository(),
            readerSession = FakeReaderSession(),
            duplicateFinder = DuplicateFinder(NoContentHashes),
            fileRemover = NoFileRemover,
            clock = FakeAppClock(),
            authorWriter = BookAuthorWriter(database.authorDao()),
            bookCleanupDao = database.bookCleanupDao(),
            bookCopyDao = database.bookCopyDao(),
            contentOpener = FakeBookContentOpener(),
            fb2Optimizer = DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer())),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun series_books_come_in_reading_order_and_only_from_that_series() = runBlocking {
        givenSource()
        database.bookDao().upsertAll(
            listOf(
                book("ksi2", "Ксили. Книга 2", "Ксили", year = 2000, index = 2.0),
                book("ksiNoNumber", "Ксили. Без номера", "Ксили", year = 2010),
                book("ksi1", "Ксили. Книга 1", "Ксили", year = 1999, index = 1.0),
                book("other", "Чужая книга", "Другая серия", year = 1998, index = 1.0),
            ),
        )

        val titles = repository.observeSeriesBooks("Ксили").first().map { it.title }

        // Стрелки «назад/вперёд» в карточке ходят в том же порядке, что и список серии на полке:
        // с номером — по возрастанию, без номера — в конец. Книга другой серии сюда не попадает.
        assertThat(titles).isEqualTo(listOf("Ксили. Книга 1", "Ксили. Книга 2", "Ксили. Без номера"))
    }

    private suspend fun givenSource() {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
    }

    private fun book(
        id: String,
        title: String,
        series: String,
        year: Int,
        index: Double? = null,
    ) = BookEntity(
        id = id,
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$id",
        relativePath = "$title.fb2",
        fileName = "$title.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1_000L,
        lastModified = 1_000L,
        title = title,
        sortTitle = title,
        authorsText = "Стивен Бакстер",
        seriesName = series,
        seriesIndex = index,
        publishedYear = year,
        addedAt = 1_000L,
        updatedAt = 1_000L,
        lastSeenAt = 1_000L,
    )

    /** Файлы и хеши в этой проверке не участвуют: свои интерфейсы — значит фейки (ADR-012). */
    private object NoContentHashes : ContentHasher {
        override fun contentHash(documentUri: String): String = documentUri
    }

    private object NoFileRemover : SafTreeWriter {
        override suspend fun write(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            bytes: ByteArray,
            mimeType: String,
        ): AppResult<String> = AppResult.success("content://doc/fake")

        override suspend fun writeStream(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            mimeType: String,
            onProgress: suspend (bytesWritten: Long, total: Long) -> Unit,
            block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
        ): AppResult<String> = AppResult.success("content://doc/fake")

        override suspend fun delete(documentUri: String): AppResult<Unit> = AppResult.success(Unit)
    }

    private companion object {
        const val SOURCE_ID = "source-1"
    }
}
