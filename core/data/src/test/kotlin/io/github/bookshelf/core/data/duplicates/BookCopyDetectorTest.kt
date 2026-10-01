package io.github.bookshelf.core.data.duplicates

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.storage.files.ContentHasher
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * «Этот файл — копия уже известной книги?» (`BookCopyDetector.kt`).
 *
 * Дефект 30.09–01.10.2026: FBReader скопировал книгу внутрь нашей библиотеки и назвал копию по
 * названию книги — `Предыстория. Книга 1.fb2.zip` вместо `Предыстория. Книга 1 [=Истоки].fb2.zip`.
 * Прежнее правило «совпали имя файла и размер» такую копию не поймало (имена разные), в полке
 * появилась вторая книга (92 вместо 91), а прогресс чтения лёг на копию вместо оригинала.
 * Поэтому решает отпечаток содержимого, а не имя и не путь.
 *
 * Проверяются границы, из-за которых «защита от копий» могла бы стать опасной: уникальный размер
 * не читается вовсе, недоступный файл копией не считается (сравнивать нечего), уже посчитанный
 * отпечаток не пересчитывается, а посчитанный отпечаток оригинала запоминается вызывающим.
 */
class BookCopyDetectorTest {

    private val hasher = RecordingHasher(
        mapOf(
            "original" to "hash-1",
            "candidate" to "hash-1",
            "other" to "hash-2",
        ),
    )

    @Test
    fun `a file with the same content under another name is a copy`() {
        val original = book(id = "original", size = 514_015)
        val copy = detect(uri("candidate"), 514_015, listOf(original))

        // Имя копии не совпадает с именем оригинала: книгу опознаёт содержимое, а не имя.
        assertThat(copy?.originalBookId).isEqualTo("original")
    }

    @Test
    fun `a file of another size is not compared at all`() {
        val original = book(id = "original", size = 588_846)

        val copy = detect(uri("candidate"), 588_847, listOf(original))

        assertThat(copy).isNull()
        // Уникальный размер — единственное, что можно проверить дешево, не читая файл целиком.
        assertThat(hasher.read).isEmpty()
    }

    @Test
    fun `the same size with other content is not a copy`() {
        val original = book(id = "original", size = 514_015)

        val copy = detect(
            uri("other"),
            514_015,
            listOf(book(id = "original", size = 514_015, contentHash = "hash-1")),
        )

        assertThat(copy).isNull()
        // Содержимое читали только у кандидата: отпечаток оригинала уже был посчитан раньше.
        assertThat(hasher.read).containsExactly("other")
    }

    @Test
    fun `a remembered fingerprint of the original is not computed again`() {
        val original = book(id = "original", size = 514_015)

        detect(
            uri("candidate"),
            514_015,
            listOf(book(id = "original", size = 514_015, contentHash = "hash-1")),
        )

        assertThat(hasher.read).containsExactly("candidate")
    }

    @Test
    fun `a computed fingerprint of the original is remembered`() {
        val original = book(id = "original", size = 514_015)
        val remembered = mutableListOf<Pair<String, String>>()

        detect(
            uri("candidate"),
            514_015,
            listOf(original),
            remember = { bookId, hash -> remembered += bookId to hash },
        )

        // Иначе следующий скан перечитывал бы ту же книгу целиком на каждом проходе.
        assertThat(remembered).containsExactly("original" to "hash-1")
    }

    @Test
    fun `an unreadable candidate is not a copy`() {
        val original = book(id = "original", size = 514_015)

        val copy = detect(
            uri("unknown"),
            514_015,
            listOf(book(id = "original", size = 514_015, contentHash = "hash-1")),
        )

        // Файл не открылся: сравнивать нечего, и «копией» объявлять его нельзя.
        assertThat(copy).isNull()
    }

    @Test
    fun `an unreadable original is not a copy`() {
        val broken = book(id = "broken", size = 514_015)

        val copy = detect(uri("candidate"), 514_015, listOf(broken))

        assertThat(copy).isNull()
    }

    @Test
    fun `an empty candidate is not compared`() {
        val original = book(id = "original", size = 0)

        val copy = detect(uri("candidate"), 0, listOf(original))

        assertThat(copy).isNull()
        assertThat(hasher.read).isEmpty()
    }

    @Test
    fun `a candidate with no books at all is not a copy`() {
        val copy = detect(uri("candidate"), 514_015, emptyList())

        assertThat(copy).isNull()
        assertThat(hasher.read).isEmpty()
    }

    @Test
    fun `the first known book with the same content wins`() {
        val copy = detect(
            uri("candidate"),
            514_015,
            listOf(
                book(id = "first", size = 514_015, contentHash = "hash-1"),
                book(id = "second", size = 514_015, contentHash = "hash-1"),
            ),
        )

        // Детерминированность: порядок книг задаёт вызывающий, перебираем его как есть.
        assertThat(copy?.originalBookId).isEqualTo("first")
    }

    /** Вызов распознавания: оно `suspend`, потому что запоминает отпечаток в базе. */
    private fun detect(
        candidateUri: String,
        candidateSize: Long,
        known: List<KnownBookFile>,
        remember: suspend (String, String) -> Unit = { _, _ -> },
    ): CopyMatch? = runBlocking {
        BookCopyDetector(hasher).findOriginal(
            candidateUri = candidateUri,
            candidateSize = candidateSize,
            known = known,
            remember = remember,
        )
    }

    private fun uri(id: String) = "content://doc/$id"

    /** Книга библиотеки в том виде, в каком её видит распознавание копий. */
    private fun book(id: String, size: Long, contentHash: String? = null) =
        KnownBookFile(id = id, documentUri = uri(id), sizeBytes = size, contentHash = contentHash)

    /** Отпечаток по адресу документа; попутно записывает, какие файлы читали. */
    private class RecordingHasher(private val hashes: Map<String, String>) : ContentHasher {

        val read = mutableListOf<String>()

        override fun contentHash(documentUri: String): String? {
            val id = documentUri.substringAfterLast('/')
            read += id
            return hashes[id]
        }
    }
}
