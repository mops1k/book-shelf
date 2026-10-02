package io.github.bookshelf.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.R
import org.junit.Test

/**
 * Сообщение из нескольких переводимых частей.
 *
 * Так собираются длинные итоги («Оптимизировано книг: 2», «сэкономлено 2,0 МБ», «уже оптимальных: 1»):
 * в них до пяти чисел, а [AppText] несёт не больше двух аргументов, поэтому части склеиваются, а не
 * подставляются в одну строку. Проверяется чистой функцией, без Compose: экранная часть — это
 * `stringResource` для разделителя, решать там нечего.
 */
class AppTextListTest {

    @Test
    fun `parts are joined in order with the separator`() {
        val parts = listOf(AppText(R.string.error_io), AppText(R.string.error_not_found))

        assertThat(parts.joined(separator = " · ") { part -> "перевод ${part.res}" })
            .isEqualTo("перевод ${R.string.error_io} · перевод ${R.string.error_not_found}")
    }

    @Test
    fun `a single part is shown without a separator`() {
        assertThat(listOf(AppText(R.string.error_io)).joined(separator = " · ") { part -> "перевод ${part.res}" })
            .isEqualTo("перевод ${R.string.error_io}")
    }

    @Test
    fun `an empty message is empty, not a separator`() {
        assertThat(emptyList<AppText>().joined(separator = " · ") { part -> "перевод ${part.res}" })
            .isEmpty()
    }
}
