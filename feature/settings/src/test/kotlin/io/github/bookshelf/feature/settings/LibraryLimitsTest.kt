package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.text.AppText
import org.junit.Test

/**
 * Подпись освобождённого объёма (`LibraryLimits.kt`): число считает функция, единицу даёт ресурс.
 *
 * Считается от байтов, а не от мегабайтов: команда «Очистить кэш обложек» возвращает точное
 * значение, и округление до целых мегабайт показало бы «0 МБ» после уборки нескольких сотен
 * килобайт — пользователь решил бы, что очистка не сработала.
 */
class LibraryLimitsTest {

    @Test
    fun `kilobytes are shown below one megabyte`() {
        assertThat(freedBytesText(512)).isEqualTo(AppText(R.string.settings_freed_bytes, 512L))
        assertThat(freedBytesText(2048)).isEqualTo(AppText(R.string.settings_freed_kb, 2L))
        assertThat(freedBytesText(1024 * 512)).isEqualTo(AppText(R.string.settings_freed_kb, 512L))
    }

    @Test
    fun `megabytes are shown with a tenth`() {
        assertThat(freedBytesText(1024L * 1024L)).isEqualTo(AppText(R.string.settings_freed_mb, "1,0"))
        assertThat(freedBytesText(5L * 1024L * 1024L)).isEqualTo(AppText(R.string.settings_freed_mb, "5,0"))
        // 3,5 МБ: значение между целыми, ради которого и нужна десятая доля.
        assertThat(freedBytesText(3_670_016L)).isEqualTo(AppText(R.string.settings_freed_mb, "3,5"))
    }

    @Test
    fun `negative difference is clamped to zero`() {
        // Отрицательное значение — расхождение «до/после», а не «минус столько-то байт»: кэш
        // мог наполниться между двумя замерами, и показывать минус было бы ложью.
        assertThat(freedBytesText(-500)).isEqualTo(AppText(R.string.settings_freed_bytes, 0L))
    }
}
