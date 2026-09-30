package io.github.bookshelf.feature.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.scan.ProblemFileUi
import io.github.bookshelf.feature.scan.canOpenReport
import io.github.bookshelf.feature.scan.ScanMode
import io.github.bookshelf.feature.scan.ScanUiState
import io.github.bookshelf.feature.scan.ScanViewModel
import io.github.bookshelf.feature.scan.scanProgressCaption
import io.github.bookshelf.feature.scan.scanProgressFraction
import io.github.bookshelf.feature.scan.scanStatusLabel
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.feature.scan.R

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
    // «К полке» ведёт на предыдущий экран — сюда же, куда и стрелка назад.
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

    }
}

/** Прогресс сканирования: пауза во время прогона, продолжение с сохранённого места. */
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
        title = stringResource(R.string.scan_title),
        subtitle = progress?.sourceName?.ifEmpty { null },
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                // Центральная кнопка продолжает скан, когда он стоит на паузе, и уходит на полку,
                // когда продолжать нечего.
                EinkAction.OPEN -> if (state.canResume) viewModel.onResume() else viewModel.onOpenLibrary()
                EinkAction.MENU -> viewModel.onShowReport()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = stringResource(R.string.scan_action_refresh),
                onAction = viewModel::onRefresh,
            )
        }

        if (progress == null) {
            EinkEmptyState(
                title = stringResource(R.string.scan_idle_title),
                message = stringResource(R.string.scan_idle_message),
                actionLabel = stringResource(R.string.scan_action_scan),
                onAction = viewModel::onStartScan,
                modifier = Modifier.weight(1f),
            )
        } else {
            EinkProgress(
                caption = scanProgressCaption(progress).resolve(),
                progress = scanProgressFraction(progress.filesSeen, progress.filesTotal),
                detail = stringResource(
                    R.string.scan_status_detail,
                    stringResource(scanStatusLabel(state.status)),
                    progress.added,
                    progress.failed,
                ),
            )
            Column(modifier = Modifier.padding(horizontal = dimens.gutter)) {
                EinkListRow(
                    title = stringResource(R.string.scan_row_files_seen),
                    trailing = progress.filesSeen.toString(),
                )
                EinkListRow(
                    title = stringResource(R.string.scan_row_added),
                    trailing = progress.added.toString(),
                )
                EinkListRow(
                    title = stringResource(R.string.scan_row_updated),
                    trailing = progress.updated.toString(),
                )
                EinkListRow(
                    title = stringResource(R.string.scan_row_removed),
                    trailing = progress.removed.toString(),
                )
                EinkListRow(
                    title = stringResource(R.string.scan_row_failed),
                    trailing = progress.failed.toString(),
                )
            }
            // Кнопки по состоянию прогона: во время скана — «Пауза», на паузе — «Продолжить».
            // «К полке» есть всегда: скан идёт в фоне, и держать пользователя на этом экране
            // незачем. Кнопки «Отменить» нет намеренно — скан прерываемый и возобновляемый.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                if (state.actions.canPause) {
                    EinkButton(
                        text = stringResource(R.string.scan_action_pause),
                        onClick = viewModel::onPause,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.actions.canResume) {
                    EinkButton(
                        text = stringResource(R.string.scan_action_resume),
                        onClick = viewModel::onResume,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (canOpenReport(state.report, state.actions.canPause)) {
                    EinkButton(
                        text = stringResource(R.string.scan_action_report),
                        onClick = viewModel::onShowReport,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.actions.canOpenLibrary) {
                    EinkButton(
                        text = stringResource(R.string.scan_action_shelf),
                        onClick = viewModel::onOpenLibrary,
                        primary = !state.actions.canPause && !state.actions.canResume,
                        modifier = Modifier.weight(1f),
                    )
                }
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
    val problemListState = rememberLazyListState()
    val scrollProblems = rememberEinkListPageScroller(problemListState)

    EinkScaffold(
        title = stringResource(R.string.scan_report_title),
        subtitle = report?.durationLabel,
        modifier = modifier,
        onBack = onBack,
        // Кнопки «Дубликаты» здесь нет: поиск дублей живёт в настройках («Библиотека и
        // сканирование» → «Проверить на дубли»), а второй вход вёл на экран, где кнопки отвечали
        // «пока не поддерживается», и список групп никто не заполнял.
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollProblems(-1)
                EinkAction.NEXT_PAGE -> scrollProblems(1)
                EinkAction.OPEN -> viewModel.onShowProgress()
                EinkAction.MENU -> viewModel.onShowProgress()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
    ) {
        if (report == null) {
            EinkEmptyState(
                title = stringResource(R.string.scan_report_empty_title),
                message = stringResource(R.string.scan_report_empty_message),
                actionLabel = stringResource(R.string.scan_action_to_scan),
                onAction = viewModel::onShowProgress,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.gutter),
            ) {
                EinkListRow(title = stringResource(R.string.scan_row_added), trailing = report.added.toString())
                EinkListRow(title = stringResource(R.string.scan_row_updated), trailing = report.updated.toString())
                EinkListRow(title = stringResource(R.string.scan_row_removed), trailing = report.removed.toString())
                EinkListRow(title = stringResource(R.string.scan_row_failed), trailing = report.failed.toString())

                EinkSectionHeader(
                    title = stringResource(R.string.scan_report_problems),
                    count = report.problemFiles.size,
                )

                if (report.problemFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.scan_report_no_problems),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.foreground,
                    )
                } else {
                    // Список прокручивается: страниц у него нет, и с выключенной прокруткой
                    // хвост проблемных файлов было не достать ни пальцем, ни клавишами.
                    LazyColumn(state = problemListState, modifier = Modifier.weight(1f)) {
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

