package io.github.bookshelf.core.storage.files

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import org.junit.Test

/**
 * Полный хеш содержимого файла (`ContentHasher.kt`).
 *
 * Проверяется то, на чём стоит поиск дублей: одинаковые байты обязаны давать одинаковый хеш,
 * а недоступный файл — `null`, а не пустую строку. Пустая строка совпала бы у всех недоступных книг
 * и предложила бы их «удалить как дубли».
 */
class ContentHasherTest {

    @Test
    fun `identical bytes give the same hash`() {
        val hasher = DefaultContentHasher(opener("Привет, книга".toByteArray()))

        val first = hasher.contentHash("content://doc/a")
        val second = hasher.contentHash("content://doc/b")

        assertThat(first).isNotNull()
        assertThat(second).isEqualTo(first)
        // SHA-256 в hex — 64 символа: проверяем форму, чтобы опечатка в алгоритме не прошла молча.
        assertThat(first).hasLength(64)
    }

    @Test
    fun `different bytes give different hashes`() {
        val first = DefaultContentHasher(opener("Первая книга".toByteArray())).contentHash("content://doc/a")
        val second = DefaultContentHasher(opener("Вторая книга".toByteArray())).contentHash("content://doc/b")

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `unreadable file gives null`() {
        val opener = FakeContentOpener(bytes = null)

        assertThat(DefaultContentHasher(opener).contentHash("content://doc/a")).isNull()
    }

    /**
     * Резолвер, отдающий заданные байты на любой адрес.
     *
     * Поток создаётся заново на каждый вызов: один и тот же `ByteArrayInputStream` был бы прочитан
     * первым обращением, и второе вернуло бы пустое содержимое — тест «одинаковые байты» ловил бы
     * ошибку самого теста, а не приложения.
     */
    private fun opener(bytes: ByteArray): BookContentOpener = FakeContentOpener(bytes)

    /**
     * Свой фейк, а не `mockk`: `BookContentOpener` — интерфейс проекта, а собственные типы в тестах
     * не мокаются (ADR-012, правило 2). Здесь же видно и поведение: `null` означает «файл не
     * открылся», и это отдельный случай теста.
     */
    private class FakeContentOpener(private val bytes: ByteArray?) : BookContentOpener {

        override fun openStream(documentUri: String): InputStream? =
            bytes?.let { content -> ByteArrayInputStream(content) }

        override fun openSeekableChannel(documentUri: String): FileChannel? = null

        override fun sizeOf(documentUri: String): Long? = bytes?.size?.toLong()

        override fun tempCopy(documentUri: String): AppResult<String> =
            AppResult.failure(AppError.Unknown(message = "файл не нужен"))

        /** Копия для читалки в этих проверках не нужна. */
        override fun exportCopy(documentUri: String): AppResult<String> =
            AppResult.failure(AppError.Unknown(message = "не используется"))
    }
}
