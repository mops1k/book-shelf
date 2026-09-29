package io.github.bookshelf.feature.scan

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.ScanStatus
import org.junit.Test

/**
 * Кнопки, подписи и состояние экрана сканирования.
 *
 * Проверяется то, из-за чего экран раньше врал и не давал продолжить: подпись прогресса
 * «58 из 0» (общее число файлов никто не считает) и всегда отключённая кнопка «Продолжить».
 * Кнопки теперь выводятся из состояния прогона, а не из неизвестного общего числа.
 */
class ScanControlsTest {

    @Test
    fun `progress caption shows no invented total`() {
        val unknown = ScanProgressUi(filesSeen = 58, filesTotal = null)
        val zero = ScanProgressUi(filesSeen = 58, filesTotal = 0)
        val estimate = ScanProgressUi(filesSeen = 58, filesTotal = 72)

        assertThat(scanProgressCaption(unknown)).isEqualTo("Просмотрено файлов: 58")
        // Ноль — это «оценки нет», а не «файлов ноль»: раньше здесь стояло «58 из 0».
        assertThat(scanProgressCaption(zero)).isEqualTo("Просмотрено файлов: 58")
        // Оценка по прошлому прогону показывается с тильдой — это ориентир, а не точное число.
        assertThat(scanProgressCaption(estimate)).isEqualTo("Просмотрено 58 из ~72")
    }

    @Test
    fun `caption without progress says the scan has not started`() {
        assertThat(scanProgressCaption(null)).isEqualTo("Сканирование ещё не запускалось")
    }

    @Test
    fun `running scan offers pause and the way to the shelf`() {
        val actions = scanActionsOf(ScanStatus.RUNNING)

        assertThat(actions.canPause).isTrue()
        assertThat(actions.canResume).isFalse()
        // Уйти на полку можно всегда: скан идёт в фоне, экран его не держит.
        assertThat(actions.canOpenLibrary).isTrue()
    }

    @Test
    fun `paused scan offers resume`() {
        val actions = scanActionsOf(ScanStatus.PAUSED)

        assertThat(actions.canResume).isTrue()
        assertThat(actions.canPause).isFalse()
        assertThat(actions.canOpenLibrary).isTrue()
    }

    @Test
    fun `finished and idle scans offer only the shelf`() {
        listOf(ScanStatus.IDLE, ScanStatus.FINISHED, ScanStatus.FAILED, ScanStatus.CANCELLED)
            .forEach { status ->
                val actions = scanActionsOf(status)
                assertThat(actions.canPause).isFalse()
                assertThat(actions.canResume).isFalse()
                assertThat(actions.canOpenLibrary).isTrue()
            }
    }

    @Test
    fun `resume is available exactly on pause`() {
        // Прежнее правило требовало известного общего числа файлов, которого нет никогда:
        // кнопка «Продолжить» была отключена даже на паузе (проверено на устройстве 28.09.2026).
        assertThat(canResumeScan(status = ScanStatus.PAUSED, filesSeen = 58)).isTrue()
        assertThat(canResumeScan(status = ScanStatus.RUNNING, filesSeen = 58)).isFalse()
        assertThat(canResumeScan(status = ScanStatus.FINISHED, filesSeen = 72)).isFalse()
    }

    @Test
    fun `status labels name the run state in human words`() {
        assertThat(scanStatusLabel(ScanStatus.RUNNING)).isEqualTo("Идёт сканирование")
        assertThat(scanStatusLabel(ScanStatus.PAUSED)).isEqualTo("Пауза — найденное сохранено")
        assertThat(scanStatusLabel(ScanStatus.FINISHED)).isEqualTo("Скан завершён")
    }

    @Test
    fun `state derives actions and resume from the run status`() {
        val paused = ScanUiState(status = ScanStatus.PAUSED, progress = ScanProgressUi(filesSeen = 58))

        assertThat(paused.isRunning).isFalse()
        assertThat(paused.canResume).isTrue()
        assertThat(paused.actions.canResume).isTrue()
        assertThat(ScanUiState().actions.canOpenLibrary).isTrue()
    }
}
