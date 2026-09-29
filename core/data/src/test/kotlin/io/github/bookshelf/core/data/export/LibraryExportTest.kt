package io.github.bookshelf.core.data.export

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.ReadingStatus
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Экспорт библиотеки в JSON.
 *
 * Проверяется состав файла, а не форматирование: версия схемы, все поля книги, полки с правилами и
 * прогресс чтения. Без версии и полного состава файл бесполезен — по нему нельзя ни перенести
 * библиотеку, ни сверить, что приложение видит те же данные.
 */
class LibraryExportTest {

    @Test
    fun `file carries the format version and the export time`() {
        val json = exportLibrary(exportedAt = 1_790_000_000_000L, books = emptyList(), collections = emptyList())

        val parsed = Json.decodeFromString<LibraryExportFile>(json)

        assertThat(parsed.version).isEqualTo(CURRENT_EXPORT_VERSION)
        assertThat(parsed.exportedAt).isEqualTo(1_790_000_000_000L)
    }

    @Test
    fun `book carries every field the card shows plus genres and progress`() {
        val json = exportLibrary(
            exportedAt = 1L,
            books = listOf(
                book(
                    id = "lem",
                    title = "Солярис",
                    subtitle = "роман",
                    authors = listOf("Станислав Лем", "Переводчик"),
                    series = "Фантастика",
                    status = ReadingStatus.READING,
                    rating = 5,
                    progress = 0.42f,
                ),
            ),
            collections = emptyList(),
            genres = mapOf("lem" to listOf("sf_space", "sf_heroic")),
        )

        val exported = Json.decodeFromString<LibraryExportFile>(json).books.single()

        assertThat(exported.title).isEqualTo("Солярис")
        assertThat(exported.subtitle).isEqualTo("роман")
        assertThat(exported.authorsText).isEqualTo("Станислав Лем, Переводчик")
        assertThat(exported.authors).containsExactly("Станислав Лем", "Переводчик")
        assertThat(exported.seriesName).isEqualTo("Фантастика")
        assertThat(exported.genres).containsExactly("sf_space", "sf_heroic")
        assertThat(exported.extension).isEqualTo("fb2")
        assertThat(exported.sizeBytes).isEqualTo(1_024L)
        assertThat(exported.readingStatus).isEqualTo("READING")
        assertThat(exported.rating).isEqualTo(5)
        // Прогресс — та же доля 0..1, что в базе: иначе при переносе процент поехал бы в 100 раз.
        assertThat(exported.progressPercent).isEqualTo(0.42f)
    }

    @Test
    fun `collections keep their type and smart rules`() {
        val json = exportLibrary(
            exportedAt = 1L,
            books = emptyList(),
            collections = listOf(
                Collection(
                    id = "c1",
                    name = "Космос",
                    type = CollectionType.SMART,
                    rulesJson = "{\"tags\":[\"sf_space\"]}",
                ),
                Collection(id = "c2", name = "Читаю", type = CollectionType.MANUAL, bookCount = 3),
            ),
        )

        val collections = Json.decodeFromString<LibraryExportFile>(json).collections

        assertThat(collections.map { it.name }).containsExactly("Космос", "Читаю")
        assertThat(collections.first().type).isEqualTo("SMART")
        assertThat(collections.first().rulesJson).contains("sf_space")
        assertThat(collections.last().bookCount).isEqualTo(3)
    }

    @Test
    fun `empty library is a valid file with empty lists`() {
        val json = exportLibrary(exportedAt = 1L, books = emptyList(), collections = emptyList())

        val parsed = Json.decodeFromString<LibraryExportFile>(json)

        assertThat(parsed.books).isEmpty()
        assertThat(parsed.collections).isEmpty()
        assertThat(json).contains("\"version\"")
    }

    @Test
    fun `suggested file name carries the export date and the json extension`() {
        val name = libraryExportFileName(now = 1_790_000_000_000L)

        // Имя предлагается в системном диалоге: без расширения `.json` провайдер создаст документ
        // без типа, и файл нельзя будет открыть как JSON, а без даты две выгрузки спорят за имя.
        assertThat(name).startsWith("bookshelf-export-")
        assertThat(name).endsWith(".json")
        assertThat(name).matches("bookshelf-export-\\d{4}-\\d{2}-\\d{2}\\.json")
    }

    private fun book(
        id: String,
        title: String,
        subtitle: String? = null,
        authors: List<String>,
        series: String? = null,
        status: ReadingStatus = ReadingStatus.UNREAD,
        rating: Int? = null,
        progress: Float? = null,
    ) = Book(
        id = id,
        title = title,
        subtitle = subtitle,
        authorsText = authors.joinToString(", "),
        authors = authors,
        seriesName = series,
        extension = "fb2",
        sizeBytes = 1_024L,
        readingStatus = status,
        rating = rating,
        addedAt = 1L,
        progressPercent = progress,
    )
}
