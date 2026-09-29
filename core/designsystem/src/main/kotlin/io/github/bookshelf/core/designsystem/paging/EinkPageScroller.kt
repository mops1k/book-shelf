package io.github.bookshelf.core.designsystem.paging

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Листание списка аппаратными клавишами Page Up/Page Down.
 *
 * Возвращает функцию «пролистать на [direction] экранов», где высота берётся из самого списка:
 * фиксированная величина не совпала бы с реальным вьюпортом (плотность и системные панели
 * на e-ink разные), и последняя строка либо оставалась за кадром, либо листание «утыкалось».
 *
 * Зачем в дизайн-системе: раньше каждый экран писал свою копию, и в трёх местах из четырёх
 * прокрутка не работала вовсе — состояние списка было не тем, к которому подключён пагинатор.
 */
@Composable
fun rememberEinkListPageScroller(listState: LazyListState): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    return { direction ->
        val amount = listState.layoutInfo.viewportSize.height
        if (amount > 0) {
            scope.launch { listState.scrollBy((direction * amount).toFloat()) }
        }
    }
}

/** Листание сетки аппаратными клавишами: прокрутка на высоту экрана. */
@Composable
fun rememberEinkGridPageScroller(gridState: LazyGridState): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    return { direction ->
        val amount = gridState.layoutInfo.viewportSize.height
        if (amount > 0) {
            scope.launch { gridState.scrollBy((direction * amount).toFloat()) }
        }
    }
}

