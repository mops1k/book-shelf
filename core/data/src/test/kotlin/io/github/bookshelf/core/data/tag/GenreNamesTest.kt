package io.github.bookshelf.core.data.tag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Подписи жанров: пользователь не должен видеть коды FB2 (`sf_space`) в списках и фильтрах.
 */
class GenreNamesTest {

    @Test
    fun `known fb2 codes are translated`() {
        assertThat(genreDisplayName("sf_space")).isEqualTo("Космическая фантастика")
        assertThat(genreDisplayName("detective")).isEqualTo("Детективы")
        assertThat(genreDisplayName("sf_litrpg")).isEqualTo("ЛитРПГ")
    }

    @Test
    fun `unknown code is shown as is`() {
        // Лучше редкий код, чем пустая подпись или «Неизвестно».
        assertThat(genreDisplayName("sf_unknown_thing")).isEqualTo("sf_unknown_thing")
    }

    @Test
    fun `case and spaces do not break the lookup`() {
        assertThat(genreDisplayName(" SF_Space ")).isEqualTo("Космическая фантастика")
    }
}
