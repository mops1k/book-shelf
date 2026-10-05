package io.github.bookshelf.feature.library.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import io.github.bookshelf.feature.library.shouldLoadMore
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Догрузка следующего окна книг.
 *
 * Книги приходят окнами по пятьдесят (`SHELF_WINDOW_SIZE`): следующая порция просится за десять
 * книг до конца загруженного, чтобы для человека подгрузка не выглядела как «палец упёрся в пустое
 * место». Ключи эффекта — размер окна и общее число книг: после прихода порции условия считаются
 * заново, и если окно всё ещё не заполнило экран, догрузка повторится.
 *
 * Живёт отдельным файлом: у `LibraryScreen` порог detekt `TooManyFunctions` по файлу (25), и
 * вынесенный помощник иначе упирался бы в него — это же правило записано в `detekt.yml`.
 *
 * @param gridState состояние сетки книг: по нему видно, докуда человек доскроллил.
 * @param loaded сколько книг уже на полке.
 * @param total сколько книг в отборе всего.
 * @param onLoadMore команда «проси следующее окно».
 */
@Composable
internal fun LoadMoreOnScroll(
    gridState: LazyGridState,
    loaded: Int,
    total: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(gridState, loaded, total) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (shouldLoadMore(lastVisible, loaded = loaded, total = total)) onLoadMore()
            }
    }
}
