package io.github.bookshelf.core.storage.files

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** Проверка ключа изменений: метаданные и только первый префикс файла влияют на хеш. */
class QuickHashTest {

    @Test
    fun quick_hash_uses_size_mtime_and_first_64_kib() {
        val content = ByteArray(100 * 1024) { (it % 251).toByte() }
        val opener = FakeBookContentOpener { ByteArrayInputStream(content) }
        val hasher = DefaultQuickHasher(opener)

        val actual = hasher.quickHash(URI, content.size.toLong(), 1_700_000L)

        assertThat(actual).isEqualTo(expectedHash(content, content.size.toLong(), 1_700_000L))
    }

    @Test
    fun quick_hash_changes_when_mtime_changes() {
        val content = "Книга".toByteArray()
        val hasher = DefaultQuickHasher(FakeBookContentOpener { ByteArrayInputStream(content) })

        val first = hasher.quickHash(URI, content.size.toLong(), 1L)
        val second = hasher.quickHash(URI, content.size.toLong(), 2L)

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun quick_hash_returns_null_when_stream_is_unavailable() {
        val hasher = DefaultQuickHasher(FakeBookContentOpener { null })

        assertThat(hasher.quickHash(URI, 10L, 20L)).isNull()
    }

    private fun expectedHash(content: ByteArray, size: Long, mtime: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$size:$mtime:".toByteArray(Charsets.UTF_8))
        digest.update(content.copyOf(minOf(content.size, MAX_PREFIX_BYTES)))
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private class FakeBookContentOpener(
        private val streamFactory: () -> InputStream?,
    ) : BookContentOpener {

        override fun openStream(documentUri: String): InputStream? = streamFactory()

        override fun openSeekableChannel(documentUri: String): FileChannel? = null

        override fun sizeOf(documentUri: String): Long? = null
        override fun factsOf(documentUri: String): DocumentFacts? = null

        override fun tempCopy(documentUri: String): AppResult<String> =
            AppResult.failure(AppError.NotFound("copy"))

        /** Копия для читалки в этих проверках не нужна. */
        override fun replaceContent(documentUri: String, bytes: ByteArray): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "замена в этом тесте не нужна"))

        override fun exportCopy(documentUri: String): AppResult<String> =
            AppResult.failure(AppError.NotFound("export"))
    }

    private companion object {

        const val URI = "content://com.example.documents/document/book-1"
        const val MAX_PREFIX_BYTES = 64 * 1024
    }
}
