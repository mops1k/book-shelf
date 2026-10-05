package io.github.bookshelf.core.data.repository.impl

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.common.testing.RecordingAppLogger
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.FormatDefaultEntity
import io.github.bookshelf.core.database.entity.ReaderProfileEntity
import io.github.bookshelf.core.readerlink.discovery.InstalledReader
import io.github.bookshelf.core.readerlink.discovery.ReaderDiscovery
import io.github.bookshelf.core.readerlink.launch.LaunchRequest
import io.github.bookshelf.core.readerlink.launch.ReaderExport
import io.github.bookshelf.core.readerlink.launch.ReaderLauncher
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSources
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Удалённой читалки в приложении не видно нигде.
 *
 * Требование Александра (06.10.2026): «если читалка удалена с устройства, её в приложении нигде не
 * должно быть видно». Наблюдалось на устройстве: после удаления FBReader Premium и xReaderPro они
 * остались в списке «Читалки по умолчанию», потому что профиль читалки живёт в базе и отдавался
 * наружу независимо от того, есть ли читалка в системе.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class UninstalledReaderTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: ReaderRepository
    private val discovery = FixedDiscovery(listOf(reader(COOL_READER)))

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookshelfDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ReaderRepositoryImpl(
            discovery = discovery,
            launcher = NoLauncher,
            export = NoExport,
            profileDao = database.readerProfileDao(),
            bookDao = database.bookDao(),
            formatDefaultDao = database.formatDefaultDao(),
            session = NoSession,
            clock = FakeAppClock(),
            addressChooser = LaunchAddressChooser(RecordingAppLogger()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a profile of an uninstalled reader is not listed`() = runTest {
        database.readerProfileDao().upsertAll(
            listOf(profileEntity(COOL_READER), profileEntity(GONE_READER)),
        )

        val readers = repository.installedReaders().first()

        assertThat(readers.map { it.packageName }).containsExactly(COOL_READER)
    }

    @Test
    fun `a profile of an uninstalled reader is removed from the database`() = runTest {
        database.readerProfileDao().upsertAll(
            listOf(profileEntity(COOL_READER), profileEntity(GONE_READER)),
        )

        repository.installedReaders().first()

        // Строку вычищаем: иначе при следующем чтении из базы читалка снова появится в списке.
        assertThat(database.readerProfileDao().getById(GONE_READER)).isNull()
        assertThat(database.readerProfileDao().getById(COOL_READER)).isNotNull()
    }

    @Test
    fun `a binding to an uninstalled reader is not reported`() = runTest {
        database.formatDefaultDao().upsert(FormatDefaultEntity(format = "epub", profileId = GONE_READER))

        assertThat(repository.defaultForFormat("epub").first()).isNull()
        assertThat(database.formatDefaultDao().getByFormat("epub")).isNull()
    }

    @Test
    fun `a binding to an installed reader is kept`() = runTest {
        database.formatDefaultDao().upsert(FormatDefaultEntity(format = "epub", profileId = COOL_READER))

        assertThat(repository.defaultForFormat("epub").first()).isEqualTo(COOL_READER)
    }

    private fun profileEntity(packageName: String) = ReaderProfileEntity(
        id = packageName,
        name = packageName,
        packageName = packageName,
        activityName = null,
        mimeTypes = "",
        supportsContentUri = true,
        extrasJson = null,
        isBuiltIn = false,
        enabled = true,
        priority = 0,
    )

    private fun reader(packageName: String) = InstalledReader(
        profile = ReaderProfile(
            id = packageName,
            name = packageName,
            packageName = packageName,
            activityName = null,
            mimeTypes = listOf("application/epub+zip"),
            supportsContentUri = true,
            extrasJson = null,
            isBuiltIn = false,
        ),
        resolveInfoLike = "$packageName.MainActivity",
    )

    private class FixedDiscovery(private val readers: List<InstalledReader>) : ReaderDiscovery {
        override suspend fun installedReaders(): List<InstalledReader> = readers
    }

    private object NoLauncher : ReaderLauncher {
        override suspend fun launch(request: LaunchRequest): AppResult<Unit> =
            AppResult.failure(io.github.bookshelf.core.common.error.AppError.Unknown())
    }

    private object NoExport : ReaderExport {
        override suspend fun exportedReaderUri(documentUri: String, extension: String): AppResult<Uri> =
            AppResult.failure(io.github.bookshelf.core.common.error.AppError.Unknown())
    }

    private object NoSession : ReaderSession {
        override fun markOpened(bookId: String, readerProfileId: String?) = Unit

        override fun isActive(): Boolean = false

        override fun clear() = Unit

        override fun onAppForeground() = Unit

        override fun onAppBackground() = Unit
    }

    private companion object {
        const val COOL_READER = "org.coolreader"
        const val GONE_READER = "com.fbreader"
    }
}
