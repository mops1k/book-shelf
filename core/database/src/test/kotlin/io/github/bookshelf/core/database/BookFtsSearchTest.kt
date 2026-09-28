package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.dao.SearchDao
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Полнотекстовый поиск по FTS4 (docs/architecture.md §3.2).
 *
 * Тест гоняется на **API 24** намеренно. `minSdk` проекта — 24, а системный SQLite там заметно
 * старее того, что стоит на стендах разработки. KDoc сущности раньше сомневался, поддерживает ли
 * этот SQLite `remove_diacritics=2`, и оставлял вопрос открытым — теперь вопрос закрыт фактами:
 * Room 2.6.1 рендерит аргумент как `tokenize=unicode61 \`remove_diacritics=2\``, SQLite такую форму
 * принимает, а параметр не меняет результат ни в одной из форм. Закрепляем поведение, чтобы
 * смена версии Room или SQLite его не тихо сломала.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookFtsSearchTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var search: SearchDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        search = database.searchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun finds_a_book_by_cyrillic_author() = runBlocking {
        given("Война и мир", "Лев Толстой")

        val found = search.search("толстой", LIMIT).first()

        assertThat(found.map { it.title }).isEqualTo(listOf("Война и мир"))
    }

    @Test
    fun finds_a_book_by_cyrillic_title() = runBlocking {
        given("Анна Каренина", "Лев Толстой")

        val found = search.search("каренина", LIMIT).first()

        assertThat(found).hasSize(1)
    }

    @Test
    fun autocomplete_matches_a_word_prefix() = runBlocking {
        given("Война и мир", "Лев Толстой")

        // Последнее слово получает `*` — так работает префиксный поиск FTS4.
        val found = search.search("вой*", LIMIT).first()

        assertThat(found.map { it.title }).isEqualTo(listOf("Война и мир"))
    }

    @Test
    fun suggest_titles_returns_only_titles() = runBlocking {
        given("Война и мир", "Лев Толстой")

        val suggestions = search.suggestTitles("толс*", LIMIT).first()

        assertThat(suggestions).isEqualTo(listOf("Война и мир"))
    }

    @Test
    fun does_not_match_a_middle_of_a_word() = runBlocking {
        given("Война и мир", "Лев Толстой")

        // Без префиксной звёздочки FTS4 ищет токен целиком: «ойна» — не токен.
        assertThat(search.search("ойна", LIMIT).first()).isEmpty()
    }

    @Test
    fun finds_a_book_by_two_words_without_the_and_token() = runBlocking {
        given("The Steel Remains", "Richard Morgan")

        // Форма запроса, которую отдаёт ftsQueryOf: слова через пробел (неявное «и»).
        // Живой дефект на устройстве: «steel AND remains» не находил ничего, потому что в SQLite
        // на Android `AND` — обычный токен, а слова «and» в названии нет.
        val found = search.search("steel remains", LIMIT).first()

        assertThat(found.map { it.title }).isEqualTo(listOf("The Steel Remains"))
    }

    @Test
    fun returns_nothing_when_nothing_matches() = runBlocking {
        given("Война и мир", "Лев Толстой")

        // Термин, которого нет ни в заголовке, ни в имени автора, ни в имени файла.
        assertThat(search.search("пушкин*", LIMIT).first()).isEmpty()
    }

    private suspend fun given(title: String, author: String) {
        database.sourceDao().insert(SOURCE)
        database.bookDao().upsertAll(listOf(book(title, author)))
    }

    private fun book(title: String, author: String) = BookEntity(
        id = "book-$title",
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$title",
        relativePath = "$title.fb2",
        fileName = "$title.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1024L,
        lastModified = 1000L,
        title = title,
        sortTitle = title,
        authorsText = author,
        addedAt = 1000L,
        updatedAt = 1000L,
        lastSeenAt = 1000L,
    )

    private companion object {
        const val LIMIT = 20
        const val SOURCE_ID = "source-1"

        val SOURCE = SourceEntity(
            id = SOURCE_ID,
            uri = "content://com.android.externalstorage.documents/tree/books",
            displayName = "Books",
            kind = SourceKind.SAF_TREE,
        )
    }
}
