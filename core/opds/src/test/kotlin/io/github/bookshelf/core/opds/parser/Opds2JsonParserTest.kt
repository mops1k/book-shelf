package io.github.bookshelf.core.opds.parser

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.opds.model.isAcquisition
import io.github.bookshelf.core.opds.model.isNavigation
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Разбор OPDS 2.0 (JSON).
 *
 * Фикстуры — настоящие фиды публичного каталога Feedbooks (`catalog.feedbooks.com`): корневой
 * навигационный фид и фид с публикациями. Синтетический JSON писался бы «по моему же пониманию
 * формата» и всегда совпадал бы с парсером — правило проекта для фикстур.
 *
 * Проверяется то, что видит человек: разделы корня, книги с авторами, языком, описанием и
 * обложкой, ссылки приобретения и шаблон поиска, приведённый к нашему виду `{searchTerms}`.
 */
class Opds2JsonParserTest {

    private val parser = Opds2JsonParser()

    @Test
    fun `navigation feed becomes sections`() = runTest {
        val feed = parser.parse(navigationFeed(), BASE_URL).getOrNull()

        assertThat(feed).isNotNull()
        assertThat(feed?.title).isEqualTo("Cantook Market")
        assertThat(feed?.isNavigation).isTrue()
        // Разделы корня — навигационные записи со ссылками на подфиды.
        val sections = feed?.entries.orEmpty().filter { entry -> entry.links.any { it.isNavigation() } }
        assertThat(sections).hasSize(3)
        assertThat(sections.map { it.title }).contains("Fiction")
    }

    @Test
    fun `navigation entry links become absolute`() = runTest {
        val feed = parser.parse(navigationFeed(), BASE_URL).getOrNull()

        val fiction = feed?.entries.orEmpty().first { it.title == "Fiction" }
        val link = fiction.links.single()
        assertThat(link.type).isEqualTo("application/opds+json")
        assertThat(link.href).startsWith("https://catalog.feedbooks.com/")
    }

    @Test
    fun `group publications become books`() = runTest {
        val feed = parser.parse(navigationFeed(), BASE_URL).getOrNull()

        // Публикации из groups тоже книги: иначе корень с подборками показал бы только разделы.
        val books = feed?.entries.orEmpty().filterNot { entry -> entry.links.any { it.isNavigation() } }
        assertThat(books).hasSize(2)
        assertThat(books.map { it.title }).contains("What Happened to You?")
    }

    @Test
    fun `book feed entries carry metadata`() = runTest {
        val feed = parser.parse(booksFeed(), BASE_URL).getOrNull()

        assertThat(feed?.isNavigation).isFalse()
        val book = feed?.entries.orEmpty().first()
        assertThat(book.title).isEqualTo("What Happened to You?")
        assertThat(book.authors).containsExactly("Oprah Winfrey", "Bruce D. Perry").inOrder()
        assertThat(book.language).isEqualTo("en")
        assertThat(book.dcIdentifier).isEqualTo("urn:ISBN:9781250223210")
        // Год берётся из published: в карточке он показывается отдельной строкой.
        assertThat(book.issued).isEqualTo("2021")
        assertThat(book.genres).isNotEmpty()
        // Обложка: первая картинка записи, а не превью на 100 пикселей.
        assertThat(book.coverUrl).isEqualTo("https://covers.feedbooks.net/item/3952853.jpg?size=large&t=1789270291")
    }

    @Test
    fun `description is plain text without markup`() = runTest {
        val feed = parser.parse(booksFeed(), BASE_URL).getOrNull()

        val summary = feed?.entries.orEmpty().first().summary.orEmpty()
        assertThat(summary).isNotEmpty()
        // В фиде описание — HTML; в карточке теги были бы мусором.
        assertThat(summary).doesNotContain("<")
        assertThat(summary).doesNotContain("&nbsp;")
    }

    @Test
    fun `acquisition links are recognised`() = runTest {
        val feed = parser.parse(booksFeed(), BASE_URL).getOrNull()

        val links = feed?.entries.orEmpty().first().links
        val acquisition = links.filter { it.isAcquisition() }
        // В фиде Feedbooks это rel вида `.../acquisition/sample`: распознаётся по префиксу.
        assertThat(acquisition.map { it.type }).contains("application/epub+zip")
        assertThat(acquisition.map { it.href }).contains("https://catalog.feedbooks.com/item/3952853/preview")
    }

    @Test
    fun `search template is normalised for our search`() = runTest {
        val feed = parser.parse(booksFeed(), BASE_URL).getOrNull()

        // OPDS 2.0 объявляет шаблон как `{?query}`, наш поиск подставляет `{searchTerms}`.
        assertThat(feed?.searchUrl)
            .isEqualTo("https://catalog.feedbooks.com/search.json?query={searchTerms}")
    }

    @Test
    fun `next page link is taken from rel next`() = runTest {
        // Ссылку `next` добавляем в настоящий фид руками: в самих фидах Feedbooks пагинации нет,
        // а поведение парсера проверить нужно.
        val nextLink = "\"rel\": \"next\", \"href\": \"$NEXT_PAGE\", \"type\": \"application/opds+json\"}, " +
            "{\"rel\": \"search\""
        val withNext = booksFeed().replace("\"rel\": \"search\"", nextLink)

        val feed = parser.parse(withNext, BASE_URL).getOrNull()

        assertThat(feed?.nextPageUrl).isEqualTo(NEXT_PAGE)
    }

    @Test
    fun `relative links are resolved against the feed address`() = runTest {
        // Адрес в подставном документе относительный — как в реальных каталогах за прокси.
        val absolute = "\"href\": \"https://catalog.feedbooks.com/item/3952853/preview\""
        val relative = booksFeed().replace(absolute, "\"href\": \"/item/3952853/preview\"")

        val feed = parser.parse(relative, BASE_URL).getOrNull()

        val href = feed?.entries.orEmpty().first().links
            .first { it.isAcquisition() && it.type == "application/epub+zip" }
            .href
        assertThat(href).isEqualTo("https://catalog.feedbooks.com/item/3952853/preview")
    }

    @Test
    fun `broken json is a failure instead of an exception`() = runTest {
        val result = parser.parse("{ это не json", BASE_URL)

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()?.message).isNotNull()
    }

    private fun navigationFeed(): String = resource("feedbooks-nav.json")

    private fun booksFeed(): String = resource("feedbooks-books.json")

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "нет фикстуры $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        const val BASE_URL = "https://catalog.feedbooks.com/catalog/index.json"
        const val NEXT_PAGE = "https://catalog.feedbooks.com/featured.json?page=2"
    }
}
