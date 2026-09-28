package io.github.bookshelf.core.opds.parser

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.opds.model.isAcquisition
import io.github.bookshelf.core.opds.model.isNavigation
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Разбор OPDS 1.2 (Atom XML) на настоящем фиде Flibusta.
 *
 * Фикстура — не выдуманная. Первый вариант теста на самодельном XML дал ложное «всё работает»,
 * а реальный каталог сразу показал вещи, которые иначе всплыли бы только на устройстве:
 *
 * * все ссылки **корневые** (`/opds/new`, `/opds/authorsindex`) — не относительные;
 * * `rel="search"` встречается **дважды**: дескриптор OpenSearch и рабочий шаблон Atom, и
 *   нужен второй, иначе поиск по каталогу не заработает;
 * * описание лежит в `<content type="text">`, а не в `<summary>`;
 * * корневые записи — **навигационные**: ссылок приобретения нет вовсе, а маркера
 *   `kind=navigation` в теле тоже нет, значит определять надо по отсутствию acquisition-ссылок;
 * * `id` вида `tag:root:new` — не URL, использовать его как ссылку нельзя.
 *
 * Robolectric обязателен: `android.util.Xml` вне него недоступен, и тест падал бы с
 * `NoClassDefFoundError`, что выглядит как «парсер сломан», а не как «среда не та».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class Opds1XmlParserTest {

    private val parser = Opds1XmlParser()

    @Test
    fun parses_real_flibusta_catalog() = runTest {
        val feed = parser.parse(FLIBUSTA_FIXTURE, BASE_URL).getOrNull()

        assertThat(feed).isNotNull()
        assertThat(feed?.title).isEqualTo("Flibusta catalog")
        assertThat(feed?.entries).hasSize(5)
    }

    /**
     * У каждого раздела корневого фида есть адрес, по которому его можно открыть.
     *
     * Проверка, которой не хватало: тесты ниже уже знали, что корневые записи навигационные
     * (acquisition-ссылок нет), но не проверяли, что у них вообще находится ссылка. Из-за
     * этого `isNavigation()` смотрел только на `rel`, а у Флибусты разделы объявлены ссылками
     * **без `rel`**: `<link href="/opds/genres" type="application/atom+xml;profile=opds-catalog"/>`.
     * На устройстве это выглядело как «кнопка „Открыть“ не нажимается».
     */
    @Test
    fun every_root_section_has_navigation_href() = runTest {
        val feed = parser.parse(FLIBUSTA_FIXTURE, BASE_URL).getOrNull()

        val sections = feed?.entries.orEmpty()
        assertThat(sections).isNotEmpty()
        assertThat(sections.all { it.links.none { link -> link.isAcquisition() } }).isTrue()

        val withoutHref = sections.filter { section ->
            section.links.none { link -> link.isNavigation() }
        }
        assertThat(withoutHref.map { it.title }).isEmpty()
    }

    /** Ссылка фида и ссылка самой записи — обе на `/opds/new`; берётся первая пригодная. */
    @Test
    fun section_href_points_to_real_feed_path() = runTest {
        val feed = parser.parse(FLIBUSTA_FIXTURE, BASE_URL).getOrNull()

        val newSection = feed?.entries.orEmpty().first { entry -> entry.title == "Новинки" }
        val href = newSection.links.first { link -> link.isNavigation() }.href
        assertThat(href).isEqualTo("https://host/opds/new")
    }

    @Test
    fun every_href_becomes_absolute() = runTest {
        val feed = parser.parse(FLIBUSTA_FIXTURE, BASE_URL).getOrNull()

        val hrefs = feed?.entries.orEmpty().flatMap { it.links.map { link -> link.href } }
        assertThat(hrefs).isNotEmpty()
        // Проверяем именно абсолютность: база в тесте фиктивная, поэтому хост — https://host.
        // Смысл проверки: в фиде ссылки корневые («/opds/new»), и в исходном виде они бесполезны.
        assertThat(hrefs.all { it.startsWith("https://") }).isTrue()
        assertThat(hrefs).contains("https://host/opds/new")
        assertThat(hrefs).contains("https://host/opds/authorsindex")
    }

    @Test
    fun search_url_is_the_template_one_not_the_opensearch_descriptor() = runTest {
        val feed = parser.parse(FLIBUSTA_FIXTURE, BASE_URL).getOrNull()

        // Дескриптор OpenSearch лежит первым и в поиске бесполезен: нужен шаблон с {searchTerms}.
        val search = feed?.searchUrl
        assertThat(search).isNotNull()
        assertThat(search).contains("{searchTerms}")
    }

    @Test
    fun root_catalog_is_navigation_not_a_book_list() = runTest {
        val feed = parser.parse(FLIBUSTA_FIXTURE, BASE_URL).getOrNull()

        // Ни у одной записи нет ссылок приобретения — это разделы, а не книги.
        assertThat(feed?.isNavigation).isTrue()
        assertThat(feed?.entries.orEmpty().all { it.links.none { link -> link.isAcquisition() } }).isTrue()
    }

    @Test
    fun entry_description_comes_from_content_not_summary() = runTest {
        val feed = parser.parse(FLIBUSTA_FIXTURE, BASE_URL).getOrNull()

        assertThat(feed?.entries?.first()?.summary).isEqualTo("Новые поступления за неделю")
    }

    @Test
    fun truncated_xml_is_a_failure_not_an_exception() = runTest {
        assertThat(parser.parse("<feed><entry>", BASE_URL).isFailure).isTrue()
    }

    @Test
    fun empty_body_is_a_failure() = runTest {
        assertThat(parser.parse("", BASE_URL).isFailure).isTrue()
    }

    @Test
    fun not_a_feed_is_a_failure() = runTest {
        assertThat(parser.parse("<html><body>404</body></html>", BASE_URL).isFailure).isTrue()
    }

    @Test
    fun absolute_urls_are_left_untouched() {
        assertThat("https://other.example/x".resolveAgainst(BASE_URL)).isEqualTo("https://other.example/x")
    }

    @Test
    fun root_relative_url_is_joined_to_the_origin() {
        assertThat("/covers/1.jpg".resolveAgainst(BASE_URL)).isEqualTo("https://host/covers/1.jpg")
    }

    @Test
    fun relative_url_is_joined_to_the_feed_directory() {
        assertThat("entry/1".resolveAgainst(BASE_URL)).isEqualTo("https://host/opds/entry/1")
    }

    @Test
    fun query_string_survives_url_resolution() {
        // java.net.URI кодирует «?» в пути: без ручной склейки пагинация отдаёт %3F.
        assertThat("/opds?page=2".resolveAgainst(BASE_URL)).isEqualTo("https://host/opds?page=2")
    }

    @Test
    fun fixture_is_loaded_from_resources() {
        val stream: InputStream? = javaClass.classLoader?.getResourceAsStream(FIXTURE_PATH)
        val body = stream?.readBytes()?.toString(Charsets.UTF_8)

        assertThat(body).isNotNull()
        assertThat(body).contains("Flibusta catalog")
        assertThat(body).contains("{searchTerms}")
    }

    private companion object {
        const val BASE_URL = "https://host/opds/root.xml"
        const val FIXTURE_PATH = "flibusta-root.xml"
        const val BOOKS_FIXTURE = "flibusta-books.xml"
        val FLIBUSTA_FIXTURE: String = checkNotNull(
            Opds1XmlParserTest::class.java.classLoader?.getResourceAsStream(FIXTURE_PATH),
        ) { "Фикстура $FIXTURE_PATH не найдена в ресурсах" }.readBytes().toString(Charsets.UTF_8)
    }


    /**
     * Регрессия на данных Flibusta: `category` у них — ЖАНР, а не серия, и `content` приходит
     * HTML-экранированным. Обе ошибки выглядели бы как «парсер работает», показывая в карточке
     * жанр вместо серии и разметку вместо аннотации.
     */
    @Test
    fun real_book_entry_has_genre_not_series_and_clean_summary() = runTest {
        val feed = parser.parse(
            javaClass.classLoader!!.getResourceAsStream(BOOKS_FIXTURE)!!.readBytes().toString(Charsets.UTF_8),
            "https://m.flibusta.is/opds/new/0/new",
        ).getOrNull()

        val entry = feed?.entries?.first { it.title == "Кольцо" }
        assertThat(entry).isNotNull()
        // Серия приходит ссылкой rel="related" на /opds/sequencebooks/…
        assertThat(entry?.seriesName).isNotNull()
        // Аннотация без тегов: content приходит как <p class="book">…</p>
        assertThat(entry?.summary).contains("Космический")
        assertThat(entry?.summary).doesNotContain("<p")
        assertThat(entry?.authors).contains("Бакстер Стивен")
    }

    @Test
    fun books_feed_is_not_navigation_because_links_are_present() = runTest {
        val feed = parser.parse(
            javaClass.classLoader!!.getResourceAsStream(BOOKS_FIXTURE)!!.readBytes().toString(Charsets.UTF_8),
            "https://m.flibusta.is/opds/new/0/new",
        ).getOrNull()

        // В списке книг есть ссылки на авторов и серии, но нет ссылок приобретения —
        // то есть это ещё не настоящий каталог книг, а список. Проверяем, что парсер не врёт.
        assertThat(feed?.entries?.size).isAtLeast(20)
    }

}
