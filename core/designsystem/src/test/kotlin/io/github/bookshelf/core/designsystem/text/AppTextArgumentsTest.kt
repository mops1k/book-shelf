package io.github.bookshelf.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.R
import org.junit.Test

/**
 * Аргументы текста: вложенный [AppText] заменяется на переведённую строку.
 *
 * Нужно потому, что сообщение об отказе собирается из двух переводимых частей: «Не удалось создать
 * полку» (ресурс экрана) и «нет доступа» (текст ошибки по типу,
 * [io.github.bookshelf.core.common.error.AppError.text]). Без разворачивания вложенного текста в
 * баннер попал бы `AppText(res=…)` — то есть данные вместо текста. Проверяется чистой функцией, без
 * Compose: сама отрисовка — это `stringResource`, в ней решать нечего.
 */
class AppTextArgumentsTest {

    @Test
    fun `a nested text is replaced by its translation`() {
        val text = AppText(R.string.error_parse, AppText(R.string.error_io))

        // Резолвер вызывается для вложенного текста, и в аргументы попадает его перевод, а не значение.
        assertThat(text.arguments { nested -> "переведено ${nested.res}" })
            .containsExactly("переведено ${R.string.error_io}")
    }

    @Test
    fun `plain arguments are passed as they are`() {
        assertThat(AppText(R.string.error_network_code, 503).arguments { "не используется" })
            .containsExactly(503)
    }

    @Test
    fun `both arguments keep their order`() {
        val text = AppText(R.string.error_unsupported, "CBR", AppText(R.string.error_io))

        assertThat(text.arguments { nested -> "переведено ${nested.res}" })
            .containsExactly("CBR", "переведено ${R.string.error_io}")
    }

    @Test
    fun `missing arguments are dropped`() {
        assertThat(AppText(R.string.error_io).arguments { "не используется" }).isEmpty()
        assertThat(AppText(R.string.error_io, "один").arguments { "не используется" })
            .containsExactly("один")
    }
}
