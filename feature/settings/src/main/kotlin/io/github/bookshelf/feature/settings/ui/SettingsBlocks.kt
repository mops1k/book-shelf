package io.github.bookshelf.feature.settings.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkChip
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.designsystem.text.sourceNameOrFolder
import io.github.bookshelf.feature.settings.PermissionItemUi
import io.github.bookshelf.feature.settings.SourceItemUi
import io.github.bookshelf.feature.settings.R

/**
 * Общие блоки экранов настроек.
 *
 * Экран настроек разделён на категории, и большинство блоков повторяются от категории к категории
 * (строка папки, выбор из чипов, честное «ещё не подключено»). Собраны здесь, чтобы в категориях
 * оставался только их собственный состав.
 */

/**
 * Секция «Где искать книги»: папки, добавление папки и пересканирование.
 *
 * «Пересканировать» живёт именно здесь: команда означает «забудь разобранное и собери заново»,
 * и рядом с книгами она выглядела безобидным «обновить».
 */
internal fun LazyListScope.sourcesSection(
    sources: List<SourceItemUi>,
    isRescanning: Boolean,
    onAddFolder: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onRescanAll: () -> Unit,
    onOpenScan: () -> Unit,
) {
    item(key = "header-sources") {
        EinkSectionHeader(title = stringResource(R.string.settings_sources_header), count = sources.size)
    }
    items(items = sources, key = { source -> source.id }) { source ->
        SourceRow(
            // Имя источника — данные; у источника без имени слова подставляет экран.
            name = sourceNameOrFolder(source.displayName),
            booksLabel = source.booksCount
                ?.let { count -> stringResource(R.string.settings_sources_books, count) }
                ?: stringResource(R.string.settings_sources_never_scanned),
            hasWriteAccess = source.hasWriteAccess,
            onRemove = { onRemoveSource(source.id) },
        )
    }
    item(key = "source-add") {
        // `LocalEinkDimens` читается внутри item: на уровне LazyListScope @Composable-вызовы
        // недоступны — этот код выполняется при построении списка, а не при отрисовке.
        val dimens = LocalEinkDimens.current
        EinkButton(
            text = stringResource(R.string.settings_action_add_folder),
            onClick = onAddFolder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.gutterSmall),
        )
    }
    item(key = "source-rescan") {
        SourceRescanRow(isRescanning = isRescanning, onRescan = onRescanAll)
    }
    item(key = "source-scan-screen") {
        // Экран сканирования (ход и отчёт с проблемными файлами) открывался только с пустой
        // полки: при живой библиотеке входа к нему не было вовсе.
        EinkListRow(
            title = stringResource(R.string.settings_sources_scan_report),
            subtitle = stringResource(R.string.settings_sources_scan_report_hint),
            onClick = onOpenScan,
        )
    }
}

/** «Пересканировать все папки» — команда «забудь разобранное и собери заново». */
@Composable
internal fun SourceRescanRow(isRescanning: Boolean, onRescan: () -> Unit) {
    EinkListRow(
        title = stringResource(R.string.settings_sources_rescan),
        subtitle = stringResource(R.string.settings_sources_rescan_hint),
        trailing = if (isRescanning) stringResource(R.string.settings_in_progress) else null,
        onClick = { if (!isRescanning) onRescan() },
    )
}

/** Строка папки-источника с кнопкой удаления: файлы из папки приложение не трогает. */
@Composable
internal fun SourceRow(
    name: String,
    booksLabel: String,
    hasWriteAccess: Boolean,
    onRemove: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gutterSmall / 2),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkListRow(
            title = name,
            // Без права записи книги в папку не скачаются: сказать об этом надо здесь, где папка
            // выбрана, а не в момент неудачной загрузки.
            subtitle = if (hasWriteAccess) {
                booksLabel
            } else {
                stringResource(R.string.settings_source_no_write)
            },
            modifier = Modifier.weight(1f),
        )
        EinkButton(text = stringResource(R.string.settings_source_remove), onClick = onRemove)
    }
}

/**
 * Выбор из нескольких вариантов чипами — тот же вид, что у строки видов на самой полке.
 *
 * Чипы, а не выпадающий список: на e-ink касание крупной плашки надёжнее попадания в строку
 * списка, а выбранное состояние видно по инверсии.
 *
 * Раскладка — [FlowRow], а не [Row]: в разделе «Библиотека и скан» у сортировки восемь вариантов,
 * и одна строка их не вмещает. Раньше восемь чипов подряд на полке занимали три экрана высоты и
 * мешали книгам, поэтому там выбор уехал в отдельный экран; в настройках вариант обязан быть
 * виден целиком, и перенос на следующую строку читается лучше обрезанного края экрана.
 */
/**
 * Один вариант выбора: устойчивый идентификатор и ресурс подписи.
 *
 * Идентификатор отделён от подписи намеренно: подпись переводится (ru/en), а значение обязано
 * оставаться неизменным — иначе английский интерфейс сломал бы сохранение настроек
 * (`onSetDensity(«Compact»)` не разбирается). [labelArg] нужен подписям с числом («130 %», «500 МБ»).
 */
data class ChoiceOption(
    val id: String,
    val labelRes: Int,
    val labelArg: Any? = null,
)

@Composable
internal fun ChoiceRow(
    title: String,
    options: List<ChoiceOption>,
    selected: String,
    onPick: (String) -> Unit,
    subtitle: String? = null,
) {
    val dimens = LocalEinkDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gutterSmall),
    ) {
        EinkSectionHeader(title = title)
        subtitle?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = LocalEinkColors.current.foreground,
                modifier = Modifier.padding(top = dimens.gutterSmall / 2),
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.gutterSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall / 2),
        ) {
            options.forEach { option ->
                EinkChip(
                    label = option.labelArg
                        ?.let { arg -> stringResource(option.labelRes, arg) }
                        ?: stringResource(option.labelRes),
                    selected = option.id == selected,
                    onClick = { onPick(option.id) },
                )
            }
        }
    }
}

/**
 * То же, что [ChoiceRow], но со значениями, а не с готовыми подписями.
 *
 * Экрану нужен сам вариант — лимит в мегабайтах или значение перечисления, — и передавать его
 * строкой значило бы держать три списка (подписи, значения, разбор строки), которые обязаны
 * совпадать. Здесь подпись выводится из значения одной функцией [label], поэтому выбранный чип и
 * сохраняемое значение разойтись не могут.
 */
@Composable
internal fun <T> ValueChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onPick: (T) -> Unit,
    subtitle: String? = null,
) {
    ChoiceRow(
        title = title,
        options = options.map { option ->
            // Значение само подставляется в подпись: «500 МБ», «2 мин», «130 %» — без отдельных списков.
            ChoiceOption(id = option.toString(), labelRes = labelRes(option), labelArg = option)
        },
        selected = selected.toString(),
        subtitle = subtitle,
        onPick = { id -> options.firstOrNull { it.toString() == id }?.let(onPick) },
    )
}

/**
 * Строка-переключатель: булева настройка, которая включается касанием.
 *
 * Состояние повторяется словом в `trailing` («включено»/«выключено»), а не только галочкой:
 * на e-ink мелкий значок состояния читается плохо, а слова видно с любого расстояния. Общий блок
 * нужен потому, что переключателей в разделах уже пять, и все они выглядят одинаково.
 */
@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    EinkListRow(
        title = title,
        subtitle = subtitle,
        trailing = if (enabled) {
            stringResource(R.string.settings_toggle_on)
        } else {
            stringResource(R.string.settings_toggle_off)
        },
        onClick = { onToggle(!enabled) },
    )
}

/**
 * Честная заглушка категории, настройки которой ещё не подключены.
 *
 * Показывать вместо неё переключатели нельзя: они бы ничего не делали — а именно этого мы
 * договорились не делать. Здесь прямо сказано, что раздел наполняется.
 */
@Composable
internal fun CategoryPending(message: String) {
    val dimens = LocalEinkDimens.current
    EinkEmptyState(
        title = stringResource(R.string.settings_pending_section),
        message = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gutter),
    )
}

/**
 * Разрешения, которые приложение действительно может запросить.
 *
 * `READ_EXTERNAL_STORAGE` имеет смысл только до API 32: с Android 33 доступ к файлам даётся
 * SAF-выбором папки, и этот пункт в списке отсутствует, чтобы не пугать пользователя
 * разрешением, которое система всё равно не выдаст.
 */
internal fun permissionsOf(context: Context): List<PermissionItemUi> = buildList {
    add(
        PermissionItemUi(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            titleRes = R.string.settings_permission_notifications,
            subtitleRes = R.string.settings_permission_notifications_hint,
            isGranted = context.isGranted(Manifest.permission.POST_NOTIFICATIONS),
        ),
    )
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        add(
            PermissionItemUi(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                titleRes = R.string.settings_permission_read,
                subtitleRes = R.string.settings_permission_read_hint,
                isGranted = context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
            ),
        )
        add(
            PermissionItemUi(
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                titleRes = R.string.settings_permission_write,
                subtitleRes = R.string.settings_permission_write_hint,
                isGranted = context.isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            ),
        )
    }
}

/**
 * Системная страница разрешений этого приложения.
 *
 * Обычных кнопок в Android у приложения две: системный диалог при первом обращении и страница
 * настроек приложения. Второй нужен, когда пользователь отказал один раз — после отказа Android
 * больше диалог не показывает, и без этой кнопки вернуть доступ нельзя.
 */
internal fun Context.openAppInfoSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

/** Выдано ли разрешение; на API ниже 23 проверки разрешений нет — считаем, что выдано. */
internal fun Context.isGranted(permission: String): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Имя выбранного дерева; системный выборщик отдаёт только URI, а показать путь нужно в списке. */
internal fun Context.displayNameOfTree(uri: Uri): String {
    val fromDocuments = runCatching {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':')
    }.getOrNull()
    if (!fromDocuments.isNullOrBlank()) return fromDocuments
    return uri.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.settings_default_source_name)
}

/**
 * Вид полки по умолчанию: та же тройка вариантов, что и в строке видов на полке.
 *
 * Подписи продублированы рядом с экраном настроек: экран не должен зависеть от `feature:library`
 * только ради трёх строк, а состав вариантов задаёт `ShelfViewPreset`.
 */
@Composable
internal fun ViewPresetChooser(
    preset: ShelfViewPreset,
    onPick: (ShelfViewPreset) -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.settings_view_header),
        options = ShelfViewPreset.entries.map { option ->
            ChoiceOption(id = option.name, labelRes = presetLabelRes(option))
        },
        selected = preset.name,
        onPick = { id -> onPick(ShelfViewPreset.valueOf(id)) },
    )
}

/** Подпись вида полки — та же, что на кнопках в строке видов полки. Ресурс, не текст. */
internal fun presetLabelRes(preset: ShelfViewPreset): Int = when (preset) {
    ShelfViewPreset.GRID -> R.string.settings_view_grid
    ShelfViewPreset.AUTHORS -> R.string.settings_view_authors
    ShelfViewPreset.SERIES -> R.string.settings_view_series
    ShelfViewPreset.GENRES -> R.string.settings_view_genres
}

