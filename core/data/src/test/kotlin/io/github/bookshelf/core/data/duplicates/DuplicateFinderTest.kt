package io.github.bookshelf.core.data.duplicates

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.storage.files.ContentHasher
import org.junit.Test

/**
 * Поиск книг с одинаковым содержимым (`DuplicateFinder.kt`).
 *
 * Проверяются свойства, из-за которых автоматическое удаление дублей может стать опасным:
 * уникальный размер не читается вовсе, недоступные файлы не считаются дублями, пустые файлы идут
 * в отдельный список, а из одинаковых копий остаётся та, что разложена по папкам.
 */
class DuplicateFinderTest {

    @Test
    fun `identical books are grouped with the deeper path kept`() {
        val flat = book(id = "flat", path = "Седьмой.fb2.zip", size = 100, hash = "same")
        val nested = book(id = "nested", path = "Лукьяненко Сергей/Седьмой.fb2.zip", size = 100, hash = "same")

        val report = finder(hashes = mapOf("flat" to "same", "nested" to "same")).find(listOf(flat, nested))

        assertThat(report.groups).hasSize(1)
        // Остаётся копия в папке автора: она разложена, в отличие от безымянной копии в корне.
        assertThat(report.groups.first().keep.id).isEqualTo("nested")
        assertThat(report.groups.first().remove.map { it.id }).containsExactly("flat")
        assertThat(report.duplicatesToRemove).isEqualTo(1)
    }

    @Test
    fun `books of unique size are not hashed at all`() {
        val hasher = RecordingHasher(mapOf("a" to "ha", "b" to "hb", "c" to "hc"))

        val report = DuplicateFinder(hasher).find(
            listOf(
                book(id = "a", path = "a.fb2", size = 10, hash = null),
                book(id = "b", path = "b.fb2", size = 20, hash = null),
                book(id = "c", path = "c.fb2", size = 30, hash = null),
            ),
        )

        // Размер уникален — содержимое совпасть не может, и читать файлы незачем.
        assertThat(report.groups).isEmpty()
        assertThat(hasher.commonUris).isEmpty()
    }

    @Test
    fun `same size with different content is not a duplicate`() {
        val first = book(id = "a", path = "a.fb2", size = 100, hash = null)
        val second = book(id = "b", path = "b.fb2", size = 100, hash = null)

        val report = finder(hashes = mapOf("a" to "first", "b" to "second")).find(listOf(first, second))

        assertThat(report.groups).isEmpty()
    }

    @Test
    fun `empty and unreadable files are not duplicates`() {
        val empty = book(id = "empty", path = "empty.fb2", size = 0, hash = "same")
        val unreadable = book(id = "broken", path = "broken.fb2", size = 100, hash = null)
        val readable = book(id = "ok", path = "ok.fb2", size = 100, hash = null)

        val report = DuplicateFinder(RecordingHasher(mapOf("ok" to "hash"))).find(
            listOf(empty, unreadable, readable),
        )

        // Пустой файл — в отдельный список «битые», недоступный не сравнивается ни с чем.
        assertThat(report.broken.map { it.id }).containsExactly("empty")
        assertThat(report.groups).isEmpty()
    }

    @Test
    fun `equally deep copies keep the newer one`() {
        val older = book(id = "older", path = "a/книга.fb2", size = 100, hash = null, addedAt = 1)
        val newer = book(id = "newer", path = "b/книга.fb2", size = 100, hash = null, addedAt = 2)

        val report = finder(hashes = mapOf("older" to "same", "newer" to "same")).find(listOf(older, newer))

        assertThat(report.groups.first().keep.id).isEqualTo("newer")
    }

    private fun finder(hashes: Map<String, String>) = DuplicateFinder(RecordingHasher(hashes))

    private fun book(
        id: String,
        path: String,
        size: Long,
        hash: String?,
        addedAt: Long = 0L,
    ): Book = Book(
        id = id,
        title = id,
        authorsText = "",
        extension = "fb2",
        documentUri = "content://doc/$id",
        relativePath = path,
        sizeBytes = size,
        addedAt = addedAt,
    )

    /** Хеш по адресу документа; попутно записывает, для каких книг содержимое читали. */
    private class RecordingHasher(
        private val hashes: Map<String, String>,
    ) : ContentHasher {

        val commonUris = mutableListOf<String>()

        override fun contentHash(documentUri: String): String? {
            val id = documentUri.substringAfterLast('/')
            commonUris += id
            return hashes[id]
        }
    }

    @Test
    fun `a stopped search reports that it did not finish`() {
        val hasher = RecordingHasher(mapOf("a" to "same", "b" to "same"))
        val books = listOf(
            book(id = "a", path = "Автор/Книга.fb2", size = 100, hash = "same"),
            book(id = "b", path = "Книга.fb2", size = 100, hash = "same"),
        )

        // Читалка открылась до начала поиска: файлы читать нельзя вовсе.
        val report = DuplicateFinder(hasher).find(books, shouldStop = { true })

        assertThat(report.stopped).isTrue()
        assertThat(report.groups).isEmpty()
        assertThat(hasher.commonUris).isEmpty()
    }

    @Test
    fun `a search that was not stopped is marked complete`() {
        val hasher = RecordingHasher(mapOf("a" to "same", "b" to "same"))
        val books = listOf(
            book(id = "a", path = "Автор/Книга.fb2", size = 100, hash = "same"),
            book(id = "b", path = "Книга.fb2", size = 100, hash = "same"),
        )

        val report = DuplicateFinder(hasher).find(books)

        assertThat(report.stopped).isFalse()
    }
}
