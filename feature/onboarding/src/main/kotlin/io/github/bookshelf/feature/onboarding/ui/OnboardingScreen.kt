package io.github.bookshelf.feature.onboarding.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.text.sourceNameOrFolder
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.onboarding.R
import io.github.bookshelf.feature.onboarding.OnboardingStep
import io.github.bookshelf.feature.onboarding.OnboardingUiState
import io.github.bookshelf.feature.onboarding.OnboardingViewModel
import io.github.bookshelf.feature.onboarding.ReaderListItem
import io.github.bookshelf.feature.onboarding.SourceListItem

/**
 * Онбординг первого запуска: три шага в одном экране (docs/eink-ux.md §6.1).
 *
 * Экран — заглушка этапа каркаса: он показывает структуру шагов, объяснение ограничений SAF
 * и обработку аппаратных кнопок, но не ходит ни в SAF, ни в репозитории (Sprint 2).
 *
 * @param onFinished вызывается по кнопке «Собрать библиотеку» — переход к сканированию.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current

    SourcePickerEffect(viewModel = viewModel)

    EinkScaffold(
        title = stringResource(R.string.onboarding_title),
        subtitle = stringResource(
            R.string.onboarding_step,
            state.step.ordinal + 1,
            OnboardingStep.entries.size,
        ),
        modifier = modifier,
        onBack = if (state.step.isFirst) null else viewModel::onPreviousStep,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE, EinkAction.BACK -> viewModel.onPreviousStep()
                EinkAction.NEXT_PAGE, EinkAction.OPEN -> viewModel.onNextStep()
                else -> Unit
            }
        },
        footer = {
            OnboardingFooter(
                state = state,
                onPrevious = viewModel::onPreviousStep,
                onNext = viewModel::onNextStep,
                onFinish = onFinished,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.gutter),
        ) {
            // Отказ показывается здесь: раньше поле состояния не читал никто, и человек не видел,
            // что добавление папки или выбор читалки не удались (дефект 01.10.2026).
            state.errorBanner?.let { banner ->
                EinkErrorBanner(message = banner.resolve())
            }

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()

                OnboardingStep.SOURCES -> SourcesStep(
                    sources = state.sources,
                    onAddSource = viewModel::onAddSource,
                    onRemoveSource = viewModel::onRemoveSource,
                )

                OnboardingStep.READERS -> ReadersStep(
                    readers = state.readers,
                    onSelectReader = viewModel::onSelectReader,
                    onCheckReader = viewModel::onCheckReader,
                )
            }
        }
    }
}

/** Шаг 1: что приложение делает и чего не делает. */
@Composable
private fun WelcomeStep() {
    EinkEmptyState(
        title = "BookShelf",
        message = stringResource(R.string.onboarding_welcome_message),
    )
}

/**
 * Шаг 2: выбор папок. Текст обязан объяснять ограничение Android 11+ — иначе пользователь
 * выбирает корень хранилища и остаётся без книг (docs/eink-ux.md §6.1).
 */
@Composable
private fun SourcesStep(
    sources: List<SourceListItem>,
    onAddSource: () -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    val colors = LocalEinkColors.current

    Text(
        text = stringResource(R.string.onboarding_sources_title),
        style = MaterialTheme.typography.titleMedium,
        color = colors.foreground,
    )
    Text(
        text = stringResource(R.string.onboarding_sources_message),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.foreground,
    )
    Text(
        text = stringResource(R.string.onboarding_sources_access_lost_message),
        style = MaterialTheme.typography.bodySmall,
        color = colors.foreground,
    )

    EinkButton(
        text = stringResource(R.string.onboarding_action_add_folder),
        onClick = onAddSource,
        primary = true,
    )

    if (sources.isEmpty()) {
        Text(
            text = stringResource(R.string.onboarding_sources_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
        )
    } else {
        SourcesList(sources = sources, onRemoveSource = onRemoveSource)
    }
}

/** Список уже выбранных папок: тап по строке — «убрать папку». */
@Composable
private fun SourcesList(
    sources: List<SourceListItem>,
    onRemoveSource: (String) -> Unit,
) {
    Column {
        sources.forEach { source ->
            EinkListRow(
                title = sourceNameOrFolder(source.displayName),
                subtitle = if (source.accessLost) {
                    stringResource(R.string.onboarding_source_unavailable)
                } else {
                    source.uri
                },
                trailing = source.documentCount?.toString(),
                onClick = { onRemoveSource(source.id) },
            )
        }
    }
}

/** Шаг 3: читалка по умолчанию для формата + проверка на тестовом файле. */
@Composable
private fun ReadersStep(
    readers: List<ReaderListItem>,
    onSelectReader: (String) -> Unit,
    onCheckReader: (String) -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Text(
        text = stringResource(R.string.onboarding_readers_title),
        style = MaterialTheme.typography.titleMedium,
        color = colors.foreground,
    )
    Text(
        text = stringResource(R.string.onboarding_readers_message),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.foreground,
    )

    if (readers.isEmpty()) {
        Text(
            text = stringResource(R.string.onboarding_readers_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.gutter)) {
            readers.forEach { reader ->
                Column(verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
                    EinkListRow(
                        title = reader.name,
                        subtitle = reader.packageName,
                        trailing = if (reader.isDefault) {
                            stringResource(R.string.onboarding_reader_default)
                        } else {
                            null
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
                        EinkButton(
                            text = stringResource(R.string.onboarding_action_make_default),
                            onClick = { onSelectReader(reader.id) },
                        )
                        EinkButton(
                            text = stringResource(R.string.onboarding_action_check),
                            onClick = { onCheckReader(reader.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Нижняя строка мастера: «Назад» и «Далее» / «Собрать библиотеку». */
@Composable
private fun OnboardingFooter(
    state: OnboardingUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        if (!state.step.isFirst) {
            EinkButton(text = stringResource(R.string.onboarding_action_back), onClick = onPrevious)
        }
        if (state.step.isLast) {
            EinkButton(
                text = stringResource(R.string.onboarding_action_finish),
                onClick = onFinish,
                enabled = state.canFinish,
                primary = true,
            )
        } else {
            EinkButton(
                text = stringResource(R.string.onboarding_action_next),
                onClick = onNext,
                primary = true,
            )
        }
    }
}


/**
 * Системный пикер папок: экран только открывает его по просьбе ViewModel.
 *
 * ViewModel просит открыть пикер событием `pickerRequests` и принимает результат через
 * `onSourcePicked`: запись в базу и проверка persistable-доступа — её забота, а не UI. Так экран
 * не знает про источники, а ViewModel — про Android Intent. Вынесено из `OnboardingScreen`
 * ради предела длины detekt.
 */
@Composable
private fun SourcePickerEffect(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.onSourcePicked(
                uri = uri.toString(),
                displayName = uri.lastPathSegment
                    ?.substringAfterLast(':')
                    .orEmpty()
                    .ifBlank { context.getString(R.string.onboarding_default_source_name) },
            )
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.pickerRequests.collect { folderPicker.launch(null) }
    }
}
