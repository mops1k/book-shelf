package io.github.bookshelf.feature.library.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.SortOrder

/**
 * Сбросить прокрутку полки при смене порядка.
 *
 * Дефект, ради которого это появилось (разбор жалобы Александра 08.10.2026): после смены сортировки
 * список оставался прокрученным на прежнем месте, и человек видел середину нового порядка — это
 * выглядело как «сортировка не применилась». Ключи эффекта — сама тройка порядка: смена фильтров
 * или вида сюда не входит, у них своя причина начинать с начала.
 *
 * Отдельный файл, а не функция в `LibraryScreen.kt`: у того файла порог detekt `TooManyFunctions`
 * уже на пределе, и помощник лучше держать рядом по смыслу, чем сжимать экран.
 */
@Composable
internal fun ScrollToTopOnOrderChange(
    gridState: LazyGridState,
    sort: SortOrder,
    descending: Boolean,
    grouping: Grouping,
) {
    LaunchedEffect(sort, descending, grouping) {
        gridState.scrollToItem(0)
    }
}
