package io.github.bookshelf.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.DownloadFailure
import io.github.bookshelf.core.designsystem.R
import org.junit.Test

/**
 * Слова для отказа загрузки выбираются по коду («TDD» §8).
 *
 * Дефект, ради которого это появилось: отказ загрузки хранился в базе готовым текстом (в том числе
 * русской подсказкой «выберите папку заново»), и в английском интерфейсе он оставался русским.
 */
class DownloadFailureTextTest {

    @Test
    fun `every download failure has its own text`() {
        assertThat(DownloadFailure.NO_WRITE_PERMISSION.text())
            .isEqualTo(AppText(R.string.download_failure_no_write_permission))
        assertThat(DownloadFailure.RECEIVER_FOLDER_GONE.text())
            .isEqualTo(AppText(R.string.download_failure_receiver_folder_gone))
        assertThat(DownloadFailure.CREDENTIALS_UNREADABLE.text())
            .isEqualTo(AppText(R.string.download_failure_credentials_unreadable))
        assertThat(DownloadFailure.BAD_DOCUMENT_URI.text())
            .isEqualTo(AppText(R.string.download_failure_bad_document_uri))
        assertThat(DownloadFailure.DOWNLOAD_FAILED.text())
            .isEqualTo(AppText(R.string.download_failure_failed))
        assertThat(DownloadFailure.UNKNOWN.text())
            .isEqualTo(AppText(R.string.download_failure_unknown))
    }
}
