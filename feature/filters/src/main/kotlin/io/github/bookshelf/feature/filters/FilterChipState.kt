package io.github.bookshelf.feature.filters

/**
 * Чистая логика выбора фасетов (docs/architecture.md §3.5, ADR-008).
 *
 * Вынесена из ViewModel и Composable отдельным файлом: правило «ИЛИ внутри фасета,
 * И между фасетами» — единственная часть экрана, которую можно проверить тестом
 * без Compose, БД и устройства.
 */

/**
 * Переключить значение внутри одного фасета: повторный выбор снимает его.
 *
 * Логика «ИЛИ внутри фасета, И между фасетами» реализуется запросом с несколькими
 * множествами выбора, а не в памяти (docs/decisions.md ADR-008).
 */
fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/** Сколько фасетов выбрано — для кнопки «Фильтры (N)» и подписи «Активно: N». */
fun activeFilterCount(selection: FilterSelectionUi): Int =
    selection.authors.size +
        selection.series.size +
        selection.formats.size +
        selection.statuses.size +
        selection.tags.size

/** Есть ли что сбрасывать. */
fun hasActiveFilters(selection: FilterSelectionUi): Boolean = activeFilterCount(selection) > 0
