package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import io.github.bookshelf.core.designsystem.R
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.input.onEinkKeys
import io.github.bookshelf.core.designsystem.refresh.RefreshReason
import io.github.bookshelf.core.designsystem.refresh.RequestRefreshOnEnter
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.designsystem.theme.LocalVolumeKeysAsPageTurn

/**
 * Каркас экрана: тулбар в одну строку, содержимое, нижняя строка, обработка аппаратных кнопок
 * и полный перерисон при входе на экран.
 *
 * Тулбар принципиально **не переносится** на вторую строку. Дизайн-док (docs/eink-ux.md §6.2)
 * требует одну строку «заголовок + 2–3 действия + меню»: каждый лишний ряд шапки — это минус
 * строка книг на полке, а «лестница» из кнопок вразнобой ещё и читается как случайный список.
 * Поэтому в [actions] место для двух-трёх частых команд, а остальные экран складывает в [menu]:
 * оно открывается кнопкой «Ещё» или аппаратной клавишей MENU.
 *
 * Меню — непрозрачная панель **поверх** содержимого, а не всплывающий слой с тенью: на e-ink
 * полупрозрачность, тени и анимации запрещены (docs/eink-ux.md §5). Содержимое при этом остаётся
 * в дереве композиции, поэтому позиция прокрутки списка не сбрасывается при открытии меню.
 *
 * @param volumeKeysAsPageTurn значение по умолчанию берётся из `LocalVolumeKeysAsPageTurn` —
 *   настройки приложения (docs/eink-ux.md §3.2). Параметр оставлен: им переопределяют поведение
 *   точечно (тесты, экран, где громкость нужна как громкость), не поднимая локал.
 */
@Composable
fun EinkScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    menuTitle: String? = null,
    footer: @Composable (() -> Unit)? = null,
    volumeKeysAsPageTurn: Boolean = LocalVolumeKeysAsPageTurn.current,
    onKeyAction: (EinkAction) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    var isMenuOpen by remember { mutableStateOf(false) }
    val keyFocus = remember { FocusRequester() }

    RequestRefreshOnEnter(RefreshReason.SCREEN_CHANGED)

    // Аппаратные клавиши приходят только узлу в фокусе. Без явного запроса фокуса `onEinkKeys`
    // не получал ни одного события: на устройстве 28.09.2026 не работали ни Page Up/Down
    // (полка не листалась), ни MENU (меню «Ещё» не открывалось). Индикатор фокуса не нужен —
    // на e-ink подсветка и анимации запрещены, поэтому indication выключен.
    RequestKeyFocus(keyFocus = keyFocus)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .focusRequester(keyFocus)
            // Индикатор фокуса не рисуется: `indication` к узлу не подключён, а анимации и
            // подсветка на e-ink запрещены (docs/eink-ux.md §5).
            .einkFocusAndKeys(
                volumeKeysAsPageTurn = volumeKeysAsPageTurn,
                hasMenu = menu != null,
                onToggleMenu = { isMenuOpen = !isMenuOpen },
                onKeyAction = onKeyAction,
            ),
    ) {
        ScaffoldHeader(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            actions = actions,
            hasMenu = menu != null,
            isMenuOpen = isMenuOpen,
            onToggleMenu = { isMenuOpen = !isMenuOpen },
        )

        EinkDivider()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize(), content = content)

            val openedMenu = menu
            if (isMenuOpen && openedMenu != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        // Панель обязана и поглощать касания: иначе пустые места меню
                        // «протекают» в список под ним.
                        .clickableNoIndication(onClick = {})
                        .padding(horizontal = dimens.gutter),
                    verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                ) {
                    EinkSectionHeader(title = menuTitle ?: stringResource(R.string.eink_menu_title))
                    openedMenu.invoke(this, { isMenuOpen = false })
                }
            }
        }

        if (footer != null) {
            EinkDivider()
            footer()
        }
    }
}


/**
 * Шапка экрана: «Назад», заголовок с подписью, действия экрана и кнопка «Ещё».
 *
 * Вынесена из `EinkScaffold` отдельной функцией: она перевалила за предел длины detekt, а шапка —
 * цельный кусок, который не зависит от содержимого и подвала экрана.
 */
@Composable
private fun ScaffoldHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    hasMenu: Boolean,
    isMenuOpen: Boolean,
    onToggleMenu: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.touchComfort)
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        if (onBack != null) {
            EinkButton(text = stringResource(R.string.eink_action_back), onClick = onBack)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
        if (hasMenu) {
            EinkButton(
                text = if (isMenuOpen) {
                    stringResource(R.string.eink_action_close)
                } else {
                    stringResource(R.string.eink_action_more)
                },
                onClick = onToggleMenu,
                primary = isMenuOpen,
            )
        }
    }
}


/**
 * Фокус узла и разбор аппаратных клавиш.
 *
 * Клавиши приходят только узлу в фокусе: без явного запроса фокуса `onEinkKeys` не получал ни
 * одного события — на устройстве 28.09.2026 не работали ни Page Up/Down, ни MENU. Индикатор
 * фокуса не подключён: анимации и подсветка на e-ink запрещены (docs/eink-ux.md §5).
 *
 * Вынесено из `EinkScaffold` отдельной функцией: та перевалила за предел длины detekt.
 */
@Composable
private fun RequestKeyFocus(keyFocus: FocusRequester) {
    LaunchedEffect(Unit) {
        runCatching { keyFocus.requestFocus() }
    }
}

/** Что делает аппаратная клавиша: MENU открывает меню, остальное отдаётся экрану. */
@Composable
private fun Modifier.einkFocusAndKeys(
    volumeKeysAsPageTurn: Boolean,
    hasMenu: Boolean,
    onToggleMenu: () -> Unit,
    onKeyAction: (EinkAction) -> Unit,
): Modifier = this
    .focusable(
        interactionSource = remember { MutableInteractionSource() },
    )
    .onEinkKeys(
        volumeKeysAsPageTurn = volumeKeysAsPageTurn,
        onAction = { action ->
            if (action == EinkAction.MENU && hasMenu) {
                onToggleMenu()
            } else {
                onKeyAction(action)
            }
        },
    )
