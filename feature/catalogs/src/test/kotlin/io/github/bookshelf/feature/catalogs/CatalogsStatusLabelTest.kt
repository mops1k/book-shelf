package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.feature.catalogs.ui.catalogsStatusLabel
import org.junit.Test

/**
 * Подпись под списком каталогов.
 *
 * Дефект, ради которого это проверяется: раньше подпись показывала «Стр. 1/2 · 12 каталогов»,
 * хотя страницы листались только аппаратными клавишами, а кнопок на экране не было — на устройстве
 * без таких клавиш вторая страница была недостижима. Теперь список прокручивается целиком, и
 * подпись не обещает страниц.
 */
class CatalogsStatusLabelTest {

    @Test
    fun `the label counts catalogs and promises no pages`() {
        assertThat(catalogsStatusLabel(3)).isEqualTo("каталогов: 3")
        assertThat(catalogsStatusLabel(3)).doesNotContain("Стр.")
    }

    @Test
    fun `an empty list is counted honestly`() {
        assertThat(catalogsStatusLabel(0)).isEqualTo("каталогов: 0")
    }
}
