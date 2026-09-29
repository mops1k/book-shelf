package io.github.bookshelf.core.data.repository.impl

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.TagEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Категории обзора авторов — ТОЧНЫЕ наборы соавторов.
 *
 * Здесь проверяется то, ради чего авторы стали связями: пара соавторов в другом порядке — та же
 * категория (дефект Александра, 29.09.2026: «A, B» и «B, A» показывались как разные авторы), а
 * книга с третьим автором в эту категорию не попадает — иначе число в категории разошлось бы с её
 * содержимым.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AuthorSetsFacetTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var filters: FilterRepository

    /**
     * Диспетчеры: `metadata` — это `StandardTestDispatcher`, поэтому тесты идут под `runTest`.
     * В `runBlocking` его никто не продвигает, и `first()` на потоке фасета зависает навсегда.
     */
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
    fun `co-authors written in another order form one category`() = runTest(dispatchers.testDispatcher) {
        givenLibrary(withThirdAuthor = false)

        val sets = filters.observeAuthorSets(BookQuery()).first()

        // Одна категория на двоих, порядок имён в файлах не важен.
        assertThat(sets.map { it.label }).containsExactly("Александр Тарарев, Юрий Тарарев")
        assertThat(sets.single().count).isEqualTo(2)
        assertThat(sets.single().names).containsExactly("Александр Тарарев", "Юрий Тарарев")
    }

    @Test
    fun `a book with a third author is a separate category`() = runTest(dispatchers.testDispatcher) {
        givenLibrary(withThirdAuthor = true)

        val sets = filters.observeAuthorSets(BookQuery()).first()

        // «A, B, C» — своя категория: в «A, B» она попасть не должна.
        val labels = sets.map { it.label }
        assertThat(labels).contains("Александр Тарарев, Юрий Тарарев")
        assertThat(labels).contains("Александр Тарарев, Пётр Третий, Юрий Тарарев")
        assertThat(sets.first { it.names.size == 2 }.count).isEqualTo(2)
    }

    @Test
    fun `series of an author set are found by exact set`() = runTest(dispatchers.testDispatcher) {
        givenLibrary(withThirdAuthor = false)

        // Серии набора: «у соавторов нет серий» — дефект с устройства (29.09.2026). Проверяем на
        // уровне данных, чтобы отделить SQL от экрана.
        val series = filters.observeAuthorSetSeries(
            authorNames = listOf("Александр Тарарев", "Юрий Тарарев"),
            genreCode = null,
        ).first()

        assertThat(series.series.map { it.id }).containsExactly("Приключения")
        assertThat(series.series.single().count).isEqualTo(2)
    }

    @Test
    fun `series of an author set are found inside a genre`() = runTest(dispatchers.testDispatcher) {
        givenLibrary(withThirdAuthor = false)
        database.tagDao().upsertAll(
            listOf(
                TagEntity(id = "tag:sf_space", name = "sf_space"),
                TagEntity(id = "tag:sf_action", name = "sf_action"),
            ),
        )
        database.tagDao().insertBookTags(
            listOf(
                BookTagCrossRef(bookId = "one", tagId = "tag:sf_space"),
                BookTagCrossRef(bookId = "two", tagId = "tag:sf_space"),
                BookTagCrossRef(bookId = "three", tagId = "tag:sf_action"),
            ),
        )

        // Дефект с устройства (29.09.2026): из жанра у набора авторов не показывалось ни одной серии.
        // Причина была в запросе: у условия жанра два плейсхолдера, а параметр передавался один.
        val series = filters.observeAuthorSetSeries(
            authorNames = listOf("Александр Тарарев", "Юрий Тарарев"),
            genreCode = "sf_space",
        ).first()

        assertThat(series.series.map { it.id }).containsExactly("Приключения")
        assertThat(series.series.single().count).isEqualTo(2)
    }

    private suspend fun givenLibrary(withThirdAuthor: Boolean) {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE,
                uri = "content://tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().upsertAll(
            buildList {
                add(book("one", "Первый сборник", "Александр Тарарев, Юрий Тарарев"))
                add(book("two", "Второй сборник", "Юрий Тарарев, Александр Тарарев"))
                if (withThirdAuthor) {
                    add(book("three", "Третий сборник", "Юрий Тарарев, Александр Тарарев, Пётр Третий"))
                }
            },
        )
        database.authorDao().upsertAll(
            listOf(
                AuthorEntity(id = "author:alexander", name = "Александр Тарарев", sortName = "Тарарев Александр"),
                AuthorEntity(id = "author:yury", name = "Юрий Тарарев", sortName = "Тарарев Юрий"),
                AuthorEntity(id = "author:peter", name = "Пётр Третий", sortName = "Третий Пётр"),
            ),
        )
        database.authorDao().insertBookAuthors(
            buildList {
                add(BookAuthorCrossRef(bookId = "one", authorId = "author:alexander", position = 0))
                add(BookAuthorCrossRef(bookId = "one", authorId = "author:yury", position = 1))
                add(BookAuthorCrossRef(bookId = "two", authorId = "author:yury", position = 0))
                add(BookAuthorCrossRef(bookId = "two", authorId = "author:alexander", position = 1))
                if (withThirdAuthor) {
                    add(BookAuthorCrossRef(bookId = "three", authorId = "author:yury", position = 0))
                    add(BookAuthorCrossRef(bookId = "three", authorId = "author:alexander", position = 1))
                    add(BookAuthorCrossRef(bookId = "three", authorId = "author:peter", position = 2))
                }
            },
        )
    }

    private fun book(
        id: String,
        title: String,
        authorsText: String,
        seriesName: String? = "Приключения",
    ) = BookEntity(
        seriesName = seriesName,
        id = id,
        sourceId = SOURCE,
        documentUri = "content://documents/$id.fb2",
        relativePath = "$title.fb2",
        fileName = "$id.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1_000L,
        lastModified = 1_000L,
        title = title,
        sortTitle = title,
        authorsText = authorsText,
        addedAt = 1L,
        updatedAt = 1L,
        lastSeenAt = 1L,
    )

    private companion object {
        const val SOURCE = "source-1"
    }
}
