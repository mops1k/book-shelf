package io.github.bookshelf.feature.collections

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.SmartRulePart
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.R as DesignR
import org.junit.Test

/**
 * Подпись правила умной полки собирается из частей (docs/tdd.md §8).
 *
 * Дефект, ради которого это появилось: строка правила рождалась в `:core:data` готовым русским
 * текстом («жанр: Космическая фантастика», «статус: не прочитано»), и в английском интерфейсе
 * подпись полки оставалась русской. Теперь слой данных отдаёт части, а слова выбирает экран.
 */
class SmartRuleTextTest {

    @Test
    fun `genre and status are translated, names stay data`() {
        assertThat(listOf(SmartRulePart.Genre("sf_space")).toSummaryText(maxParts = 1))
            .containsExactly(
                AppText(R.string.collections_rule_genre, AppText(DesignR.string.genre_sf_space)),
            )
        assertThat(listOf(SmartRulePart.Status("UNREAD")).toSummaryText(maxParts = 1))
            .containsExactly(
                AppText(R.string.collections_rule_status, AppText(DesignR.string.status_unread)),
            )
        // Имя автора — данные: переводить нечего, оно уходит аргументом как есть.
        assertThat(listOf(SmartRulePart.Author("Лем")).toSummaryText(maxParts = 1))
            .containsExactly(AppText(R.string.collections_rule_author, "Лем"))
    }

    @Test
    fun `an unknown genre code is shown as data`() {
        // Код из будущей версии не должен оставить подпись пустой: показываем его как есть.
        assertThat(listOf(SmartRulePart.Genre("some_new_genre")).toSummaryText(maxParts = 1))
            .containsExactly(AppText(R.string.collections_rule_genre, "some_new_genre"))
    }

    @Test
    fun `extra conditions are counted, not dropped silently`() {
        val parts = listOf(
            SmartRulePart.Genre("sf_space"),
            SmartRulePart.Author("Лем"),
        )

        val summary = parts.toSummaryText(maxParts = 1)

        assertThat(summary).hasSize(2)
        assertThat(summary.last()).isEqualTo(AppText(R.string.collections_rule_more, 1))
    }
}
