package io.github.bookshelf.feature.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkProgress
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.paging.statusLabel
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.scan.DuplicateGroupUi
import io.github.bookshelf.feature.scan.ProblemFileUi
import io.github.bookshelf.feature.scan.ScanMode
import io.github.bookshelf.feature.scan.ScanUiState
import io.github.bookshelf.feature.scan.ScanViewModel
import io.github.bookshelf.feature.scan.scanProgressFraction

/** Сколько групп дубликатов помещается на «страницу». */
private const val DUPLICATES_PAGE_SIZE = 2

/**
 * Сканирование: прогресс, отчёт и дубликаты (docs/eink-ux.md §6.6–6.7).
 *
 * Прогресс обновляется не чаще раза в секунду — частые перерисоновки на e-ink вредны.
 * Отмена мягкая: уже найденное сохраняется, продолжение возможно с сохранённого курсора.
 * Работа с `ScanRepository` появится в Sprint 2, разрешение дубликатов — в Sprint 7.
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // «Отменить» должна работать как «Назад», когда отменять нечего.
    LaunchedEffect(Unit) { viewModel.setOnExit(onBack) }

    when (state.mode) {
        ScanMode.PROGRESS -> ProgressScreen(
            state = state,
            viewModel = viewModel,
            onBack = onBack,
            modifier = modifier,
        )

        ScanMode.REPORT -> ReportScreen(
            state = state,
            viewModel = viewModel,
            onBack = onBack,
            onOpenBook = onOpenBook,
            modifier = modifier,
        )

        ScanMode.DUPLICATES -> DuplicatesScreen(
            state = state,
            viewModel = viewModel,
            onBack = onBack,
            onOpenBook = onOpenBook,
            modifier = modifier,
        )
    }
}

/** Прогресс сканирования с мягкой отменой и продолжением. */
@Composable
private fun ProgressScreen(
    state: ScanUiState,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalEinkDimens.current
    val progress = state.progress

    EinkScaffold(
        title = "Сканирование",
        subtitle = progress?.sourceName?.ifEmpty { null },
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.OPEN -> viewModel.onResume()
                EinkAction.MENU -> viewModel.onShowReport()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(message = error, actionLabel = "Обновить", onAction = viewModel::onRefresh)
        }

        if (progress == null) {
            EinkEmptyState(
                title = "Сканирование ещё не запускалось",
                message = "Нажмите «Сканировать» — приложение пройдёт по дереву папки и найдёт книги. " +
                    "Найденное сохраняется, прерванный скан продолжится с того же места.",
                actionLabel = "Сканировать",
                onAction = viewModel::onStartScan,
                modifier = Modifier.weight(1f),
            )
        } else {
            EinkProgress(
                caption = "Сканирование… ${progress.filesSeen} из ${progress.filesTotal ?: 0}",
                progress = scanProgressFraction(progress.filesSeen, progress.filesTotal),
                detail = "Добавлено ${progress.added}, ошибок ${progress.failed}",
            )
            Column(modifier = Modifier.padding(horizontal = dimens.gutter)) {
                EinkListRow(title = "Просмотрено файлов", trailing = progress.filesSeen.toString())
                EinkListRow(title = "Добавлено", trailing = progress.added.toString())
                EinkListRow(title = "Обновлено", trailing = progress.updated.toString())
                EinkListRow(title = "Пропало", trailing = progress.removed.toString())
                EinkListRow(title = "Не разобрано", trailing = progress.failed.toString())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                EinkButton(text = "Отменить", onClick = viewModel::onCancel, modifier = Modifier.weight(1f))
                EinkButton(
                    text = "Продолжить",
                    onClick = viewModel::onResume,
                    enabled = state.canResume,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Отчёт по завершённому сканированию и проблемные файлы. */
@Composable
private fun ReportScreen(
    state: ScanUiState,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val report = state.report

    EinkScaffold(
        title = "Отчёт сканирования",
        subtitle = report?.durationLabel,
        modifier = modifier,
        onBack = onBack,
        actions = { EinkButton(text = "Дубликаты", onClick = viewModel::onShowDuplicates) },
        onKeyAction = { action ->
            when (action) {
                EinkAction.OPEN -> viewModel.onShowDuplicates()
                EinkAction.MENU -> viewModel.onShowProgress()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
    ) {
        if (report == null) {
            EinkEmptyState(
                title = "Отчёта пока нет",
                message = "Здесь появится итог скана: сколько добавлено, обновлено, пропало и не разобрано, " +
                    "и список проблемных файлов с причиной ошибки.",
                actionLabel = "К дубликатам",
                onAction = viewModel::onShowDuplicates,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.gutter),
            ) {
                EinkListRow(title = "Добавлено", trailing = report.added.toString())
                EinkListRow(title = "Обновлено", trailing = report.updated.toString())
                EinkListRow(title = "Пропало", trailing = report.removed.toString())
                EinkListRow(title = "Не разобрано", trailing = report.failed.toString())

                EinkSectionHeader(title = "Проблемные файлы", count = report.problemFiles.size)

                if (report.problemFiles.isEmpty()) {
                    Text(
                        text = "Проблемных файлов нет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.foreground,
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), userScrollEnabled = false) {
                        items(items = report.problemFiles, key = { file -> file.id }) { file ->
                            ProblemFileRow(file = file, onClick = { onOpenBook(file.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Строка проблемного файла: имя и причина, по которой его не удалось разобрать. */
@Composable
private fun ProblemFileRow(
    file: ProblemFileUi,
    onClick: () -> Unit,
) {
    EinkListRow(title = file.fileName, subtitle = file.reason, onClick = onClick)
}

/** Группы дубликатов: по одному решению на группу, без множественного выделения. */
@Composable
private fun DuplicatesScreen(
    state: ScanUiState,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalEinkDimens.current
    val pager = rememberEinkPagerState(pageSize = DUPLICATES_PAGE_SIZE, totalItems = state.duplicates.size)

    LaunchedEffect(state.duplicates.size) { pager.updateTotalItems(state.duplicates.size) }

    EinkScaffold(
        title = "Дубликаты",
        subtitle = "Файлы не удаляются — только записи библиотеки",
        modifier = modifier,
        onBack = onBack,
        actions = { EinkButton(text = "К отчёту", onClick = viewModel::onShowReport) },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.MENU -> viewModel.onShowReport()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Text(
                text = pager.statusLabel("групп"),
                style = MaterialTheme.typography.labelMedium,
                color = LocalEinkColors.current.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            )
        },
    ) {
        Box(
                modifier = Modifier.weight(1f),
            ) {
            if (state.duplicates.isEmpty()) {
                EinkEmptyState(
                    title = "Дубликатов не найдено",
                    message = "Здесь появятся группы книг с одинаковым contentHash: «оставить этот», " +
                        "«скрыть остальные», «удалить из библиотеки» — по одному решению на группу.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimens.gutter),
                    verticalArrangement = Arrangement.spacedBy(dimens.gutter),
                ) {
                    state.duplicates.drop(pager.offset).take(pager.pageSize).forEach { group ->
                        DuplicateGroupBlock(
                            group = group,
                            onKeep = viewModel::onKeepCopy,
                            onRemove = viewModel::onRemoveDuplicates,
                            onOpenBook = onOpenBook,
                        )
                    }
                }
            }
        }
    }
}

/** Одна группа дубликатов: копии и два решения по группе. */
@Composable
private fun DuplicateGroupBlock(
    group: DuplicateGroupUi,
    onKeep: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val dimens = LocalEinkDimens.current
    val keeper = group.copies.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
        EinkSectionHeader(title = group.title, count = group.copies.size)
        group.copies.forEach { copy ->
            EinkListRow(
                title = copy.path,
                subtitle = copy.sizeLabel,
                trailing = if (copy.isKeeper) "оставить" else null,
                selected = copy.isKeeper,
                onClick = { onOpenBook(copy.bookId) },
            )
        }
        if (keeper != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
                EinkButton(
                    text = "Оставить эту",
                    onClick = { onKeep(group.id, keeper.bookId) },
                    modifier = Modifier.weight(1f),
                )
                EinkButton(
                    text = "Убрать остальные",
                    onClick = { onRemove(group.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
