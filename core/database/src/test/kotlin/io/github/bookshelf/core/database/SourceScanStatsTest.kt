package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
 * Итог сканирования записывается в сам источник.
 *
 * Дефект, ради которого появился тест (найден на живом устройстве 2026-09-28): `sources.lastScanAt`
 * не обновлял никто, поэтому настройки показывали «ещё не сканировалось» даже после успешного
 * скана — а без этой пары полей нельзя отличить «0 книг» от «скана ещё не было».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SourceScanStatsTest {

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
    fun scan_stats_are_written_to_the_source() = runBlocking {
        val sourceDao = database.sourceDao()
        sourceDao.insert(FIRST_SOURCE)

        assertThat(sourceDao.getById(FIRST_SOURCE.id)?.lastScanAt).isNull()
        assertThat(sourceDao.getById(FIRST_SOURCE.id)?.documentCount).isEqualTo(0)

        sourceDao.updateScanStats(id = FIRST_SOURCE.id, at = SCANNED_AT, documents = BOOKS_FOUND)

        val stored = sourceDao.getById(FIRST_SOURCE.id)
        assertThat(stored?.lastScanAt).isEqualTo(SCANNED_AT)
        assertThat(stored?.documentCount).isEqualTo(BOOKS_FOUND)
    }

    @Test
    fun scan_stats_of_one_source_do_not_touch_another() = runBlocking {
        val sourceDao = database.sourceDao()
        sourceDao.insert(FIRST_SOURCE)
        sourceDao.insert(SECOND_SOURCE)

        sourceDao.updateScanStats(id = FIRST_SOURCE.id, at = SCANNED_AT, documents = BOOKS_FOUND)

        val untouched = sourceDao.getById(SECOND_SOURCE.id)
        assertThat(untouched?.lastScanAt).isNull()
        assertThat(untouched?.documentCount).isEqualTo(0)
    }

    private companion object {

        const val SCANNED_AT = 1_700_000_000_000L

        const val BOOKS_FOUND = 72

        val FIRST_SOURCE = SourceEntity(
            id = "source-1",
            uri = "content://com.android.externalstorage.documents/tree/primary%3ABooks",
            displayName = "Books",
            kind = SourceKind.SAF_TREE,
        )

        val SECOND_SOURCE = SourceEntity(
            id = "source-2",
            uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
            displayName = "Download",
            kind = SourceKind.SAF_TREE,
        )
    }
}
