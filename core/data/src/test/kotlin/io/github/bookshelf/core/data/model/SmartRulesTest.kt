package io.github.bookshelf.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Правила умной полки.
 *
 * Проверяется то, от чего зависит содержимое полки: правила переживают запись в БД и обратно,
 * превращаются в тот же запрос, что и фильтр, а битые или пустые правила не превращают полку
 * во «всю библиотеку».
 */
class SmartRulesTest {

    @Test
    fun `rules survive the json round trip`() {
        val rules = SmartRules(
            authors = setOf("Бакстер Стивен"),
            series = setOf("Ксили"),
            tags = setOf("sf_space"),
            formats = setOf("fb2"),
            statuses = setOf("UNREAD"),
            favoritesOnly = true,
        )

        assertThat(SmartRules.fromJson(rules.toJson())).isEqualTo(rules)
    }

    @Test
    fun `rules become the same query as the shelf filter`() {
        val query = SmartRules(
            authors = setOf("Бакстер Стивен"),
            series = setOf("Ксили"),
            tags = setOf("sf_space"),
            formats = setOf("fb2"),
            statuses = setOf("UNREAD"),
            favoritesOnly = true,
        ).toBookQuery()

        // Одна реализация предиката на фильтр и на полку: иначе «полка по жанру» показывала бы
        // не то, что фильтр по тому же жанру.
        assertThat(query.authorNames).containsExactly("Бакстер Стивен")
        assertThat(query.series).containsExactly("Ксили")
        assertThat(query.tags).containsExactly("sf_space")
        assertThat(query.formats).containsExactly("fb2")
        assertThat(query.statuses).containsExactly(ReadingStatus.UNREAD)
        assertThat(query.favoritesOnly).isTrue()
    }

    @Test
    fun `unknown status is skipped instead of breaking the shelf`() {
        val query = SmartRules(statuses = setOf("UNREAD", "ЧИТАЮ")).toBookQuery()

        assertThat(query.statuses).containsExactly(ReadingStatus.UNREAD)
    }

    @Test
    fun `empty rules are not the whole library`() {
        assertThat(SmartRules().isEmpty).isTrue()
        // Полка без условий честно пустая: показать 72 книги вместо подборки — соврать о полке.
        assertThat(SmartRules.fromJson(null)).isNull()
        assertThat(SmartRules.fromJson("   ")).isNull()
    }

    @Test
    fun `broken json means no rules`() {
        assertThat(SmartRules.fromJson("{не json")).isNull()
    }

    @Test
    fun `rules are built from the current filter`() {
        val rules = SmartRules.fromQuery(
            BookQuery(
                authorNames = setOf("Лукьяненко"),
                tags = setOf("sf_space"),
                statuses = setOf(ReadingStatus.READING),
            ),
        )

        assertThat(rules.authors).containsExactly("Лукьяненко")
        assertThat(rules.tags).containsExactly("sf_space")
        assertThat(rules.statuses).containsExactly("READING")
    }

    @Test
    fun `parts name the conditions without text from the data layer`() {
        // Подпись собирает экран: здесь только значения и коды, никакого русского текста
        // (иначе в английском интерфейсе подпись правила была бы русской, «TDD» §8).
        assertThat(SmartRules(tags = setOf("sf_space")).parts())
            .containsExactly(SmartRulePart.Genre("sf_space"))
        assertThat(SmartRules(favoritesOnly = true).parts())
            .containsExactly(SmartRulePart.Favorites)
        assertThat(SmartRules(statuses = setOf("UNREAD")).parts())
            .containsExactly(SmartRulePart.Status("UNREAD"))
        assertThat(SmartRules().parts()).isEmpty()
    }

    @Test
    fun `parts keep one condition of each kind in a stable order`() {
        val rules = SmartRules(
            tags = setOf("sf_space", "detective"),
            authors = setOf("Лем"),
            statuses = setOf("UNREAD", "READ"),
        )

        // По одному условию каждого вида: подпись из двух жанров смысла не имеет.
        assertThat(rules.parts()).containsExactly(
            SmartRulePart.Genre("sf_space"),
            SmartRulePart.Author("Лем"),
            SmartRulePart.Status("UNREAD"),
        ).inOrder()
    }
}
