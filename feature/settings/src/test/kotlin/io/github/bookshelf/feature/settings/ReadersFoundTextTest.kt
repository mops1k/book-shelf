package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Подпись строки «Читалки по умолчанию» на первом уровне экрана «Читалки».
 *
 * Строка ведёт на отдельный экран со списком читалок (требование Александра 05.10.2026), поэтому
 * подпись говорит, есть ли что смотреть: ноль читалок и «двенадцать» — разные состояния.
 */
class ReadersFoundTextTest {

    @Test
    fun `no readers is said plainly`() {
        val text = readersFoundText(0)

        assertThat(text.res).isEqualTo(R.string.settings_readers_defaults_none)
        assertThat(text.arg1).isNull()
    }

    @Test
    fun `the number of readers is shown`() {
        val text = readersFoundText(12)

        assertThat(text.res).isEqualTo(R.string.settings_readers_defaults_found)
        assertThat(text.arg1).isEqualTo(12)
    }
}
