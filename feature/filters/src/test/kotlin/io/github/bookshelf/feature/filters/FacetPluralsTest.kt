package io.github.bookshelf.feature.filters

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Формы слов в сводке фильтров: «1 автор», «2 автора», «5 авторов», но «11 авторов» и снова
 * «21 автор».
 *
 * Проверяется на самих ресурсах, а не на тексте в коде: слова переехали в `<plurals>`, и правило
 * выбора формы теперь часть ресурса. Исключение для 11–14 обязательно — «11 автор» и «12 автора»
 * именно те ошибки, которые выдают машинный перевод. Локаль `ru` берётся квалификатором: русский
 * лежит в `values/` (основной язык), английский — в `values-en/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], qualifiers = "ru")
class FacetPluralsTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `russian forms agree with the number`() {
        assertThat(word(R.plurals.filters_word_authors, 1)).isEqualTo("1 автор")
        assertThat(word(R.plurals.filters_word_authors, 2)).isEqualTo("2 автора")
        assertThat(word(R.plurals.filters_word_authors, 5)).isEqualTo("5 авторов")
        assertThat(word(R.plurals.filters_word_authors, 11)).isEqualTo("11 авторов")
        assertThat(word(R.plurals.filters_word_authors, 21)).isEqualTo("21 автор")
    }

    @Test
    fun `every criterion has its own word`() {
        assertThat(word(R.plurals.filters_word_series, 22)).isEqualTo("22 серии")
        assertThat(word(R.plurals.filters_word_statuses, 12)).isEqualTo("12 статусов")
        assertThat(word(R.plurals.filters_word_tags, 3)).isEqualTo("3 жанра")
        assertThat(word(R.plurals.filters_word_formats, 0)).isEqualTo("0 форматов")
    }

    private fun word(id: Int, count: Int): String = resources.getQuantityString(id, count, count)
}
