package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import io.github.bookshelf.core.database.model.OpdsDownloadState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Очистка истории загрузок OPDS.
 *
 * Требование Александра 30.09.2026: на экране загрузок нужна кнопка «Очистить историю».
 * Проверяется главное свойство: убираются только терминальные записи, а активная загрузка остаётся
 * — её удаление оставило бы работающую работу без строки, куда писать прогресс и результат.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class OpdsDownloadHistoryTest {

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
    fun `clearing history keeps active downloads`() = runBlocking {
        val dao = database.opdsDownloadDao()
        dao.upsert(download("done", OpdsDownloadState.DONE))
        dao.upsert(download("failed", OpdsDownloadState.FAILED))
        dao.upsert(download("cancelled", OpdsDownloadState.CANCELLED))
        dao.upsert(download("running", OpdsDownloadState.RUNNING))
        dao.upsert(download("queued", OpdsDownloadState.QUEUED))

        val removed = dao.deleteFinished()

        assertThat(removed).isEqualTo(3)
        assertThat(dao.getById("done")).isNull()
        assertThat(dao.getById("failed")).isNull()
        assertThat(dao.getById("cancelled")).isNull()
        assertThat(dao.getById("running")).isNotNull()
        assertThat(dao.getById("queued")).isNotNull()
    }

    @Test
    fun `clearing an empty history removes nothing`() = runBlocking {
        val dao = database.opdsDownloadDao()
        dao.upsert(download("running", OpdsDownloadState.RUNNING))

        assertThat(dao.deleteFinished()).isEqualTo(0)
        assertThat(dao.getById("running")).isNotNull()
    }

    private fun download(id: String, state: OpdsDownloadState) = OpdsDownloadEntity(
        id = id,
        catalogId = "catalog-1",
        entryId = "entry-$id",
        title = "Книга $id",
        downloadUrl = "https://host/book-$id.fb2",
        targetSourceId = "source-1",
        targetRelativeDir = "",
        state = state,
        createdAt = 1L,
    )
}
