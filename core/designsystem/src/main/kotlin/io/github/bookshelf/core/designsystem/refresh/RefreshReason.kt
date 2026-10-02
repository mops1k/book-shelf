package io.github.bookshelf.core.designsystem.refresh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Причина, по которой нужен полный перерисон экрана.
 *
 * Список закрытый: `EinkRefreshHost` в `:core:eink` знает, какие события действительно
 * требуют полного обновления, а какие можно отрисовать частично («E-ink UX» §4).
 */
enum class RefreshReason {
    SCREEN_CHANGED,
    DIALOG_OPENED,
    DIALOG_CLOSED,
    PAGE_CHANGED,
    SCAN_STARTED,
    SCAN_FINISHED,
    FILTER_APPLIED,
    MANUAL,
}

/**
 * Точка запроса перерисона.
 *
 * Дизайн-система намеренно не знает про `:core:eink`: она только объявляет «здесь нужен полный
 * кадр». Реализацию подставляет `:app` через `CompositionLocalProvider`, поэтому UI-модули
 * тестируются без вендорных SDK, а на устройстве без поддержки режимов вызов становится no-op.
 */
val LocalEinkRefresh = staticCompositionLocalOf<(RefreshReason) -> Unit> { { _ -> } }

/** Запросить полный перерисон при входе на экран (смена экрана — главный источник «призраков»). */
@Composable
fun RequestRefreshOnEnter(reason: RefreshReason = RefreshReason.SCREEN_CHANGED) {
    val requestRefresh = LocalEinkRefresh.current
    LaunchedEffect(reason) { requestRefresh(reason) }
}
