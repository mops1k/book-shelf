package io.github.bookshelf.feature.settings.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.component.EinkChip
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.feature.settings.PermissionItemUi
import io.github.bookshelf.feature.settings.SettingsSectionKind
import io.github.bookshelf.feature.settings.SettingsViewModel

/**
 * Настройки (docs/eink-ux.md §6.8).
 *
 * Первые три блока — рабочие, а не список обещаний: читалка, папки и разрешения. Именно их
 * спрашивали, и раньше они были недостижимы — экран был в графе навигации, но в него не вёл
 * ни один переход. Остальные разделы остаются заглушками с честными подписями.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSectionKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
    val context = LocalContext.current

    // Источник папок выбирает система, а не приложение: путь на устройстве нам неизвестен.
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.displayNameOfTree(uri)
        runCatching { context.contentResolver.takePersistableUriPermission(uri, 0) }
        viewModel.onAddSource(uri = uri.toString(), displayName = name)
    }

    // Права выдаёт система: перезапрос после каждого изменения нужен, иначе строка
    // продолжит показывать «не выдано» при уже выданном.
    var permissions by remember { mutableStateOf(permissionsOf(context)) }
    val permissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { permissions = permissionsOf(context) }

    EinkScaffold(
        title = "Настройки",
        subtitle = "Читалка, папки и права — здесь",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            if (action == EinkAction.BACK) onBack() else Unit
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = "Повторить",
                onAction = viewModel::onRefreshDiagnostics,
            )
        }

        // Список содержит только читалки: посторонние приложения, объявившие книжный MIME
        // (браузеры, галереи, файловые менеджеры), discovery отбрасывает. Пользователь
        // выбирает читалку, а не «любое приложение, которому система подошла».
        val readers = state.installedReaders

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.gutter),
        ) {
            // Вид полки по умолчанию: та же пятёрка кнопок, что и на самой полке, — иначе
            // пользователь выбирал бы обзор в одном месте, а значение по умолчанию задавал в другом.
            item(key = "view-preset") {
                ViewPresetChooser(
                    preset = state.settings.viewPreset,
                    onPick = viewModel::onSetViewPreset,
                )
            }
            item(key = "header-readers") {
                EinkSectionHeader(title = "В чём читать", count = readers.size)
            }
            if (readers.isEmpty()) {
                item(key = "readers-empty") {
                    EinkEmptyState(
                        title = "Читалка не найдена",
                        message = "Установите FBReader — приложение открывает книги в ней. " +
                            "В другую программу книга отправлена не будет.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(items = readers, key = { reader -> reader.id }) { reader ->
                    EinkListRow(
                        title = reader.name,
                        subtitle = reader.packageName,
                        trailing = if (reader.isDefault) "по умолчанию" else "выбрать",
                        onClick = { viewModel.onSetDefaultReader(reader.id) },
                    )
                }
            }

            item(key = "header-sources") {
                EinkSectionHeader(title = "Где искать книги", count = state.sources.size)
            }
            items(items = state.sources, key = { source -> source.id }) { source ->
                SourceRow(
                    name = source.displayName,
                    booksLabel = source.booksCount?.let { "$it книг" } ?: "ещё не сканировалось",
                    onRemove = { viewModel.onRemoveSource(source.id) },
                )
            }
            item(key = "source-add") {
                EinkButton(
                    text = "Добавить папку",
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimens.gutterSmall),
                )
            }

            item(key = "header-permissions") {
                EinkSectionHeader(title = "Разрешения", count = permissions.size)
            }
            items(items = permissions, key = { item -> item.permission }) { item ->
                EinkListRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    trailing = if (item.isGranted) "выдано" else "запросить",
                    onClick = { permissionRequest.launch(item.permission) },
                )
            }
            item(key = "permissions-appinfo") {
                EinkButton(
                    text = "Разрешения приложения в системе",
                    onClick = { context.openAppInfoSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimens.gutterSmall),
                )
            }

            item(key = "header-sections") {
                EinkSectionHeader(title = "Остальные разделы", count = state.sections.size)
            }
            items(items = state.sections, key = { section -> section.kind.name }) { section ->
                EinkListRow(
                    title = section.title,
                    subtitle = section.summary,
                    onClick = { onOpenSection(section.kind) },
                )
            }
        }
    }
}

/** Строка папки-источника с кнопкой удаления: файлы из папки приложение не трогает. */
@Composable
private fun SourceRow(
    name: String,
    booksLabel: String,
    onRemove: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gutterSmall / 2),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        EinkListRow(
            title = name,
            subtitle = booksLabel,
            modifier = Modifier.weight(1f),
        )
        EinkButton(text = "Убрать", onClick = onRemove)
    }
}

/**
 * Разрешения, которые приложение действительно может запросить.
 *
 * `READ_EXTERNAL_STORAGE` имеет смысл только до API 32: с Android 33 доступ к файлам даётся
 * SAF-выбором папки, и этот пункт в списке отсутствует, чтобы не пугать пользователя
 * разрешением, которое система всё равно не выдаст. На API 33+ доступ к файлам берётся
 * кнопкой «Добавить папку» — это и есть системный запрос доступа, только без диалога.
 */
private fun permissionsOf(context: Context): List<PermissionItemUi> = buildList {
    add(
        PermissionItemUi(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            title = "Уведомления",
            subtitle = "Показывать ход сканирования, когда приложение свёрнуто",
            isGranted = context.isGranted(Manifest.permission.POST_NOTIFICATIONS),
        ),
    )
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        add(
            PermissionItemUi(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                title = "Чтение файлов",
                subtitle = "Искать книги в памяти телефона без выбора папки",
                isGranted = context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
            ),
        )
        add(
            PermissionItemUi(
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                title = "Запись файлов",
                subtitle = "Сохранять загруженные книги в память телефона",
                isGranted = context.isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            ),
        )
    }
}

/**
 * Системная страница разрешений этого приложения.
 *
 * Обычных кнопок в Android у приложения два: системный диалог при первом обращении и
 * страница настроек приложения. Второй нужен, когда пользователь отказал один раз — после
 * отказа Android больше диалог не показывает, и без этой кнопки вернуть доступ нельзя.
 */
private fun Context.openAppInfoSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

/** Выдано ли разрешение; на API ниже 23 проверки разрешений нет — считаем, что выдано. */
private fun Context.isGranted(permission: String): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Имя выбранного дерева; системный выборщик отдаёт только URI, а показать путь нужно в списке. */
private fun Context.displayNameOfTree(uri: android.net.Uri): String {
    val fromDocuments = runCatching {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':')
    }.getOrNull()
    if (!fromDocuments.isNullOrBlank()) return fromDocuments
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Папка"
}

/**
 * Вид полки по умолчанию: та же пятёрка кнопок, что и в строке видов на полке.
 *
 * Подписи продублированы рядом с экраном настроек: экран не должен зависеть от `feature:library`
 * только ради пяти строк, а порядок и состав вариантов задаёт `ShelfViewPreset`.
 */
@Composable
private fun ViewPresetChooser(
    preset: ShelfViewPreset,
    onPick: (ShelfViewPreset) -> Unit,
) {
    val dimens = LocalEinkDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gutterSmall),
    ) {
        EinkSectionHeader(title = "Вид полки по умолчанию")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.gutterSmall),
            // Кнопки по размеру подписи и прижаты влево — как строка видов на самой полке.
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            ShelfViewPreset.entries.forEach { candidate ->
                EinkChip(
                    label = presetLabel(candidate),
                    selected = candidate == preset,
                    onClick = { onPick(candidate) },
                )
            }
        }
    }
}

/** Подпись вида полки — та же, что на кнопках в строке видов полки. */
private fun presetLabel(preset: ShelfViewPreset): String = when (preset) {
    ShelfViewPreset.GRID -> "Сетка"
    ShelfViewPreset.AUTHORS -> "Авторы"
    ShelfViewPreset.SERIES -> "Серии"
}
