package io.github.bookshelf.feature.catalogs.ui

import io.github.bookshelf.feature.catalogs.messageText
import io.github.bookshelf.feature.catalogs.namedText
import io.github.bookshelf.feature.catalogs.FeedLabel
import io.github.bookshelf.feature.catalogs.FeedUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkCheckbox
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkProgress
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.paging.rememberEinkGridPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.catalogs.CatalogUi
import io.github.bookshelf.feature.catalogs.CatalogsMode
import io.github.bookshelf.feature.catalogs.CatalogsUiState
import io.github.bookshelf.feature.catalogs.CatalogsViewModel
import io.github.bookshelf.feature.catalogs.downloadsButtonLabel
import io.github.bookshelf.feature.catalogs.entryCoverOf
import io.github.bookshelf.feature.catalogs.DownloadUi
import io.github.bookshelf.feature.catalogs.FeedEntryUi
import io.github.bookshelf.feature.catalogs.R
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.theme.EinkDimens
import io.github.bookshelf.feature.catalogs.AddCatalogFormUi

@Composable
internal fun catalogsList(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    listState: LazyListState,
    dimens: EinkDimens,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        listItems(items = state.catalogs, key = { catalog -> catalog.id }) { catalog ->
            Column(modifier = Modifier.fillMaxWidth()) {
                EinkListRow(
                    title = catalog.title,
                    subtitle = catalog.url,
                    trailing = catalogTrailing(catalog),
                    // «⋯» — единственный вход к правке и удалению: раньше каталог
                    // можно было только добавить, а убрать или переименовать — нет.
                    trailingContent = {
                        EinkButton(
                            text = "⋯",
                            onClick = { viewModel.catalogActions.onToggleActions(catalog.id) },
                        )
                    },
                    onClick = { viewModel.onOpenCatalog(catalog.id) },
                )
                if (state.actionsForCatalogId == catalog.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.gutter),
                        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                    ) {
                        EinkButton(
                            text = stringResource(R.string.catalogs_action_edit),
                            onClick = { viewModel.catalogActions.onAskEdit(catalog.id) },
                            modifier = Modifier.weight(1f),
                        )
                        EinkButton(
                            text = stringResource(R.string.catalogs_action_delete),
                            onClick = { viewModel.catalogActions.onAskDelete(catalog.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (state.confirmingDeleteCatalogId == catalog.id) {
                    EinkConfirmRow(
                        question = stringResource(
                            R.string.catalogs_delete_confirm,
                            catalog.title,
                        ),
                        confirmLabel = stringResource(R.string.catalogs_action_delete),
                        onConfirm = viewModel.catalogActions::onConfirmDelete,
                        onCancel = viewModel.catalogActions::onCancelDelete,
                        modifier = Modifier.padding(horizontal = dimens.gutter),
                    )
                }
            }
        }
    }
}

/** Подпись состояния каталога в списке. */
@Composable
internal fun catalogTrailing(catalog: CatalogUi): String = when {
    !catalog.isAvailable -> stringResource(R.string.catalogs_catalog_unavailable)
    catalog.requiresAuth -> stringResource(R.string.catalogs_catalog_login)
    catalog.lastOpenedLabel != null -> catalog.lastOpenedLabel.resolve()
    else -> stringResource(R.string.catalogs_catalog_ready)
}

/** Подпись записи фида: форматы, размер, язык. */
@Composable
internal fun entryMeta(entry: FeedEntryUi): String {
    val parts = buildList {
        if (entry.formats.isNotEmpty()) add(entry.formats.joinToString(separator = ", "))
        entry.sizeLabel?.let { size -> add(size.resolve()) }
        entry.language?.let { add(it) }
    }
    return parts.joinToString(separator = " · ")
}

/**
 * Подпись уровня фида словами: имя с сервера — как есть, выбранные приложением слова — из ресурсов.
 */
@Composable
internal fun FeedLabel.label(): String = messageText?.resolve() ?: namedText.orEmpty()

/** Крошки одной строкой; пустой список — подсказка пустого состояния. */
@Composable
internal fun crumbText(feed: FeedUi?): String {
    val labels = feed?.breadcrumbs.orEmpty()
    if (labels.isEmpty()) return stringResource(R.string.catalogs_browser_empty_title)
    val parts = ArrayList<String>(labels.size)
    for (label in labels) parts.add(label.label())
    return parts.joinToString(separator = " / ")
}


/** Кнопки шапки браузера фида: очередь загрузок и возврат к списку каталогов. */
@Composable
internal fun FeedBrowserActions(
    downloads: List<DownloadUi>,
    onOpenDownloads: () -> Unit,
    onBackToList: () -> Unit,
) {
    // Дорога к очереди прямо из фида: раньше после «Скачать» карточка молча закрывалась,
    // а «Загрузки» были доступны только из списка каталогов — в разделах фида их не было
    // видно вовсе (жалоба Александра, 28.09.2026).
    EinkButton(
        text = downloadsButtonLabel(downloads).resolve(),
        onClick = onOpenDownloads,
    )
    EinkButton(
        text = stringResource(R.string.catalogs_browser_feed_title),
        onClick = onBackToList,
    )
}


/**
 * Содержимое браузера фида: поиск по фиду, баннер ошибки, индикатор загрузки и сетка записей.
 *
 * Вынесено из `FeedBrowserScreen` отдельной функцией: экран перевалил за предел длины detekt,
 * а содержимое — цельный кусок, зависящий от состояния фида, его записей и прокрутки.
 */
@Composable
internal fun ColumnScope.FeedBrowserContent(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    feed: FeedUi?,
    entries: List<FeedEntryUi>,
    entriesGridState: LazyGridState,
    onOpenEntry: (String, String, String?) -> Unit,
) {
    val dimens = LocalEinkDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkTextField(
            value = state.feedQuery,
            onValueChange = viewModel::onFeedQueryChange,
            placeholder = stringResource(R.string.catalogs_search_title),
            modifier = Modifier.weight(1f),
        )
        EinkButton(text = stringResource(R.string.catalogs_search_action), onClick = viewModel::onSearchInFeed)
    }

    // Ошибка загрузки фида раньше не показывалась вовсе: нажатие на раздел, который не
    // открылся, выглядело как «ничего не произошло». Баннер стоит над списком, а не в подвале,
    // потому что в подвале уже подпись о записях.
    state.error?.let { message ->
        EinkErrorBanner(
            message = message.resolve(),
            actionLabel = stringResource(R.string.catalogs_action_retry),
            onAction = viewModel::onReloadFeed,
        )
    }

    // Индикация загрузки: переход по разделам ждёт сеть, и без подписи экран выглядел
    // зависшим — пользователь не понимал, идёт работа или приложение задумалось.
    if (state.isLoading) {
        LoadingRow(label = stringResource(R.string.catalogs_loading_section))
    }

    Box(
            modifier = Modifier.weight(1f),
        ) {
        if (entries.isEmpty()) {
            when {
                // Первый фид: пустая подсказка «фид не загружен» противоречила бы индикатору.
                state.isLoading -> LoadingRow(label = stringResource(R.string.catalogs_loading_feed))

                feed == null -> EinkEmptyState(
                    title = stringResource(R.string.catalogs_feed_empty_title),
                    message = stringResource(R.string.catalogs_feed_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )

                // Фид загружен, но записей в нём нет: так отвечает поиск, которому нечего
                // показать. «Фид не загружен» здесь было бы неправдой, а «Назад» — выходом.
                else -> EinkEmptyState(
                    title = stringResource(R.string.catalogs_nothing_found_title),
                    message = stringResource(R.string.catalogs_nothing_found_message),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            // Список один и прокручивается целиком; когда пользователь доходит до конца,
            // следующая страница фида догружается сама (rel="next").
            FeedContent(
                entries = entries,
                gridState = entriesGridState,
                columns = GridCells.Adaptive(minSize = dimens.coverMinWidth),
                hasNextPage = feed?.hasNextPage == true,
                isLoadingMore = state.isLoadingMore,
                onOpen = { entry -> viewModel.onOpenEntry(entry.id, onOpenEntry) },
                onLoadMore = viewModel::onLoadNextPage,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}


/** Поля формы каталога: имя, адрес, логин, пароль и «открыть сразу». */
@Composable
internal fun AddCatalogFields(
    form: AddCatalogFormUi,
    isEditing: Boolean,
    viewModel: CatalogsViewModel,
) {
    val dimens = LocalEinkDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.gutter),
    ) {
        EinkTextField(
            value = form.title,
            onValueChange = { viewModel.onFormChange(form.copy(title = it)) },
            label = stringResource(R.string.catalogs_form_name),
            placeholder = stringResource(R.string.catalogs_form_name_placeholder),
        )
        EinkTextField(
            value = form.url,
            onValueChange = { viewModel.onFormChange(form.copy(url = it)) },
            label = stringResource(R.string.catalogs_form_url),
            placeholder = "https://example.org/opds",
        )
        EinkTextField(
            value = form.username,
            onValueChange = { viewModel.onFormChange(form.copy(username = it)) },
            label = stringResource(R.string.catalogs_form_login),
        )
        // Пароль не логируется и не экспортируется: в БД попадает только alias.
        EinkTextField(
            value = form.password,
            onValueChange = { viewModel.onFormChange(form.copy(password = it)) },
            label = if (isEditing) {
                stringResource(R.string.catalogs_form_password_new)
            } else {
                stringResource(R.string.catalogs_form_password)
            },
            visualTransformation = PasswordVisualTransformation(),
        )
        EinkCheckbox(
            checked = form.openImmediately,
            onCheckedChange = { viewModel.onFormChange(form.copy(openImmediately = it)) },
            label = stringResource(R.string.catalogs_form_open_now),
        )
    }
}
