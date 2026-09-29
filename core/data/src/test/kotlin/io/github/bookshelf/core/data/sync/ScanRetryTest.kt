package io.github.bookshelf.core.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Повторы сбойного прогона скана.
 *
 * Дефект, ради которого это появилось (30.09.2026): сбойный прогон просил `Result.retry()` без
 * предела. WorkManager повторял работу с растущей задержкой, а `ExistingWorkPolicy.KEEP` не давал
 * поставить новый скан по тому же источнику — кнопка «Пересканировать» переставала действовать
 * (на устройстве `workspec.run_attempt_count` дошёл до 10, состояние ENQUEUED). Повторы нужны, но
 * не бесконечные: после порога работа завершается, а прогон остаётся `PAUSED` с курсором, и его
 * продолжит следующий скан.
 */
class ScanRetryTest {

    @Test
    fun `first attempts are retried`() {
        assertThat(shouldRetryScan(0)).isTrue()
        assertThat(shouldRetryScan(1)).isTrue()
    }

    @Test
    fun `retries stop after the limit`() {
        // Иначе работа уходит в экспоненциальный backoff и блокирует ручной скан политикой KEEP.
        assertThat(shouldRetryScan(MAX_SCAN_ATTEMPTS)).isFalse()
        assertThat(shouldRetryScan(MAX_SCAN_ATTEMPTS + 7)).isFalse()
    }
}
