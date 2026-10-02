package io.github.bookshelf.core.data.repository.impl

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Фасет «источник» (папка) — запрос ИСПОЛНЯЕТСЯ, а не сверяется по строке.
 *
 * Этот тест появился не сам по себе: критерий папок был добавлен, тест на SQL проверял только
 * подстроки и был зелёным, а на устройстве фильтры показали «Не удалось загрузить фильтры» —
 * в таблице `sources` колонка называется `displayName`, а не `name`, и запрос падал при
 * исполнении. Поэтому здесь настоящая база: SQL обязан выполниться.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SourceFacetTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var filters: FilterRepository

    private val dispatchers = TestAppDispatchers()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookshelfDatabase::class.java,
        ).allowMainThreadQueries().build()
        filters = FilterRepositoryImpl(database.facetDao(), dispatchers)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `each folder is counted with its name`() = runTest(dispatchers.testDispatcher) {
        givenFolders()

        val sources = filters.observeFacets(BookQuery()).first().sources

        assertThat(sources.map { it.id }).containsExactly(SD_CARD, INTERNAL)
        assertThat(sources.map { it.labelCode }).containsExactly("SD", "Внутренняя память")
        assertThat(sources.first { it.id == SD_CARD }.count).isEqualTo(2)
        assertThat(sources.first { it.id == INTERNAL }.count).isEqualTo(1)
    }

    @Test
    fun `the chosen folder does not hide the other folders`() = runTest(dispatchers.testDispatcher) {
        givenFolders()

        // Свой фильтр фасет снимает: иначе второй источник было бы уже не выбрать.
        val sources = filters.observeFacets(BookQuery(sourceIds = setOf(SD_CARD))).first().sources

        assertThat(sources.map { it.id }).containsExactly(SD_CARD, INTERNAL)
        assertThat(sources.first { it.id == INTERNAL }.count).isEqualTo(1)
    }

    private suspend fun givenFolders() {
        database.sourceDao().insert(
            SourceEntity(
                id = SD_CARD,
                uri = "content://tree/sd",
                displayName = "SD",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.sourceDao().insert(
            SourceEntity(
                id = INTERNAL,
                uri = "content://tree/internal",
                displayName = "Внутренняя память",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().upsertAll(
            listOf(
                book("one", SD_CARD),
                book("two", SD_CARD),
                book("three", INTERNAL),
            ),
        )
    }

    private fun book(id: String, sourceId: String) = BookEntity(
        id = id,
        sourceId = sourceId,
        documentUri = "content://documents/$id.fb2",
        relativePath = "$id.fb2",
        fileName = "$id.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1_000L,
        lastModified = 1_000L,
        title = "Книга $id",
        sortTitle = "Книга $id",
        authorsText = "",
        addedAt = 1_000L,
        updatedAt = 1_000L,
        lastSeenAt = 1_000L,
    )

    private companion object {
        const val SD_CARD = "source:sd"
        const val INTERNAL = "source:internal"
    }
}
