package io.github.bookshelf.feature.onboarding.ui

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
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

    EinkScaffold(
        title = "Первый запуск",
        subtitle = "Шаг ${state.step.ordinal + 1} из ${OnboardingStep.entries.size}",
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

            StubNotice(sprint = 2)
        }
    }
}

/** Шаг 1: что приложение делает и чего не делает. */
@Composable
private fun WelcomeStep() {
    EinkEmptyState(
        title = "BookShelf",
        message = "Приложение не читает книги само: оно собирает библиотеку и открывает книгу " +
            "в вашей читалке. Полка, поиск и фильтры работают полностью офлайн.",
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
        text = "Шаг 2. Папки с книгами",
        style = MaterialTheme.typography.titleMedium,
        color = colors.foreground,
    )
    Text(
        text = "Выберите саму папку с книгами (например, «Books»), а не корень хранилища: " +
            "на Android 11+ система не отдаёт приложению корень накопителя, корень SD-карты и папку " +
            "«Download». Если книги лежат в «Download», перенесите их в другую папку или включите " +
            "«Доступ ко всем файлам» в полной сборке.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.foreground,
    )
    Text(
        text = "Доступ к папке теряется, если её переместить или удалить. Тогда полка попросит " +
            "указать папку заново, а уже найденные книги останутся в библиотеке.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.foreground,
    )

    EinkButton(text = "Добавить папку", onClick = onAddSource, primary = true)

    if (sources.isEmpty()) {
        Text(
            text = "Папки пока не добавлены — без источника собирать библиотеку нечего.",
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
                title = source.displayName,
                subtitle = if (source.accessLost) "Папка недоступна — выбрать заново" else source.uri,
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
        text = "Шаг 3. Читалка по умолчанию",
        style = MaterialTheme.typography.titleMedium,
        color = colors.foreground,
    )
    Text(
        text = "Книга открывается во внешней читалке. Выберите читалку по умолчанию — " +
            "позже её можно сменить для каждого формата в настройках.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.foreground,
    )

    if (readers.isEmpty()) {
        Text(
            text = "Читалки пока не найдены: проверка установленных приложений появится в Sprint 5.",
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
                        trailing = if (reader.isDefault) "по умолчанию" else null,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
                        EinkButton(text = "Сделать основной", onClick = { onSelectReader(reader.id) })
                        EinkButton(text = "Проверить", onClick = { onCheckReader(reader.id) })
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
            EinkButton(text = "Назад", onClick = onPrevious)
        }
        if (state.step.isLast) {
            EinkButton(
                text = "Собрать библиотеку",
                onClick = onFinish,
                enabled = state.canFinish,
                primary = true,
            )
        } else {
            EinkButton(text = "Далее", onClick = onNext, primary = true)
        }
    }
}

/** Пометка этапа каркаса: видно, что экран ещё не реализован, и в каком спринте будет. */
@Composable
private fun StubNotice(sprint: Int) {
    val colors = LocalEinkColors.current
    Text(
        text = "Раздел в разработке — Спринт $sprint",
        style = MaterialTheme.typography.labelMedium,
        color = colors.foreground,
    )
}
