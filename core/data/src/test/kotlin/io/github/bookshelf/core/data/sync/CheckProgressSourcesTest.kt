package io.github.bookshelf.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.ReadingProgressOrigins
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSources
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import io.github.bookshelf.core.data.model.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Диагностика источников прогресса (`CheckProgressSources`).
 *
 * Отвечает на вопрос «включил, а процента нет»: какие из **включённых** источников читаются сейчас
 * и чей прогресс записан последним. Выключенные источники в отчёт не попадают — их никто не читает.
 * Ошибка подключения одного источника не должна ломать отчёт целиком: у чужого файла может не быть
 * доступа ко всей памяти, и это как раз то, что человек хочет увидеть.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class CheckProgressSourcesTest {

    private lateinit var database: BookshelfDatabase
    private val books = FakeBookRepository()
    private val settings = FakeSettingsRepository()
    private val available = FakeSource(ProgressSourceId.KOREADER_SIDECAR, available = true)
    private val missing = FakeSource(ProgressSourceId.COOLREADER_DB, available = false)
    private val broken = FakeSource(ProgressSourceId.LIBRERA_JSON, broken = true)

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
    fun `every switched-on source is asked and reported`() = runBlocking {
        settings.reader = ReaderSettings(enableKoReaderProgress = true, enableCoolReaderProgress = true)

        val report = check().check().getOrNull()

        assertThat(report?.sources).containsExactly(
            ProgressSourceStatus(sourceKey = ProgressSourceId.KOREADER_SIDECAR.storageKey, available = true),
            ProgressSourceStatus(sourceKey = ProgressSourceId.COOLREADER_DB.storageKey, available = false),
        )
        assertThat(missing.connectCalls).isEqualTo(1)
        // Чужой файл закрываем даже тогда, когда он не открылся: иначе привязка осталась бы висеть.
        assertThat(missing.disconnectCalls).isEqualTo(1)
    }

    @Test
    fun `a switched-off source is not asked at all`() = runBlocking {
        settings.reader = ReaderSettings(enableKoReaderProgress = true)

        val report = check().check().getOrNull()

        assertThat(report?.sources).hasSize(1)
        assertThat(missing.connectCalls).isEqualTo(0)
    }

    @Test
    fun `a source that throws is reported as unavailable`() = runBlocking {
        settings.reader = ReaderSettings(enableLibreraProgress = true)

        val report = check().check().getOrNull()

        assertThat(report?.sources?.single()?.available).isFalse()
        assertThat(broken.disconnectCalls).isEqualTo(1)
    }

    @Test
    fun `the last recorded progress names its source and book`() = runBlocking {
        settings.reader = ReaderSettings(enableKoReaderProgress = true)
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = BOOK_ID,
                profileId = null,
                percent = 0.42f,
                locatorJson = null,
                updatedAt = 5_000L,
                source = ProgressSourceId.KOREADER_SIDECAR.storageKey,
            ),
        )
        books.bookById = AppResult.success(
            Book(
                id = BOOK_ID,
                title = "Солярис",
                authorsText = "Станислав Лем",
                extension = "fb2",
                sizeBytes = 1_000L,
                addedAt = 1_000L,
            ),
        )
        // Старая запись другой книги: победить должна свежая, а не последняя вставленная.
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = "old",
                profileId = null,
                percent = 0.1f,
                locatorJson = null,
                updatedAt = 1_000L,
                source = ReadingProgressOrigins.FBREADER_AIDL,
            ),
        )

        val report = check().check().getOrNull()

        assertThat(report?.lastSourceKey).isEqualTo(ProgressSourceId.KOREADER_SIDECAR.storageKey)
        assertThat(report?.lastBookTitle).isEqualTo("Солярис")
        assertThat(report?.lastUpdatedAt).isEqualTo(5_000L)
    }

    @Test
    fun `an empty library gives no last progress`() = runBlocking {
        settings.reader = ReaderSettings(enableKoReaderProgress = true)

        val report = check().check().getOrNull()

        assertThat(report?.lastSourceKey).isNull()
        assertThat(report?.lastBookTitle).isNull()
    }

    private fun check() = CheckProgressSources(
        sources = ReadingProgressSources(setOf(available, missing, broken)),
        settings = settings,
        progress = database.readingProgressDao(),
        books = books,
    )

    /** Источник-заглушка: свои интерфейсы не мокаются (ADR-012), поэтому фейк маленький и явный. */
    private class FakeSource(
        override val id: ProgressSourceId,
        private val available: Boolean = true,
        private val broken: Boolean = false,
    ) : ReadingProgressSource {

        override val knowsOpenBook: Boolean = false
        var connectCalls: Int = 0
        var disconnectCalls: Int = 0

        override suspend fun connect(): Boolean {
            connectCalls++
            if (broken) error("чужой файл не читается")
            return available
        }

        override suspend fun disconnect() {
            disconnectCalls++
        }

        override suspend fun openedBook(): ReaderBookAddress? = null

        override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> =
            AppResult.failure(AppError.NotFound(what = "прогресс"))
    }

    private class FakeSettingsRepository : SettingsRepository {

        var reader: ReaderSettings = ReaderSettings()

        override val settings: Flow<AppSettings> = flowOf(AppSettings(reader = reader))

        override suspend fun current(): AppSettings = AppSettings(reader = reader)

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

        override suspend fun resetToDefaults() = Unit
    }

    private companion object {
        const val BOOK_ID = "book-1"
    }
}
