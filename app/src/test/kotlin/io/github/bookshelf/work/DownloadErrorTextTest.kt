package io.github.bookshelf.work

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Текст ошибки загрузки книги (`DownloadErrorText.kt`).
 *
 * Проверяется случай, из-за которого текст и появился: провайдер отказал в записи в папку, и вместо
 * сырого `Permission Denial` пользователь обязан увидеть, что делать дальше.
 */
class DownloadErrorTextTest {

    @Test
    fun `permission denial becomes an actionable message`() {
        val raw = "Провайдер не создал 'Автор - Книга.fb2.zip' с типом 'application/fb2+zip': " +
            "Permission Denial: writing com.android.externalstorage.ExternalStorageProvider uri " +
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
            "primary%3ABooks from pid=5356, uid=10056 requires " +
            "android.permission.MANAGE_DOCUMENTS, or grantUriPermission()"

        val message = downloadErrorMessage(raw)

        assertThat(message).contains("не разрешено писать в папку")
        // В подсказке обязано быть место, где это исправляется.
        assertThat(message).contains("Библиотека и сканирование")
        // Сырого текста провайдера в интерфейсе быть не должно.
        assertThat(message).doesNotContain("MANAGE_DOCUMENTS")
    }

    @Test
    fun `other errors keep their own text`() {
        // Сеть, код ответа и разбор объясняют себя сами: переписав их, потеряли бы подробности.
        assertThat(downloadErrorMessage("Сервер ответил кодом 404"))
            .isEqualTo("Сервер ответил кодом 404")
    }

    @Test
    fun `empty error gets a neutral message`() {
        assertThat(downloadErrorMessage(null)).isEqualTo("Не удалось скачать книгу")
        assertThat(downloadErrorMessage("   ")).isEqualTo("Не удалось скачать книгу")
    }
}
