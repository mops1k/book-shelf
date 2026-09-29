package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Подпись освобождённого объёма (`LibraryLimits.kt`).
 *
 * Считается от байтов, а не от мегабайтов: команда «Очистить кэш обложек» возвращает точное
 * значение, и округление до целых мегабайт показало бы «0 МБ» после уборки нескольких сотен
 * килобайт — пользователь решил бы, что очистка не сработала.
 */
class LibraryLimitsTest {

    @Test
    fun `kilobytes are shown below one megabyte`() {
        assertThat(freedBytesLabel(512)).isEqualTo("512 Б")
        assertThat(freedBytesLabel(2048)).isEqualTo("2 КБ")
        assertThat(freedBytesLabel(1024 * 512)).isEqualTo("512 КБ")
    }

    @Test
    fun `megabytes are shown with a tenth`() {
        assertThat(freedBytesLabel(1024L * 1024L)).isEqualTo("1,0 МБ")
        assertThat(freedBytesLabel(5L * 1024L * 1024L)).isEqualTo("5,0 МБ")
        // 3,5 МБ: значение между целыми, ради которого и нужна десятая доля.
        assertThat(freedBytesLabel(3_670_016L)).isEqualTo("3,5 МБ")
    }

    @Test
    fun `negative difference is clamped to zero`() {
        // Отрицательное значение — расхождение «до/после», а не «минус столько-то байт»: кэш
        // мог наполниться между двумя замерами, и показывать минус было бы ложью.
        assertThat(freedBytesLabel(-500)).isEqualTo("0 Б")
    }
}
