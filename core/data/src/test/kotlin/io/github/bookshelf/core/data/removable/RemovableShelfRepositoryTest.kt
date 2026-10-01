package io.github.bookshelf.core.data.removable

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.data.repository.impl.SourceRepositoryImpl
import io.github.bookshelf.core.data.sync.ScanScheduler
import io.github.bookshelf.core.storage.saf.DocumentEntry
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.storage.saf.SafTreeReader
import io.github.bookshelf.core.storage.volume.RemovableVolume
import io.github.bookshelf.core.storage.volume.RemovableVolumeMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Полка съёмного носителя (`RemovableShelfRepositoryImpl`).
 *
 * Поведение, которое видит человек (требование Александра 01.10.2026): вставил карту с книгами —
 * полка появилась с числом книг; вынул — полка исчезла, а книги с карты пропали с полки, но
 * **не удалились**; вставил снова — книги вернулись. Плюс два решения: настройка выключает полку
 * целиком, а источник заводится сам только когда есть доступ ко всем файлам — иначе экран обязан
 * спросить человека.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class RemovableShelfRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private val monitor = FakeVolumeMonitor()
    private val settings = FakeSettingsRepository()
    private var hasAllFilesAccess = true
    private val safTreeReader = FakeSafTreeReader()
    private lateinit var repository: RemovableShelfRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sources = SourceRepositoryImpl(
            sourceDao = database.sourceDao(),
            bookDao = database.bookDao(),
            safTreeReader = safTreeReader,
            scanScheduler = FakeScanScheduler(),
            appClock = FakeAppClock(),
        )
        repository = RemovableShelfRepositoryImpl(
            monitor = monitor,
            settings = settings,
            sourceDao = database.sourceDao(),
            bookDao = database.bookDao(),
            sources = sources,
            allFilesAccess = { hasAllFilesAccess },
            clock = FakeAppClock(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `no medium means no shelf`() = runBlocking<Unit> {
        assertThat(repository.observeShelf().first()).isNull()
    }

    @Test
    fun `a medium without a source asks for access and counts nothing`() = runBlocking<Unit> {
        monitor.volumes.value = listOf(CARD)

        val shelf = repository.observeShelf().first()

        assertThat(shelf?.volume?.uuid).isEqualTo(CARD.uuid)
        assertThat(shelf?.sourceId).isNull()
        // Доступа ещё нет: экран обязан предложить его дать, а не показывать пустую полку молча.
        assertThat(shelf?.needsAccess).isTrue()
        assertThat(shelf?.bookCount).isEqualTo(0)
    }

    @Test
    fun `the shelf counts the books of its source`() = runBlocking<Unit> {
        monitor.volumes.value = listOf(CARD)
        givenSourceOfCard()
        givenBook("book-1", "card-source")
        givenBook("book-2", "card-source")

        val shelf = repository.observeShelf().first()

        assertThat(shelf?.sourceId).isEqualTo("card-source")
        assertThat(shelf?.needsAccess).isFalse()
        assertThat(shelf?.bookCount).isEqualTo(2)
    }

    @Test
    fun `the setting hides the shelf`() = runBlocking<Unit> {
        givenSourceOfCard()
        givenBook("book-1", "card-source")
        settings.library = LibrarySettings(showRemovableShelf = false)

        assertThat(repository.observeShelf().first()).isNull()
    }

    @Test
    fun `with all files access the source is registered without asking`() = runBlocking<Unit> {
        val result = repository.ensureSource(CARD)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val source = database.sourceDao().observeAll().first().single()
        assertThat(source.uri).isEqualTo(volumeTreeUri(CARD.uuid))
        assertThat(database.sourceDao().countAll()).isEqualTo(1)
    }

    @Test
    fun `without all files access the person is asked`() = runBlocking<Unit> {
        hasAllFilesAccess = false

        val result = repository.ensureSource(CARD)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        // Источник не заводится сам: без доступа он читался бы как недоступный и полка врала бы.
        assertThat(database.sourceDao().countAll()).isEqualTo(0)
    }

    @Test
    fun `an already registered source is not registered twice`() = runBlocking<Unit> {
        givenSourceOfCard()

        repository.ensureSource(CARD)

        assertThat(database.sourceDao().countAll()).isEqualTo(1)
    }

    @Test
    fun `ejecting the medium hides its books but keeps them`() = runBlocking<Unit> {
        givenSourceOfCard()
        givenBook("book-1", "card-source")

        repository.onEjected(CARD)

        val book = database.bookDao().getById("book-1")
        // Книга не удаляется: история чтения дороже дискового места (ADR-010).
        assertThat(book).isNotNull()
        assertThat(book?.isMissing).isTrue()
    }

    @Test
    fun `ejecting another medium does not touch our books`() = runBlocking<Unit> {
        givenSourceOfCard()
        givenBook("book-1", "card-source")

        repository.onEjected(RemovableVolume(uuid = "9Z9Z-9Z9Z", label = "Другая", path = "/storage/other"))

        assertThat(database.bookDao().getById("book-1")?.isMissing).isFalse()
    }

    @Test
    fun `the folder chosen by the person becomes the card source`() = runBlocking<Unit> {
        hasAllFilesAccess = false
        val picked = "${volumeTreeUri(CARD.uuid)}Books"

        val result = repository.grantTreeAccess(CARD, picked)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val source = database.sourceDao().observeAll().first().single()
        assertThat(source.uri).isEqualTo(picked)
        // Имя источника — метка носителя: в настройках человек читает «SD-карта», а не UUID.
        assertThat(source.displayName).isEqualTo(CARD.label)
    }

    @Test
    fun `a folder outside the card is refused`() = runBlocking<Unit> {
        hasAllFilesAccess = false

        val result = repository.grantTreeAccess(CARD, INTERNAL_FOLDER)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        // Папка встроенной памяти не даёт доступа к карте: заведённый источник полка носителя не
        // узнала бы, и после «выданного» разрешения она продолжала бы просить доступ.
        assertThat(database.sourceDao().countAll()).isEqualTo(0)
    }

    @Test
    fun `a refused permission is reported and nothing is written`() = runBlocking<Unit> {
        hasAllFilesAccess = false
        safTreeReader.persistable = AppResult.failure(AppError.PermissionDenied(message = "нет права"))

        val result = repository.grantTreeAccess(CARD, volumeTreeUri(CARD.uuid))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat(database.sourceDao().countAll()).isEqualTo(0)
    }

    private suspend fun givenSourceOfCard() {
        database.sourceDao().insert(
            SourceEntity(
                id = "card-source",
                uri = volumeTreeUri(CARD.uuid),
                displayName = "Карта памяти",
                kind = SourceKind.SAF_TREE,
            ),
        )
    }

    private suspend fun givenBook(id: String, sourceId: String) {
        database.bookDao().insert(
            BookEntity(
                id = id,
                sourceId = sourceId,
                documentUri = "content://tree/card/document/$id",
                relativePath = "$id.fb2",
                fileName = "$id.fb2",
                extension = "fb2",
                mimeType = "application/fb2",
                sizeBytes = 1_000L,
                lastModified = 1_000L,
                title = id,
                sortTitle = id,
                authorsText = "",
                addedAt = 1_000L,
                updatedAt = 1_000L,
                lastSeenAt = 1_000L,
            ),
        )
    }

    private class FakeVolumeMonitor : RemovableVolumeMonitor {
        val volumes = MutableStateFlow<List<RemovableVolume>>(emptyList())
        override fun observe(): Flow<List<RemovableVolume>> = volumes
    }

    private class FakeSettingsRepository : SettingsRepository {
        var library = LibrarySettings()
        override val settings: Flow<AppSettings> = flowOf(AppSettings(library = library))
        override suspend fun current(): AppSettings = AppSettings(library = library)
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
        override suspend fun resetToDefaults() = Unit
    }

    private class FakeScanScheduler : ScanScheduler {
        override suspend fun enqueueScan(sourceId: String, restart: Boolean) = Unit
        override suspend fun cancelScan(sourceId: String) = Unit
    }

    /** SAF-порт в тестах полки не читает дерево: проверяется регистрация источника. */
    private class FakeSafTreeReader : SafTreeReader {

        /** Ответ на [takePersistablePermission]: по умолчанию право берётся. */
        var persistable: AppResult<Unit> = AppResult.success(Unit)

        override suspend fun children(
            treeUri: String,
            parentDocumentId: String,
        ): AppResult<List<DocumentEntry>> = AppResult.success(emptyList())

        override suspend fun takePersistablePermission(
            treeUri: String,
            modeFlags: Int,
        ): AppResult<Unit> = persistable

        override fun hasPersistedPermission(treeUri: String): Boolean = false

        override fun hasWritePermission(treeUri: String): Boolean = false

        override fun documentUri(treeUri: String, documentId: String): String =
            "$treeUri/document/$documentId"

        override fun relativePath(treeUri: String, documentId: String): String =
            documentId.substringAfterLast('/')
    }

    private companion object {
        val CARD = RemovableVolume(uuid = "1A2B-3C4D", label = "SD-карта", path = "/storage/1A2B-3C4D")

        /** Папка встроенной памяти: UUID карты в адресе не встречается. */
        const val INTERNAL_FOLDER =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks"
    }
}
