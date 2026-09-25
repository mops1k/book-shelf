package io.github.bookshelf.feature.scan

import com.google.common.truth.Truth.assertThat
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
    fun `scan can be resumed only when it is stopped and unfinished`() {
        assertThat(canResumeScan(isRunning = false, filesSeen = 4300, filesTotal = 12000)).isTrue()
        assertThat(canResumeScan(isRunning = true, filesSeen = 4300, filesTotal = 12000)).isFalse()
        assertThat(canResumeScan(isRunning = false, filesSeen = 12000, filesTotal = 12000)).isFalse()
        assertThat(canResumeScan(isRunning = false, filesSeen = 0, filesTotal = null)).isFalse()
    }

    @Test
    fun `an idle state without progress cannot be resumed`() {
        assertThat(ScanUiState().canResume).isFalse()
    }
}
