package io.github.bookshelf.feature.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.designsystem.component.EinkChip
import io.github.bookshelf.core.designsystem.component.EinkDivider
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.filters.FacetChipUi
import io.github.bookshelf.feature.filters.FacetKind
import io.github.bookshelf.feature.filters.FacetSectionUi
import io.github.bookshelf.feature.filters.FilterSelectionUi

/**
 * Список фасетов с чипами и поиском.
 *
 * Один компонент на два экрана: фильтры библиотеки и правила умной полки. Раньше он был частью
 * экрана фильтров, и редактор правил пришлось бы рисовать второй копией — а «полка по жанру»
 * обязана выглядеть и считаться так же, как фильтр по тому же жанру (ADR-008).
 */

/** Сколько чипов помещается в одну строку. */
private const val CHIPS_PER_ROW = 2

/**
 * Живая сводка по критериям: сколько значений доступно в каждом разделе прямо сейчас.
 *
 * Строка закреплена над списком и видна всегда — в том числе когда раскрыта чужая секция и её
 * строки уехали вниз. Без неё пересчёт фасетов на каждый тап происходил, но оставался за
 * пределами экрана: выбранный автор сужал серии, а увидеть это можно было только вернувшись
 * на полку и открыв фильтры заново. На разборе 28.09.2026 это выглядело как «обновляется
 * только после Применить».
 *
 * Формулировка — «Значений на выбор: 20 авторов · 14 серий · …», а не «Авторы 20 · Серии 14 · …»:
 * число без пояснения читалось как «выбрано 20 авторов» (замечание Александра, 29.09.2026).
 * Пустой набор разделов даёт пустую строку: там экран показывает «фильтров пока нет», и подпись
 * «Значений на выбор:» без чисел выглядела бы обрезанной.
 */
internal fun facetOverview(sections: List<FacetSectionUi>): String {
    if (sections.isEmpty()) return ""
    val parts = sections.joinToString(separator = " · ") { section ->
        // «20 авторов» — сколько значений осталось доступно после остальных фильтров.
        "${section.items.size} ${section.kind.availableWord(section.items.size)}"
    }
    return "Значений на выбор: $parts"
}

/** Слово критерия в нужной форме: «1 автор», «2 автора», «5 авторов». */
internal fun FacetKind.availableWord(count: Int): String = when (this) {
    FacetKind.AUTHORS -> russianPlural(count, "автор", "автора", "авторов")
    FacetKind.SERIES -> russianPlural(count, "серия", "серии", "серий")
    FacetKind.TAGS -> russianPlural(count, "жанр", "жанра", "жанров")
    FacetKind.FORMATS -> russianPlural(count, "формат", "формата", "форматов")
    FacetKind.STATUSES -> russianPlural(count, "статус", "статуса", "статусов")
}

/**
 * Русская форма слова по числу: 1 автор, 2 автора, 5 авторов, но 11 авторов и снова 21 автор.
 *
 * Исключение для 11–14 обязательно: «11 автор» и «12 автора» — именно те ошибки, которые выдают
 * машинный перевод. Общий помощник, потому что та же арифметика нужна любой подписи с числом.
 */
internal fun russianPlural(count: Int, one: String, few: String, many: String): String {
    val lastTwo = count % CENTURY
    val last = count % DECADE
    return when {
        lastTwo in TEENS_START..TEENS_END -> many
        last == 1 -> one
        last in FEW_START..FEW_END -> few
        else -> many
    }
}

/** Разряды счёта: единицы, сотни и «подростковый» разряд 11–14, который всегда даёт «много». */
private const val DECADE = 10
private const val CENTURY = 100
private const val TEENS_START = 11
private const val TEENS_END = 14
private const val FEW_START = 2
private const val FEW_END = 4


/**
 * Секции фасетов: свёрнутая строка критерия и раскрытый список чипов с поиском.
 *
 * Раскрыта максимум одна секция — иначе экран растёт вместе с числом авторов, и до нижних
 * критериев нельзя добраться. В каждой строке — до [CHIPS_PER_ROW] чипов.
 */
@Composable
internal fun FacetList(
    sections: List<FacetSectionUi>,
    selected: FilterSelectionUi,
    onToggle: (FacetKind, String) -> Unit,
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalEinkDimens.current
    val colors = LocalEinkColors.current
    var expandedKind by remember { mutableStateOf<FacetKind?>(null) }
    var search by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        // Живая сводка закреплена над списком и не прокручивается: числа фасетов видны в момент
        // тапа по чипу, даже если раскрытая секция вытеснила чужие строки вниз.
        Text(
            text = facetOverview(sections),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        )
        EinkDivider()

        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = dimens.gutter),
        ) {
            sections.forEach { section ->
                val isOpen = expandedKind == section.kind
                item(key = "section:${section.kind}") {
                    FacetRow(
                        section = section,
                        selected = selected,
                        expanded = isOpen,
                        onClick = {
                            expandedKind = if (isOpen) null else section.kind
                            search = ""
                        },
                    )
                }
                if (isOpen) {
                    item(key = "search:${section.kind}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimens.gutterSmall),
                        ) {
                            EinkTextField(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = "Поиск: ${section.title.lowercase()}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    val visible = section.items.filter { chip ->
                        search.isBlank() || chip.label.contains(search.trim(), ignoreCase = true)
                    }
                    if (visible.isEmpty()) {
                        item(key = "empty:${section.kind}") {
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.foreground,
                            )
                        }
                    }
                    itemsIndexed(
                        items = visible.chunked(CHIPS_PER_ROW),
                        key = { index, _ -> "row:${section.kind}:$index" },
                    ) { _, rowItems ->
                        Row(
                            modifier = Modifier.padding(vertical = dimens.gutterSmall / 2),
                            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                        ) {
                            rowItems.forEach { chip ->
                                FacetChip(
                                    chip = chip,
                                    selected = isFacetSelected(selected, section.kind, chip.id),
                                    onClick = { onToggle(section.kind, chip.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Строка критерия: название фасета и выбранные значения.
 *
 * Свёрнутая строка занимает одну строку экрана — именно поэтому экран фильтров больше не растёт
 * с числом авторов и серий. Полный список значений открывается касанием.
 */
@Composable
private fun FacetRow(
    section: FacetSectionUi,
    selected: FilterSelectionUi,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val chosen = section.items.filter { chip -> isFacetSelected(selected, section.kind, chip.id) }
    EinkListRow(
        title = "${section.title}: " + chosen.joinToString(", ") { chip -> chip.label }.ifEmpty { "не выбрано" },
        subtitle = if (chosen.isEmpty()) {
            "доступно: ${section.items.size}"
        } else {
            "выбрано: ${chosen.size} из ${section.items.size}"
        },
        trailing = if (expanded) "▲" else "▼",
        // Раскрытый критерий инвертируется: видно, чьи значения показаны ниже.
        selected = expanded,
        onClick = onClick,
    )
}

/** Один чип фасета с числом книг. */
@Composable
private fun FacetChip(
    chip: FacetChipUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    EinkChip(
        label = chip.label,
        count = chip.count,
        selected = selected,
        onClick = onClick,
    )
}

/** Выбран ли чип в своём фасете. */
private fun isFacetSelected(
    selection: FilterSelectionUi,
    kind: FacetKind,
    facetId: String,
): Boolean = when (kind) {
    FacetKind.AUTHORS -> facetId in selection.authors
    FacetKind.SERIES -> facetId in selection.series
    FacetKind.TAGS -> facetId in selection.tags
    FacetKind.FORMATS -> facetId in selection.formats
    FacetKind.STATUSES -> facetId in selection.statuses
}
