package io.github.bookshelf.core.data.mapper

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.datastore.model.Grouping as SettingsGrouping
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.datastore.model.SortOrder as SettingsSortOrder
import org.junit.Test

/**
 * Маппинг «настройка полки → запрос» (`LibraryQueryDefaults.kt`).
 *
 * Проверяется главное свойство: **каждое** значение настроек получает пару в слое данных, а не
 * молча падает в одну и ту же сортировку. Поэтому тест идёт по всем `entries`, а не по двум
 * выборочным значениям: новое значение в настройках без пары обязано уронить проверку, а не
 * изменить порядок книг у пользователя.
 */
class LibrarySettingsMapperTest {

    @Test
    fun `every settings sort order has its own query counterpart`() {
        val mapped = SettingsSortOrder.entries.map { it.toQuerySortOrder() }

        assertThat(mapped).containsExactlyElementsIn(SortOrder.entries)
        assertThat(mapped).hasSize(SettingsSortOrder.entries.size)
    }

    @Test
    fun `every settings grouping has its own query counterpart`() {
        val mapped = SettingsGrouping.entries.map { it.toQueryGrouping() }

        assertThat(mapped).containsExactlyElementsIn(Grouping.entries)
        assertThat(mapped).hasSize(SettingsGrouping.entries.size)
    }

    @Test
    fun `named values survive the mapping`() {
        // Соответствие по имени, а не по позиции: перестановка констант в настройках не должна
        // менять порядок книг на полке.
        assertThat(SettingsSortOrder.SERIES.toQuerySortOrder()).isEqualTo(SortOrder.SERIES)
        assertThat(SettingsGrouping.FOLDER.toQueryGrouping()).isEqualTo(Grouping.FOLDER)
    }

    @Test
    fun `library settings build the whole default order`() {
        val defaults = LibrarySettings(
            sortOrder = SettingsSortOrder.ADDED_AT,
            sortDescending = true,
            grouping = SettingsGrouping.AUTHOR,
        ).toLibraryQueryDefaults()

        assertThat(defaults.sort).isEqualTo(SortOrder.ADDED_AT)
        assertThat(defaults.descending).isTrue()
        assertThat(defaults.grouping).isEqualTo(Grouping.AUTHOR)
    }

    @Test
    fun `documented defaults are not inverted`() {
        // Умолчание — «Название», прямой порядок, без группировки («E-ink UX» §6.2):
        // обратный порядок по умолчанию показал бы библиотеку с конца.
        val defaults = LibrarySettings().toLibraryQueryDefaults()

        assertThat(defaults.sort).isEqualTo(SortOrder.TITLE)
        assertThat(defaults.descending).isFalse()
        assertThat(defaults.grouping).isEqualTo(Grouping.NONE)
    }
}
