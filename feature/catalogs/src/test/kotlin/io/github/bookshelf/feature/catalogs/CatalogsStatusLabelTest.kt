package io.github.bookshelf.feature.catalogs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Подпись под списком каталогов.
 *
 * Дефект, ради которого это проверяется: раньше подпись показывала «Стр. 1/2 · 12 каталогов»,
 * хотя страницы листались только аппаратными клавишами, а кнопок на экране не было — на устройстве
 * без таких клавиш вторая страница была недостижима. Теперь список прокручивается целиком, и
 * подпись не обещает страниц.
 *
 * Текст живёт в ресурсах (`catalogs_count`), поэтому проверяется сам ресурс: обещания страниц в
 * нём быть не должно ни при каком числе.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], qualifiers = "ru")
class CatalogsStatusLabelTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `the label counts catalogs and promises no pages`() {
        val label = resources.getString(R.string.catalogs_count, 3)

        assertThat(label).isEqualTo("каталогов: 3")
        assertThat(label).doesNotContain("Стр.")
    }

    @Test
    fun `an empty list is counted honestly`() {
        assertThat(resources.getString(R.string.catalogs_count, 0)).isEqualTo("каталогов: 0")
    }
}
