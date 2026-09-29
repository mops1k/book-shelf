package io.github.bookshelf.core.opds.parser

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Выбор парсера по телу ответа: OPDS 1.2 (Atom XML) или OPDS 2.0 (JSON).
 *
 * Каталоги обоих видов живут в одном списке, и человеку всё равно, какая у каталога версия. Раньше
 * в DI был привязан только XML-парсер: JSON-каталог (например, Feedbooks) не открывался вовсе —
 * на его теле XML-парсер отдавал ошибку разбора.
 *
 * Robolectric нужен XML-ветке: `XmlPullParser` — класс Android, без него разбор Atom падает.
 *
 * Определение идёт по телу, а не по `Content-Type`: клиент отдаёт только тело ответа, а первый
 * значимый символ различает форматы надёжно. Фикстуры — настоящие фиды Флибусты и Feedbooks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class OpdsFeedRouterTest {

    private val router = OpdsFeedRouter(Opds1XmlParser(), Opds2JsonParser())

    @Test
    fun `xml feed goes to the atom parser`() = runTest {
        val feed = router.parse(resource("flibusta-root.xml"), BASE_URL).getOrNull()

        assertThat(feed).isNotNull()
        assertThat(feed?.isNavigation).isTrue()
    }

    @Test
    fun `json feed goes to the opds 2 parser`() = runTest {
        val feed = router.parse(resource("feedbooks-books.json"), BASE_URL).getOrNull()

        assertThat(feed).isNotNull()
        assertThat(feed?.title).isEqualTo("New & noteworthy")
        assertThat(feed?.entries).hasSize(2)
    }

    @Test
    fun `leading whitespace does not hide the format`() = runTest {
        val feed = router.parse("\n\n  " + resource("feedbooks-books.json"), BASE_URL).getOrNull()

        // Пробелы и переводы строк перед `{` встречаются у прокси и не должны ломать выбор.
        assertThat(feed).isNotNull()
        assertThat(feed?.entries).isNotEmpty()
    }

    @Test
    fun `unknown body is a failure with a clear reason`() = runTest {
        val result = router.parse("просто текст", BASE_URL)

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()?.message).contains("OPDS")
    }

    @Test
    fun `broken json is reported as a parse failure`() = runTest {
        val result = router.parse("{ это не json", BASE_URL)

        assertThat(result.isFailure).isTrue()
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "нет фикстуры $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        const val BASE_URL = "https://catalog.feedbooks.com/featured.json"
    }
}
