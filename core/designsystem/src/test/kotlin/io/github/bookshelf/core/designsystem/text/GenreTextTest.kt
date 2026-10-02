package io.github.bookshelf.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.R
import org.junit.Test

/**
 * Подпись жанра выбирается по коду метаданных (docs/tdd.md §8).
 *
 * Дефект, ради которого это появилось: таблица названий жанров жила русским текстом в `:core:data`
 * (`FB2_GENRE_NAMES`), поэтому в английском интерфейсе подписи фасетов, уровня обзора и правила
 * умной полки оставались русскими. Ядро данных ресурсов не имеет — слова обязан выбирать
 * интерфейс, а код жанра остаётся значением фильтра.
 */
class GenreTextTest {

    @Test
    fun `a known code gets its own text`() {
        assertThat(genreText("sf_space")).isEqualTo(AppText(R.string.genre_sf_space))
        assertThat(genreText("detective")).isEqualTo(AppText(R.string.genre_detective))
        assertThat(genreText("prose_rus_classic")).isEqualTo(AppText(R.string.genre_prose_rus_classic))
    }

    @Test
    fun `case and spaces do not matter`() {
        // Коды приходят из метаданных и из настроек: регистр у них встречается разный.
        assertThat(genreText("  SF_Space ")).isEqualTo(AppText(R.string.genre_sf_space))
    }

    @Test
    fun `an unknown code has no text and stays data`() {
        // Неизвестный код — это значение метаданных, а не текст: экран покажет его как есть.
        assertThat(genreText("some_unknown_genre")).isNull()
        assertThat(genreText("")).isNull()
    }
}
