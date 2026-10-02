package io.github.bookshelf.work

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.DownloadFailure
import org.junit.Test

/**
 * Классификация отказа загрузки (`DownloadFailure.kt`).
 *
 * Дефект 29.09.2026: провайдер отказал в записи, а в очереди показывался сырой `Permission Denial`
 * — по нему нельзя понять, что делать. Теперь такой отказ становится кодом
 * [DownloadFailure.NO_WRITE_PERMISSION], а слова для человека выбирает экран.
 *
 * Сырой текст провайдера и сообщения нижнего слоя в интерфейс не идут: они остаются диагностикой
 * и пишутся в журнал (docs/tdd.md §7), иначе английский интерфейс показывал бы русские строки.
 */
class DownloadFailureTest {

    @Test
    fun `permission denial becomes an actionable code`() {
        val raw = "Провайдер не создал 'Автор - Книга.fb2.zip' с типом 'application/fb2+zip': " +
            "Permission Denial: writing com.android.externalstorage.ExternalStorageProvider uri " +
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
            "primary%3ABooks from pid=5356, uid=10056 requires " +
            "android.permission.MANAGE_DOCUMENTS, or grantUriPermission()"

        assertThat(downloadFailureOf(raw)).isEqualTo(DownloadFailure.NO_WRITE_PERMISSION)
    }

    @Test
    fun `other download errors get the general code`() {
        // Подробность (код ответа, обрыв сети) остаётся диагностикой: переписав её своими словами,
        // мы потеряли бы то, по чему ищут причину, — а в интерфейсе показывается понятное общее.
        assertThat(downloadFailureOf("Сервер ответил кодом 404"))
            .isEqualTo(DownloadFailure.DOWNLOAD_FAILED)
        assertThat(downloadFailureOf("Таймаут соединения"))
            .isEqualTo(DownloadFailure.DOWNLOAD_FAILED)
    }

    @Test
    fun `empty reason still gets a code`() {
        assertThat(downloadFailureOf(null)).isEqualTo(DownloadFailure.DOWNLOAD_FAILED)
        assertThat(downloadFailureOf("   ")).isEqualTo(DownloadFailure.DOWNLOAD_FAILED)
    }
}
