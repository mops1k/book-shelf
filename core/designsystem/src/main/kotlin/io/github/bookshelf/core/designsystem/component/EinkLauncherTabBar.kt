package io.github.bookshelf.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bookshelf.core.designsystem.R

/**
 * Панель «Полка | Приложения» для двух главных экранов лаунчера (ADR-015).
 *
 * Подписи живут в дизайн-системе, а не в двух feature-модулях: обе вкладки показываются на обоих
 * экранах, и дублирование строк неизбежно разъехалось бы при правке. Неактивная вкладка ведёт
 * себя как «перейти», активная — как «ничего не делать» (решение 02.11.2026).
 *
 * @param selectedApps `true` — активна вкладка приложений, `false` — полка.
 * @param onSelectShelf переход на полку; на самой полке не вызывается.
 * @param onSelectApps переход к списку приложений; на самом экране не вызывается.
 */
@Composable
fun EinkLauncherTabBar(
    selectedApps: Boolean,
    onSelectShelf: () -> Unit,
    onSelectApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(R.string.eink_tab_shelf),
        stringResource(R.string.eink_tab_apps),
    )
    EinkTabBar(
        labels = labels,
        selectedIndex = if (selectedApps) 1 else 0,
        onSelect = { index -> if (index == 0) onSelectShelf() else onSelectApps() },
        modifier = modifier,
    )
}
