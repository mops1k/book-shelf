package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Видимость строки видов полки («Сетка / Авторы / Серии / Жанры»).
 *
 * Решение Александра (29.09.2026): строка нужна только на корне обзора. Зайдя в конкретную
 * категорию — жанр, автора или серию, — человек выбирает книгу, а не вид полки, и кнопки только
 * мешают; вернуться даёт «Назад» в шапке.
 */
class BrowseUiTest {

    @Test
    fun `view switch is visible at the browse root`() {
        assertThat(BrowseUi(mode = BrowseMode.AUTHORS, level = BrowseLevel.Root).showsViewSwitch)
            .isTrue()
        assertThat(BrowseUi(mode = BrowseMode.GENRES, level = BrowseLevel.Root).showsViewSwitch)
            .isTrue()
    }

    @Test
    fun `view switch is hidden inside a genre`() {
        val browse = BrowseUi(
            mode = BrowseMode.GENRES,
            level = BrowseLevel.Genre(code = "sf_space", label = "Космическая фантастика"),
        )

        assertThat(browse.showsViewSwitch).isFalse()
    }

    @Test
    fun `view switch is hidden inside an author and a series`() {
        assertThat(
            BrowseUi(mode = BrowseMode.AUTHORS, level = BrowseLevel.Author("Лев Толстой"))
                .showsViewSwitch,
        ).isFalse()
        assertThat(
            BrowseUi(mode = BrowseMode.SERIES, level = BrowseLevel.Series("Классика")).showsViewSwitch,
        ).isFalse()
    }
}
