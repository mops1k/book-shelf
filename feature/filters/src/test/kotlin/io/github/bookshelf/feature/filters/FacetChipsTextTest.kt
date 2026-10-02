package io.github.bookshelf.feature.filters

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.designsystem.R
import io.github.bookshelf.core.designsystem.text.AppText
import org.junit.Test

/**
 * Подписи чипов фасетов: код — resource, имя — данные (docs/tdd.md §8).
 *
 * Дефект, ради которого это появилось: подписи жанров и статусов рождались в `:core:data`
 * готовыми русскими строками, и в английском интерфейсе чипы оставались русскими. Теперь слой
 * данных отдаёт код/имя, а слова выбирает интерфейс.
 */
class FacetChipsTextTest {

    @Test
    fun `genre and status chips carry their own text`() {
        val sections = FacetSet(
            tags = listOf(FacetItem(id = "sf_space", labelCode = "sf_space", count = 3)),
            statuses = listOf(FacetItem(id = "UNREAD", labelCode = "UNREAD", count = 2)),
            authors = listOf(FacetItem(id = "Лем", labelCode = "Лем", count = 1)),
        ).toSectionsUi()

        val tags = sections.first { section -> section.kind == FacetKind.TAGS }.items.single()
        val statuses = sections.first { section -> section.kind == FacetKind.STATUSES }.items.single()
        val authors = sections.first { section -> section.kind == FacetKind.AUTHORS }.items.single()

        assertThat(tags.labelText).isEqualTo(AppText(R.string.genre_sf_space))
        assertThat(statuses.labelText).isEqualTo(AppText(R.string.status_unread))
        // Имя автора — данные: текста из ресурсов у него нет, показывается как есть.
        assertThat(authors.labelText).isNull()
        assertThat(authors.labelCode).isEqualTo("Лем")
    }

    @Test
    fun `an unknown status or genre keeps its code as data`() {
        val sections = FacetSet(
            statuses = listOf(FacetItem(id = "PAUSED", labelCode = "PAUSED", count = 1)),
            tags = listOf(FacetItem(id = "some_new_genre", labelCode = "some_new_genre", count = 1)),
        ).toSectionsUi()

        val statuses = sections.first { section -> section.kind == FacetKind.STATUSES }.items.single()
        val tags = sections.first { section -> section.kind == FacetKind.TAGS }.items.single()

        // Значение из будущей версии не ломает экран: покажем код как значение метаданных.
        assertThat(statuses.labelText).isNull()
        assertThat(tags.labelText).isNull()
        assertThat(tags.labelCode).isEqualTo("some_new_genre")
    }
}
