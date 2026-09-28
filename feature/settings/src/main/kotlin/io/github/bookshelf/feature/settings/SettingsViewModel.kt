package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.eink.EinkController
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel настроек (docs/eink-ux.md §6.8, Sprint 5).
 *
 * Настройки читаются одним снимком и записываются транзакционно через [SettingsRepository].
 * Так значения, сохранённые в одном разделе, не могут разъехаться с остальными настройками,
 * а изменения применяются до перезапуска приложения.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val readerRepository: ReaderRepository,
    private val sourceRepository: SourceRepository,
    private val einkController: EinkController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    /** Состояние экрана настроек для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Идентификаторы читалок, выбранных хотя бы для одного формата. */
    private var defaultReaderIds: Set<String> = emptySet()

    init {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            diagnostics = safeDiagnostics(_uiState.value.diagnostics),
        )
        observeSettings()
        observeReaders()
        observeSources()
    }

    /** Папки-источники: список живой, добавление и удаление сразу видны в экране. */
    private fun observeSources() {
        viewModelScope.launch {
            sourceRepository.observeSources().collect { sources ->
                _uiState.value = _uiState.value.copy(
                    sources = sources.map { source ->
                        SourceItemUi(
                            id = source.id,
                            displayName = source.displayName,
                            uri = source.uri,
                            // null, пока скан не выполнялся: «0 книг» и «ещё не сканировали» —
                            // разные вещи, и путать их в настройках нельзя.
                            booksCount = source.lastScanAt?.let { source.documentCount },
                        )
                    },
                )
            }
        }
    }

    /** Выбрать плотность интерфейса: Крупный / Обычный / Плотный. */
    fun onSetDensity(density: String) {
        val value = parseEnum(
            value = density,
            aliases = mapOf(
                "КРУПНЫЙ" to EinkDensityLike.LARGE,
                "ОБЫЧНЫЙ" to EinkDensityLike.NORMAL,
                "ПЛОТНЫЙ" to EinkDensityLike.COMPACT,
            ),
        )
        if (value == null) {
            showError("Неизвестный режим плотности: $density")
            return
        }
        updateSettings { settings ->
            settings.copy(appearance = settings.appearance.copy(density = value))
        }
    }

    /**
     * Выбрать вид полки по умолчанию: сетка, список, компактно или обзор по авторам/сериям.
     *
     * Значение читает полка при открытии (`LibraryViewModel`), поэтому настройка сохраняется
     * в `AppSettings.library.viewPreset`, а не живёт только в UI-состоянии.
     */
    fun onSetViewPreset(preset: ShelfViewPreset) {
        updateSettings { settings ->
            settings.copy(library = settings.library.copy(viewPreset = preset))
        }
    }

    /** Задать масштаб шрифта интерфейса в процентах. */
    fun onSetFontScale(percent: Int) {
        if (percent !in MIN_FONT_SCALE..MAX_FONT_SCALE) {
            showError("Масштаб шрифта должен быть от $MIN_FONT_SCALE до $MAX_FONT_SCALE %")
            return
        }
        updateSettings { settings ->
            settings.copy(appearance = settings.appearance.copy(fontScale = percent))
        }
    }

    /** Выбрать режим обновления экрана: Авто / Полный / Быстрый. */
    fun onSetRefreshMode(mode: String) {
        val value = parseEnum(
            value = mode,
            aliases = mapOf(
                "АВТО" to RefreshModeLike.AUTO,
                "ПОЛНЫЙ" to RefreshModeLike.FULL,
                "БЫСТРЫЙ" to RefreshModeLike.FAST,
            ),
        )
        if (value == null) {
            showError("Неизвестный режим обновления экрана: $mode")
            return
        }
        updateSettings { settings ->
            settings.copy(eink = settings.eink.copy(refreshMode = value))
        }
    }

    /** Включить или выключить инверсию обложек. */
    fun onSetInvertCovers(enabled: Boolean) {
        updateSettings { settings ->
            settings.copy(appearance = settings.appearance.copy(invertCovers = enabled))
        }
    }

    /** Включить или выключить тёмную тему. */
    fun onSetDarkTheme(enabled: Boolean) {
        val theme = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
        updateSettings { settings ->
            settings.copy(appearance = settings.appearance.copy(themeMode = theme))
        }
    }

    /** Полный refresh при смене страницы списка. */
    fun onSetFullRefreshOnPage(enabled: Boolean) {
        updateSettings { settings ->
            settings.copy(eink = settings.eink.copy(refreshOnPageChange = enabled))
        }
    }

    /** Запускать сканирование при старте приложения. */
    fun onSetScanOnStart(enabled: Boolean) {
        updateSettings { settings ->
            settings.copy(library = settings.library.copy(autoScanOnStart = enabled))
        }
    }

    /**
     * Назначить читалку по умолчанию для формата.
     *
     * Экран настроек ещё не передаёт формат, поэтому используется базовый `fb2`; после
     * появления выбора формата параметр можно передать из UI без изменения репозитория.
     */
    fun onSetDefaultReader(readerId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = readerRepository.setDefault(DEFAULT_READER_FORMAT, readerId)
                if (result.isFailure) {
                    showError(
                        appErrorMessage(
                            result.errorOrNull(),
                            "Не удалось назначить читалку по умолчанию.",
                        ),
                    )
                } else {
                    defaultReaderIds = defaultReaderIds + readerId
                    _uiState.value = _uiState.value.copy(
                        installedReaders = _uiState.value.installedReaders.map { reader ->
                            reader.copy(isDefault = reader.id == readerId || reader.isDefault)
                        },
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось назначить читалку по умолчанию."))
            }
        }
    }

    /**
     * Добавить папку-источник.
     *
     * URI приходит из системного выборщика: приложение само знать путь не может, а
     * `addSafSource` ещё и забирает у системы постоянное разрешение на дерево.
     */
    fun onAddSource(uri: String, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSourceBusy = true, error = null)
            val result = sourceRepository.addSafSource(uri = uri, displayName = displayName)
            if (result.isFailure) {
                showError(
                    appErrorMessage(
                        result.errorOrNull(),
                        "Не удалось добавить папку. Выдайте доступ заново.",
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(isSourceBusy = false)
        }
    }

    /**
     * Убрать папку из списка источников.
     *
     * Книги из неё не удаляются с устройства: исчезает только источник, и после следующего
     * скана они перестанут считаться нашими. Это осознанно — удалять файлы из чужой папки
     * приложению нельзя.
     */
    fun onRemoveSource(sourceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSourceBusy = true, error = null)
            val result = sourceRepository.removeSource(sourceId)
            if (result.isFailure) {
                showError(appErrorMessage(result.errorOrNull(), "Не удалось убрать папку."))
            }
            _uiState.value = _uiState.value.copy(isSourceBusy = false)
        }
    }

    /**
     * Проверить читалку на тестовом файле.
     *
     * Открытие тестового файла появится вместе с интеграцией Sprint 5; сейчас действие
     * безопасно сообщает ограничение, вместо того чтобы бросать `NotImplementedError` из UI.
     */
    fun onCheckReader(readerId: String) {
        showError("Проверка читалки на тестовом файле пока не поддерживается")
    }

    /** Очистить дисковый кэш обложек. */
    fun onClearCoverCache() {
        showError("Очистка кэша обложек пока не поддерживается")
    }

    /** Экспортировать библиотеку и настройки в JSON (без паролей OPDS). */
    fun onExportLibrary() {
        showError("Экспорт библиотеки пока не поддерживается")
    }

    /** Перечитать диагностику вендора e-ink. */
    fun onRefreshDiagnostics() {
        _uiState.value = _uiState.value.copy(
            diagnostics = safeDiagnostics(_uiState.value.diagnostics),
            isLoading = false,
            error = null,
        )
    }

    /**
     * Изменить сохранённый уровень подсветки.
     *
     * Значение сохраняется сейчас, применяется к железу в Sprint 10. Это не позволяет
     * потерять настройку до появления вендорской реализации [EinkController.setFrontLight].
     */
    fun onSetFrontLight(level: Int) {
        val safeLevel = level.coerceIn(MIN_FRONT_LIGHT, MAX_FRONT_LIGHT)
        updateSettings { settings ->
            settings.copy(eink = settings.eink.copy(frontLightLevel = safeLevel))
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            try {
                settingsRepository.settings.collect { settings ->
                    defaultReaderIds = settings.reader.defaultReaderByFormat.values.toSet()
                    _uiState.value = _uiState.value.copy(
                        settings = _uiState.value.settings.withSettings(settings),
                        installedReaders = _uiState.value.installedReaders.map(::withDefaultFlag),
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось прочитать настройки."))
            }
        }
    }

    private fun observeReaders() {
        viewModelScope.launch {
            try {
                readerRepository.installedReaders().collect { readers ->
                    _uiState.value = _uiState.value.copy(
                        installedReaders = readers.map(::toReaderItem),
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось получить список читалок."))
            }
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                settingsRepository.update(transform)
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось сохранить настройку."))
            }
        }
    }

    /**
     * Диагностика e-ink — «лучшее из возможного», а не команда с важным результатом.
     *
     * Вендорские контроллеры (Onyx, Rockchip) обращаются к скрытым API и бросают `Throwable`
     * произвольного типа, когда их нет на устройстве. Экран настроек от этого развалиться не
     * должен: показываем то, что удалось выяснить, и молча оставляем прежние значения там, где
     * выяснить не удалось. Подавление правил — прямое следствие этого решения, а не способ
     * обойти анализатор.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun safeDiagnostics(current: EinkDiagnosticsUi): EinkDiagnosticsUi = try {
        val hasFrontLight = try {
            einkController.frontLight() != null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            current.supportsFrontLight
        }
        current.copy(
            isEinkDevice = einkController.isEinkDevice,
            vendorLabel = einkController.vendor.displayName,
            supportsFastMode = einkController.supportsFastMode,
            supportsFrontLight = hasFrontLight,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        current
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = message)
    }

    private fun appErrorMessage(error: AppError?, fallback: String): String = when {
        error == null -> fallback
        error is AppError.AccessLost -> "Доступ к папке потерян, выберите её заново."
        error is AppError.Unsupported -> error.message ?: "Операция пока не поддерживается."
        !error.message.isNullOrBlank() -> error.message.orEmpty()
        else -> fallback
    }

    private fun toReaderItem(reader: ReaderProfileSummary): ReaderItemUi = ReaderItemUi(
        id = reader.id,
        name = reader.name,
        packageName = reader.packageName,
        isDefault = reader.id in defaultReaderIds,
    )

    private fun withDefaultFlag(reader: ReaderItemUi): ReaderItemUi =
        reader.copy(isDefault = reader.id in defaultReaderIds)

    private inline fun <reified T : Enum<T>> parseEnum(
        value: String,
        aliases: Map<String, T>,
    ): T? {
        val normalized = value.trim().uppercase(Locale.ROOT)
        return aliases[normalized] ?: runCatching { enumValueOf<T>(normalized) }.getOrNull()
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    private companion object {
        const val DEFAULT_READER_FORMAT = "fb2"
        const val MIN_FONT_SCALE = 50
        const val MAX_FONT_SCALE = 200
        const val MIN_FRONT_LIGHT = 0
        const val MAX_FRONT_LIGHT = 100
    }
}

private fun AppSettingsUi.withSettings(settings: AppSettings): AppSettingsUi = copy(
    densityLabel = when (settings.appearance.density) {
        EinkDensityLike.LARGE -> "Крупный"
        EinkDensityLike.NORMAL -> "Обычный"
        EinkDensityLike.COMPACT -> "Плотный"
    },
    showCovers = !settings.appearance.textOnlyMode,
    fontScalePercent = settings.appearance.fontScale,
    refreshModeLabel = when (settings.eink.refreshMode) {
        RefreshModeLike.AUTO -> "Авто"
        RefreshModeLike.FULL -> "Полный"
        RefreshModeLike.FAST -> "Быстрый"
    },
    fullRefreshOnScreenChange = settings.eink.refreshOnScreenChange,
    fullRefreshOnDialog = settings.eink.refreshOnDialog,
    fullRefreshOnPage = settings.eink.refreshOnPageChange,
    invertCovers = settings.appearance.invertCovers,
    darkTheme = settings.appearance.themeMode == ThemeMode.DARK,
    scanOnStart = settings.library.autoScanOnStart,
    viewPreset = settings.library.viewPreset,
    coverCacheSizeLabel = "${settings.library.coverCacheLimitMb} МБ",
)
