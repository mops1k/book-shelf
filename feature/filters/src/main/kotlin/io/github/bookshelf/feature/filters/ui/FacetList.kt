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
 * Живая сводка по критериям: сколько значений доступно в каждом фасете прямо сейчас.
 *
 * Строка закреплена над списком и видна всегда — в том числе когда раскрыта чужая секция и её
 * строки уехали вниз. Без неё пересчёт фасетов на каждый тап происходил, но оставался за
 * пределами экрана: выбранный автор сужал серии, а увидеть это можно было только вернувшись
 * на полку и открыв фильтры заново. На разборе 28.09.2026 это выглядело как «обновляется
 * только после Применить».
 */
internal fun facetOverview(sections: List<FacetSectionUi>): String =
    sections.joinToString(separator = " · ") { section ->
        // «Авторы 16» — сколько значений осталось доступно после остальных фильтров.
        "${section.kind.shortTitle} ${section.items.size}"
    }

/** Короткие подписи критериев для сводки: в одну строку должно влезть пять чисел. */
internal val FacetKind.shortTitle: String
    get() = when (this) {
        FacetKind.AUTHORS -> "Авторы"
        FacetKind.SERIES -> "Серии"
        FacetKind.TAGS -> "Жанры"
        FacetKind.FORMATS -> "Форматы"
        FacetKind.STATUSES -> "Статус"
    }


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
