package io.github.bookshelf.core.designsystem.paging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Состояние постраничного листания.
 *
 * Приложение листает библиотеку **страницами**, а не скроллом: на e-ink каждый кадр скролла —
 * это мерцание и «призраки». Отсюда требование к запросам: страница запрашивается у БД
 * через `LIMIT/OFFSET`, а не «вся библиотека в память» (docs/architecture.md §3, §4).
 */
@Stable
class EinkPagerState internal constructor(
    initialPageSize: Int,
    initialTotalItems: Int,
) {
    var pageSize: Int by mutableIntStateOf(initialPageSize)
        private set

    var totalItems: Int by mutableIntStateOf(initialTotalItems)
        private set

    var page: Int by mutableIntStateOf(0)
        private set

    val pageCount: Int
        get() = if (totalItems <= 0) 1 else ((totalItems + pageSize - 1) / pageSize).coerceAtLeast(1)

    val hasPrevious: Boolean get() = page > 0

    val hasNext: Boolean get() = page < pageCount - 1

    /** Смещение для запроса страницы: `OFFSET [offset] LIMIT [pageSize]`. */
    val offset: Int get() = page * pageSize

    /** Диапазон индексов элементов текущей страницы (для «Стр. 3/12 · 47 книг»). */
    val visibleRange: IntRange
        get() = if (totalItems == 0) IntRange.EMPTY else offset until minOf(offset + pageSize, totalItems)

    fun next() {
        if (hasNext) page += 1
    }

    fun previous() {
        if (hasPrevious) page -= 1
    }

    fun first() {
        page = 0
    }

    /** Вызывается, когда изменилось число книг (после фильтра, скана, удаления). */
    fun updateTotalItems(total: Int) {
        totalItems = total.coerceAtLeast(0)
        if (page > pageCount - 1) page = pageCount - 1
    }

    fun updatePageSize(size: Int) {
        if (size <= 0 || size == pageSize) return
        val firstVisible = offset
        pageSize = size
        page = (firstVisible / size).coerceIn(0, pageCount - 1)
    }
}

@Composable
fun rememberEinkPagerState(
    pageSize: Int,
    totalItems: Int = 0,
): EinkPagerState = remember(pageSize) {
    EinkPagerState(initialPageSize = pageSize, initialTotalItems = totalItems)
}

/** Подпись под списком: «Стр. 3/12 · 47 книг». */
fun EinkPagerState.statusLabel(totalLabel: String): String =
    "Стр. ${page + 1}/$pageCount · $totalItems $totalLabel"
