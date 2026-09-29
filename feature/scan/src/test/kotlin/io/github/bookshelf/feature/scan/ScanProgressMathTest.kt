package io.github.bookshelf.feature.scan

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.ScanStatus
import org.junit.Test

/**
 * Тесты чистой арифметики прогресса скана: проценты и правило «можно продолжить».
 */
class ScanProgressMathTest {

    @Test
    fun `percent is zero while the total is unknown`() {
        assertThat(scanPercent(filesSeen = 500, filesTotal = null)).isEqualTo(0)
        assertThat(scanPercent(filesSeen = 500, filesTotal = 0)).isEqualTo(0)
    }

    @Test
    fun `percent is computed from seen and total`() {
        assertThat(scanPercent(filesSeen = 300, filesTotal = 1200)).isEqualTo(25)
        assertThat(scanPercent(filesSeen = 1200, filesTotal = 1200)).isEqualTo(100)
    }

    @Test
    fun `percent is clamped when counters disagree`() {
        assertThat(scanPercent(filesSeen = 1500, filesTotal = 1200)).isEqualTo(100)
        assertThat(scanPercent(filesSeen = -5, filesTotal = 1200)).isEqualTo(0)
    }

    @Test
    fun `fraction follows percent`() {
        assertThat(scanProgressFraction(filesSeen = 600, filesTotal = 1200)).isEqualTo(0.5f)
    }

    @Test
    fun `scan can be resumed only when the run is paused`() {
        // Правило перенесено в ScanControlsTest: признак возобновляемости — состояние прогона,
        // а не неизвестное общее число файлов.
        assertThat(canResumeScan(status = ScanStatus.PAUSED, filesSeen = 4300)).isTrue()
        assertThat(canResumeScan(status = ScanStatus.RUNNING, filesSeen = 4300)).isFalse()
        assertThat(canResumeScan(status = ScanStatus.FINISHED, filesSeen = 12000)).isFalse()
    }

    @Test
    fun `an idle state without progress cannot be resumed`() {
        assertThat(ScanUiState().canResume).isFalse()
    }
}
