package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.permission.AllFilesAccess
import io.github.bookshelf.core.data.sync.ProgressSourcesReporter
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.sync.ProgressSyncSchedule
import io.github.bookshelf.core.readerlink.mime.formatCategoryOf
import io.github.bookshelf.core.readerlink.mime.ReaderFormatCategory
import io.github.bookshelf.core.datastore.ProgressSyncInterval
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.opds.download.DOWNLOAD_FILE_NAME_TEMPLATES
import io.github.bookshelf.core.readerlink.fbreader.FbReaderApi
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel настроек («E-ink UX» §6.8, Sprint 5).
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
    private val bookRepository: BookRepository,
    private val einkController: EinkController,
    private val einkDeviceDetector: io.github.bookshelf.core.eink.device.EinkDeviceDetector,
    private val coverCache: CoverCache,
    private val progressSchedule: ProgressSyncSchedule,
    private val fbReaderApi: FbReaderApi,
    /** Флаги сборки: раздел OPDS существует только в сборке с OPDS (ADR-006). */
    private val featureFlags: FeatureFlags,
    /**
     * Доступ ко всей памяти: без него читалки на общей памяти недоступны (и в `play` его нет вовсе).
     * Тем же портом пользуется слой данных, когда решает, заводить ли источник прямым путём.
     */
    private val allFilesAccess: AllFilesAccess,
    /** Диагностика источников прогресса: какие из включённых читаются и чей прогресс записан. */
    private val progressSourcesReporter: ProgressSourcesReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            sections = defaultSettingsSections(opdsEnabled = featureFlags.opdsEnabled),
            settings = AppSettingsUi(isAllFilesAccessGranted = allFilesAccess.granted()),
        ),
    )

    /**
     * Пересканировать все папки: обойти все включённые источники заново.
     *
     * Книги при этом не пропадают: [BookRepository.rescanAll] больше не очищает таблицу (решение
     * Александра 30.09.2026), а скан сам удаляет исчезнувшее с диска и переразбирает изменённое.
     * В обычной жизни команда не нужна: библиотека обновляется при возврате в приложение и раз в
     * 15 минут. Место в настройках выбрано потому, что рядом с книгами такая кнопка выглядела как
     * безобидное «обновить» (решение Александра, 28.09.2026).
     */
    fun onRescanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRescanning = true, error = null) }
            val result = bookRepository.rescanAll()
            _uiState.update { state ->
                state.copy(
                    isRescanning = false,
                    error = if (result is AppResult.Failure) {
                        AppText(R.string.settings_error_rescan, result.error.text())
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * Спросить подтверждение сброса.
     *
     * Сброс меняет все предпочтения разом (внешний вид, экран, полка, читалки, OPDS), поэтому
     * одного касания мало: случайное нажатие в подвале списка категорий не должно сбивать
     * настройки, к которым пользователь привык.
     */
    /**
     * Показать или скрыть подтверждение сброса.
     *
     * Один метод на оба состояния: «Сбросить настройки?» и отказ — это одно поле состояния, и два
     * отдельных метода были бы двумя обёртками над `isConfirmingReset`. Экран вызывает его с
     * `true` по нажатию в подвале и с `false` по «Отмена».
     */
    fun onSetResetConfirmation(confirming: Boolean) {
        _uiState.update { it.copy(isConfirmingReset = confirming, error = null) }
    }

    /**
     * Сбросить настройки к документированным умолчаниям.
     *
     * Данные не затрагиваются: папки, каталоги, библиотека, загрузки и файлы кэша остаются.
     * Служебные поля (флаг бэкфилла жанров, «последняя читалка») и зеркало читалки по умолчанию
     * переживают сброс — это делает [SettingsRepository.resetToDefaults].
     */
    fun onResetToDefaults() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true, isConfirmingReset = false, error = null) }
            try {
                settingsRepository.resetToDefaults()
                _uiState.update { it.copy(isResetting = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        error = AppText(R.string.settings_error_reset, error.toAppError().text()),
                    )
                }
            }
        }
    }

    /** Состояние экрана настроек для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Идентификаторы читалок, выбранных хотя бы для одного формата. */
    private var defaultReaderIds: Set<String> = emptySet()

    init {
        _uiState.value = _uiState.value.copy(isLoading = true).withFreshDiagnostics(
            controller = einkController,
            detector = einkDeviceDetector,
            opdsEnabled = featureFlags.opdsEnabled,
        )
        observeSettings(_uiState, settingsRepository, viewModelScope) { ids -> defaultReaderIds = ids }
        observeReaders(_uiState, readerRepository, viewModelScope) { defaultReaderIds }
        observeSources(_uiState, sourceRepository, viewModelScope)
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
            _uiState.showError(AppText(R.string.settings_error_unknown_density, density))
            return
        }
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
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
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(library = settings.library.copy(viewPreset = preset))
        }
    }

    /**
     * Команды блока «Прогресс чтения» в категории «Читалки».
     *
     * Одна точка входа, а не два сеттера: в классе 29 методов при пороге detekt `TooManyFunctions`
     * 30, и каждый новый метод приближает нарушение. Действие описывает намерение целиком, поэтому
     * читается не хуже двух отдельных функций.
     */
    fun onReaderProgress(action: ReaderProgressAction) {
        // Переключатели источников обрабатываются одинаково: запись флага + пересмотр расписания
        // (без пересмотра новая настройка не изменила бы поведение до перезапуска приложения).
        val switch = action.sourceSwitch()
        if (switch != null) {
            updateSettings(
                settingsRepository,
                _uiState,
                viewModelScope,
                afterWrite = { progressSchedule.review() },
            ) { settings -> settings.copy(reader = switch(settings.reader)) }
        }
        when (action) {
            is ReaderProgressAction.SetTempExport -> {
                updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
                    settings.copy(reader = settings.reader.copy(tempExportEnabled = action.enabled))
                }
            }

            ReaderProgressAction.CheckApi -> viewModelScope.launch {
                _uiState.update { it.copy(isCheckingReaderApi = true) }
                val available = fbReaderApi.connect()
                val openBook = if (available) fbReaderApi.currentBook() else null
                if (available) fbReaderApi.disconnect()
                _uiState.update {
                    it.copy(
                        isCheckingReaderApi = false,
                        readerApiStatus = when {
                            !available -> AppText(R.string.settings_reader_api_unavailable)
                            openBook == null -> AppText(R.string.settings_reader_api_no_book)
                            else -> AppText(
                                R.string.settings_reader_api_open,
                                openBook.title ?: openBook.path,
                            )
                        },
                    )
                }
            }

            is ReaderProgressAction.SetInterval -> {
                val minutes = ProgressSyncInterval.minutesOrDefault(action.minutes)
                updateSettings(
                    settingsRepository,
                    _uiState,
                    viewModelScope,
                    afterWrite = { progressSchedule.review() },
                ) { settings ->
                    settings.copy(reader = settings.reader.copy(progressSyncIntervalMinutes = minutes))
                }
            }

            // Переключатели источников прогресса обработаны выше, до этого `when`.
            else -> Unit
        }
    }

    /** Задать масштаб шрифта интерфейса в процентах. */
    fun onSetFontScale(percent: Int) {
        if (percent !in MIN_FONT_SCALE..MAX_FONT_SCALE) {
            _uiState.showError(
                AppText(R.string.settings_error_font_scale, MIN_FONT_SCALE, MAX_FONT_SCALE),
            )
            return
        }
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
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
            _uiState.showError(AppText(R.string.settings_error_unknown_refresh, mode))
            return
        }
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(eink = settings.eink.copy(refreshMode = value))
        }
    }

    /** Включить или выключить инверсию обложек. */
    fun onSetInvertCovers(enabled: Boolean) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(appearance = settings.appearance.copy(invertCovers = enabled))
        }
    }

    /**
     * Выбрать тему: светлая / тёмная / системная.
     *
     * Отдельный сеттер, а не `onSetDarkTheme(Boolean)`: «системная» — не третье состояние булева
     * флага, а отдельный выбор, и сводить его к `false` значило бы потерять настройку, которую
     * пользователь поставил («E-ink UX» §2.1).
     */
    fun onSetThemeMode(mode: ThemeMode) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(appearance = settings.appearance.copy(themeMode = mode))
        }
    }

    /** Полный кадр при смене экрана — главный источник «призраков» прежнего кадра. */
    fun onSetRefreshOnScreenChange(enabled: Boolean) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(eink = settings.eink.copy(refreshOnScreenChange = enabled))
        }
    }

    /** Полный кадр при открытии и закрытии диалога. */
    fun onSetRefreshOnDialog(enabled: Boolean) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(eink = settings.eink.copy(refreshOnDialog = enabled))
        }
    }

    /** Полный refresh при смене страницы списка. */
    fun onSetFullRefreshOnPage(enabled: Boolean) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(eink = settings.eink.copy(refreshOnPageChange = enabled))
        }
    }

    /** Запускать сканирование при старте приложения. */
    fun onSetLibraryBehaviour(action: LibraryBehaviourAction) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            when (action) {
                is LibraryBehaviourAction.SetScanOnStart ->
                    settings.copy(library = settings.library.copy(autoScanOnStart = action.enabled))

                is LibraryBehaviourAction.SetShowRemovableShelf ->
                    settings.copy(library = settings.library.copy(showRemovableShelf = action.enabled))

                is LibraryBehaviourAction.SetKeepMetadataOnFileChange -> settings.copy(
                    advanced = settings.advanced.copy(
                        keepMetadataOnFileChange = action.enabled,
                    ),
                )
            }
        }
    }

    /**
     * Привязать читалку к формату (или снять привязку, если [readerId] пуст).
     *
     * Источник истины — таблица `format_defaults`: её читает открытие книги. Зеркало
     * `reader.defaultReaderByFormat` в настройках обновляется рядом, иначе отметка «по умолчанию»
     * в списке читалок разошлась бы с настоящим поведением.
     */
    fun onSetReaderForFormat(extension: String, readerId: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = readerRepository.setDefault(extension, readerId)
                if (result.isFailure) {
                    _uiState.showError(
                        AppText(
                            R.string.settings_error_default_reader,
                            (result.errorOrNull() ?: AppError.Unknown()).text(),
                        ),
                    )
                    return@launch
                }
                // Зеркало в настройках: список читалок показывает отметку «по умолчанию» из него,
                // и расходиться с `format_defaults` она не должна.
                settingsRepository.update { current ->
                    val mirror = current.reader.defaultReaderByFormat.toMutableMap()
                    if (readerId == null) mirror.remove(extension) else mirror[extension] = readerId
                    current.copy(reader = current.reader.copy(defaultReaderByFormat = mirror))
                }
                // После выбора возвращаемся к форматам категории: человек обычно настраивает
                // несколько форматов подряд, а не уходит с экрана.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = null,
                    readersLevel = ReadersLevelUi.Formats(formatCategoryOf(extension)),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.showError(
                    AppText(R.string.settings_error_default_reader, error.toAppError().text()),
                )
            }
        }
    }

    /**
     * Перейти на уровень экрана «Читалки».
     *
     * Один метод на все переходы вперёд: экран идёт категория → форматы → читалки, и каждый шаг
     * это просто новое состояние уровня. Методов «на каждый шаг» здесь было бы три вместо одного.
     */
    /**
     * Перейти на уровень экрана «Читалки».
     *
     * Заодно перечитываем доступ ко всей памяти: его выдают в системных настройках, и человек может
     * вернуться оттуда с уже выданным доступом — подэкран прогресса обязан показать это сразу, а не
     * после перезапуска приложения.
     */
    fun onReadersLevel(level: ReadersLevelUi) {
        _uiState.value = _uiState.value.copy(
            readersLevel = level,
            settings = _uiState.value.settings.copy(isAllFilesAccessGranted = allFilesAccess.granted()),
        )
    }

    fun onAddSource(uri: String, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSourceBusy = true, error = null)
            val result = sourceRepository.addSafSource(uri = uri, displayName = displayName)
            if (result.isFailure) {
                _uiState.showError(
                    AppText(
                        R.string.settings_error_add_source,
                        (result.errorOrNull() ?: AppError.Unknown()).text(),
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
                _uiState.showError(
                    AppText(
                        R.string.settings_error_remove_source,
                        (result.errorOrNull() ?: AppError.Unknown()).text(),
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(isSourceBusy = false)
        }
    }

    /**
     * Очистить дисковый кэш обложек.
     *
     * Раньше здесь была заглушка «пока не поддерживается», хотя `CoverCache.clear()` реализован и
     * документирован ровно под эту команду: пользователь видел кнопку, которая обещала и ничего
     * не делала. Подсчёт освобождённого объёма и текст результата — в [clearCoverCacheWithReport],
     * чтобы это проверялось без фейков всех репозиториев экрана.
     */
    fun onClearCoverCache() {
        if (_uiState.value.isClearingCoverCache) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isClearingCoverCache = true, coverCacheMessage = null, error = null)
            }
            val message = clearCoverCacheWithReport(coverCache)
            _uiState.update {
                it.copy(isClearingCoverCache = false, coverCacheMessage = message)
            }
        }
    }

    /** Перечитать диагностику вендора e-ink: вместе с ней пересобирается список разделов. */
    fun onRefreshDiagnostics() {
        _uiState.value = _uiState.value.withFreshDiagnostics(
            controller = einkController,
            detector = einkDeviceDetector,
            opdsEnabled = featureFlags.opdsEnabled,
        ).copy(
            isLoading = false,
            error = null,
        )
        // Диагностика источников прогресса — здесь же: на экране «О приложении» одна кнопка
        // обновления, и человек ждёт от неё обновления всей диагностики, а не половины.
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingProgressSources = true) }
            val report = progressSourcesReporter.check().getOrNull()
            _uiState.update { it.copy(isCheckingProgressSources = false, progressSources = report) }
        }
    }

    /**
     * Задать интервал полного обновления: полный GC каждые [interval] частичных обновлений.
     *
     * Значение меньше [MIN_GC_INTERVAL] смысла не имеет: «полный кадр каждые 0 обновлений» — это
     * либо тихое отключение механизма против «призраков», либо деление на ноль в вендорском слое.
     */
    fun onSetGcInterval(interval: Int) {
        if (interval < MIN_GC_INTERVAL) {
            _uiState.showError(AppText(R.string.settings_error_gc_interval, MIN_GC_INTERVAL))
            return
        }
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(eink = settings.eink.copy(gcInterval = interval))
        }
    }

    /** Включить или выключить листание клавишами громкости. */
    fun onSetVolumeKeysAsPageTurn(enabled: Boolean) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(eink = settings.eink.copy(volumeKeysAsPageTurn = enabled))
        }
    }

    /**
     * Изменить уровень подсветки.
     *
     * Значение применяет `MainActivity` через [EinkController.setFrontLight] — сразу, без
     * перезапуска. На устройствах без подсветки и на вендорских заглушках вызов безвреден:
     * контроллер либо no-op, либо его ошибку гасит `runCatching`.
     */
    fun onSetFrontLight(level: Int) {
        val safeLevel = level.coerceIn(MIN_FRONT_LIGHT, MAX_FRONT_LIGHT)
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(eink = settings.eink.copy(frontLightLevel = safeLevel))
        }
    }

    /**
     * Лимиты диска: кэш обложек и временная копия.
     *
     * Одна точка входа на два родственных лимита, потому что у класса ровно 29 методов при пороге
     * detekt `TooManyFunctions` 30: два отдельных сеттера упёрлись бы в порог. Наборы вариантов
     * проверяются: сохранённое «со стороны» значение вне набора означало бы лимит, которого на
     * экране нет.
     */
    fun onSetDiskLimit(action: DiskLimitAction) {
        when (action) {
            is DiskLimitAction.SetCoverCacheMb -> {
                if (action.limitMb !in COVER_CACHE_LIMIT_CHOICES_MB) {
                    _uiState.showError(
                        AppText(
                            R.string.settings_error_cover_cache_limit,
                            COVER_CACHE_LIMIT_CHOICES_MB.joinToString(" / "),
                        ),
                    )
                    return
                }
                updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
                    settings.copy(library = settings.library.copy(coverCacheLimitMb = action.limitMb))
                }
            }

            is DiskLimitAction.SetTempCopyMb -> {
                if (action.limitMb !in TEMP_COPY_LIMIT_CHOICES_MB) {
                    _uiState.showError(
                        AppText(
                            R.string.settings_error_temp_copy_limit,
                            TEMP_COPY_LIMIT_CHOICES_MB.joinToString(" / "),
                        ),
                    )
                    return
                }
                updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
                    settings.copy(library = settings.library.copy(tempCopyLimitMb = action.limitMb))
                }
            }
        }
    }

    /**
     * Сортировка полки по умолчанию.
     *
     * Это то же значение, что и порядок, выбранный на самой полке: с 08.10.2026 полка пишет свой
     * выбор сюда (`LibraryViewModel.rememberOrder`), поэтому настройка и подвал полки показывают
     * одно и то же.
     */
    fun onSetDefaultSortOrder(order: SortOrder) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(library = settings.library.copy(sortOrder = order))
        }
    }

    /** Обратный порядок сортировки полки по умолчанию. */
    fun onSetDefaultSortDescending(descending: Boolean) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(library = settings.library.copy(sortDescending = descending))
        }
    }

    /** Группировка полки по умолчанию: заголовки-разделители внутри страницы. */
    fun onSetDefaultGrouping(grouping: Grouping) {
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(library = settings.library.copy(grouping = grouping))
        }
    }

    /**
     * Шаблон пути скачанного файла.
     *
     * Допускаются только готовые варианты ([DOWNLOAD_FILE_NAME_TEMPLATES]): шаблон попадает в имя
     * файла и путь, и произвольная строка могла бы дать пустое имя или папку без названия. В UI
     * чипы строятся по тому же списку, поэтому «лишний» вариант сюда попасть может только из
     * испорченных настроек — тогда значение не сохраняется, а экран показывает ошибку.
     */
    fun onSetDownloadFileNameTemplate(template: String) {
        if (template !in DOWNLOAD_FILE_NAME_TEMPLATES) {
            _uiState.showError(AppText(R.string.settings_error_file_name_template))
            return
        }
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(opds = settings.opds.copy(downloadFileNameTemplate = template))
        }
    }

    /** Возраст кэша фидов, минуты: 5 / 15 / 60. */
    fun onSetFeedCacheMaxAgeMinutes(minutes: Int) {
        if (minutes !in FEED_CACHE_CHOICES_MINUTES) {
            _uiState.showError(
                AppText(
                    R.string.settings_error_feed_cache_age,
                    FEED_CACHE_CHOICES_MINUTES.joinToString(" / "),
                ),
            )
            return
        }
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(opds = settings.opds.copy(feedCacheMaxAgeMinutes = minutes))
        }
    }

    private companion object {
        const val MIN_FONT_SCALE = 50
        const val MAX_FONT_SCALE = 200
        const val MIN_FRONT_LIGHT = 0
        const val MAX_FRONT_LIGHT = 100

        /** Меньше одного обновления между полными кадрами не бывает. */
        const val MIN_GC_INTERVAL = 1
    }
}

/**
 * Разбор текстового значения настройки в перечисление.
 *
 * Живёт вне класса: у `SettingsViewModel` больше тридцати методов, и каждый помощник в классе
 * приближает его к порогу detekt `TooManyFunctions` — а поднимать порог значило бы ослаблять гейт.
 * Здесь же функция не трогает состояние и проверяется отдельно.
 *
 * Алиасы нужны для старых вызовов с подписями («Крупный», «Обычный»): пользователь мог видеть
 * их в UI прошлых версий, и молча терять значение при чтении нельзя.
 */
private inline fun <reified T : Enum<T>> parseEnum(
    value: String,
    aliases: Map<String, T>,
): T? {
    val normalized = value.trim().uppercase(Locale.ROOT)
    return aliases[normalized] ?: runCatching { enumValueOf<T>(normalized) }.getOrNull()
}

private fun AppSettingsUi.withSettings(settings: AppSettings): AppSettingsUi = copy(
    isFbReaderProgressEnabled = settings.reader.enableFbReaderProgress,
    isKoReaderProgressEnabled = settings.reader.enableKoReaderProgress,
    isCoolReaderProgressEnabled = settings.reader.enableCoolReaderProgress,
    isPocketBookProgressEnabled = settings.reader.enablePocketBookProgress,
    isLibreraProgressEnabled = settings.reader.enableLibreraProgress,
    isXReaderProgressEnabled = settings.reader.enableXReaderProgress,
    progressSyncIntervalMinutes = settings.reader.progressSyncIntervalMinutes,
    isTempExportEnabled = settings.reader.tempExportEnabled,
    isKeepMetadataOnFileChange = settings.advanced.keepMetadataOnFileChange,
    density = settings.appearance.density,
    showCovers = !settings.appearance.textOnlyMode,
    fontScalePercent = settings.appearance.fontScale,
    refreshMode = settings.eink.refreshMode,
    fullRefreshOnScreenChange = settings.eink.refreshOnScreenChange,
    fullRefreshOnDialog = settings.eink.refreshOnDialog,
    fullRefreshOnPage = settings.eink.refreshOnPageChange,
    invertCovers = settings.appearance.invertCovers,
    themeMode = settings.appearance.themeMode,
    gcInterval = settings.eink.gcInterval,
    frontLightLevel = settings.eink.frontLightLevel,
    volumeKeysAsPageTurn = settings.eink.volumeKeysAsPageTurn,
    scanOnStart = settings.library.autoScanOnStart,
    viewPreset = settings.library.viewPreset,
    sortOrder = settings.library.sortOrder,
    sortDescending = settings.library.sortDescending,
    showRemovableShelf = settings.library.showRemovableShelf,
    grouping = settings.library.grouping,
    coverCacheLimitMb = settings.library.coverCacheLimitMb,
    tempCopyLimitMb = settings.library.tempCopyLimitMb,
    downloadFileNameTemplate = settings.opds.downloadFileNameTemplate,
    feedCacheMaxAgeMinutes = settings.opds.feedCacheMaxAgeMinutes,
)

/**
 * Подписка на настройки: единственный источник значений экрана.
 *
 * Живёт вне класса: у `SettingsViewModel` порог detekt `TooManyFunctions` (30) уже под рукой, и
 * каждый помощник в классе приближает нарушение. Состояния класса помощнику не нужно —
 * производный признак «читалка выбрана по умолчанию» он отдаёт вызывающему, который его хранит.
 */
private fun observeSettings(
    state: MutableStateFlow<SettingsUiState>,
    repository: SettingsRepository,
    scope: CoroutineScope,
    onDefaultReaders: (Set<String>) -> Unit,
) {
    scope.launch {
        try {
            repository.settings.collect { settings ->
                val defaultReaderIds = settings.reader.defaultReaderByFormat.values.toSet()
                onDefaultReaders(defaultReaderIds)
                state.value = state.value.copy(
                    settings = state.value.settings.withSettings(settings),
                    installedReaders = state.value.installedReaders.map { reader ->
                        reader.withDefaultFlag(defaultReaderIds)
                    },
                    isLoading = false,
                    error = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state.showError(AppText(R.string.settings_error_load, error.toAppError().text()))
        }
    }
}

/** Подписка на установленные читалки: список живой, установка и удаление сразу видны на экране. */
private fun observeReaders(
    state: MutableStateFlow<SettingsUiState>,
    repository: ReaderRepository,
    scope: CoroutineScope,
    defaultReaderIds: () -> Set<String>,
) {
    scope.launch {
        try {
            combine(repository.installedReaders(), repository.readerDefaults()) { readers, defaults ->
                readers to defaults
            }.collect { (readers, defaults) ->
                val items = readers.map { reader -> toReaderItem(reader, defaultReaderIds()) }
                val formats = formatReadersOf(items, defaults)
                state.value = state.value.copy(
                    installedReaders = items,
                    readerFormats = formats,
                    readerCategories = readerCategoriesOf(formats),
                    error = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state.showError(AppText(R.string.settings_error_readers, error.toAppError().text()))
        }
    }
}

/** Папки-источники: список живой, добавление и удаление сразу видны в экране. */
private fun observeSources(
    state: MutableStateFlow<SettingsUiState>,
    repository: SourceRepository,
    scope: CoroutineScope,
) {
    scope.launch {
        repository.observeSources().collect { sources ->
            // Цикл, а не `map`: право записи спрашивается у репозитория (suspend), а лямбда `map`
            // не приостанавливается.
            val items = mutableListOf<SourceItemUi>()
            for (source in sources) {
                items += SourceItemUi(
                    id = source.id,
                    displayName = source.displayName,
                    uri = source.uri,
                    // null, пока скан не выполнялся: «0 книг» и «ещё не сканировали» —
                    // разные вещи, и путать их в настройках нельзя.
                    booksCount = source.lastScanAt?.let { source.documentCount },
                    hasWriteAccess = repository.hasWriteAccess(source.id),
                )
            }
            state.value = state.value.copy(sources = items)
        }
    }
}

/**
 * Записать изменение настроек транзакционно и показать сбой баннером.
 *
 * Вне класса по той же причине: класс стоит у порога detekt `TooManyFunctions`, а помощнику нужны
 * только репозиторий, состояние и scope — всё это передаёт вызывающий.
 */
private fun updateSettings(
    repository: SettingsRepository,
    state: MutableStateFlow<SettingsUiState>,
    scope: CoroutineScope,
    // `afterWrite` стоит ПЕРЕД `transform`: завершающая лямбда привязывается к последнему
    // параметру, и при обратном порядке все вызовы `updateSettings(...) { settings -> ... }`
    // начали бы передавать преобразование в «действие после записи».
    afterWrite: suspend () -> Unit = {},
    transform: (AppSettings) -> AppSettings,
) {
    scope.launch {
        state.value = state.value.copy(isLoading = true, error = null)
        try {
            repository.update(transform)
            afterWrite()
            state.value = state.value.copy(isLoading = false, error = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state.showError(AppText(R.string.settings_error_save, error.toAppError().text()))
        }
    }
}

/** Показать баннер ошибки и снять признак загрузки: других способов сообщить о сбое у экрана нет. */
private fun MutableStateFlow<SettingsUiState>.showError(message: AppText) {
    value = value.copy(isLoading = false, error = message)
}

/**
 * Свежая диагностика вместе с согласованным списком разделов.
 *
 * Разделы зависят от железа: без вендорских команд раздел «Экран (e-ink)» состоял бы из одних
 * неработающих переключателей, поэтому его в списке нет (решение Александра, 05.10.2026). Список
 * пересобирается тем же вызовом, что и диагностика, иначе кнопка «Обновить диагностику» оставила
 * бы список устаревшим.
 *
 * Живёт вне класса по той же причине, что и [safeDiagnostics]: у `SettingsViewModel` порог detekt
 * `TooManyFunctions` уже под рукой.
 */
private fun SettingsUiState.withFreshDiagnostics(
    controller: EinkController,
    detector: io.github.bookshelf.core.eink.device.EinkDeviceDetector,
    opdsEnabled: Boolean,
): SettingsUiState {
    val fresh = safeDiagnostics(controller, detector, diagnostics)
    return copy(
        diagnostics = fresh,
        sections = defaultSettingsSections(opdsEnabled = opdsEnabled, einkEnabled = fresh.hasVendorCommands),
    )
}

/**
 * Диагностика e-ink — «лучшее из возможного», а не команда с важным результатом.
 *
 * Вендорские контроллеры (Onyx, Rockchip) обращаются к скрытым API и бросают `Throwable`
 * произвольного типа, когда их нет на устройстве. Экран настроек от этого развалиться не должен:
 * показываем то, что удалось выяснить, и молча оставляем прежние значения там, где выяснить
 * не удалось. Подавление правил — прямое следствие этого решения, а не способ обойти анализатор.
 *
 * Живёт вне класса: помощник не трогает состояние, а у `SettingsViewModel` порог detekt
 * `TooManyFunctions` уже под рукой — каждый метод в классе приближает его к нарушению.
 */
/**
 * Перехват здесь широкий осознанно.
 *
 * `controller` — вендорский контроллер: реализации для Onyx и Rockchip пока помечены
 * `TODO("STUB(Sprint 10)")` и бросают `NotImplementedError` (это `Error`, а не `Exception`),
 * а вендорские вызовы идут рефлексией и вправе упасть чем угодно. Диагностика — экран справки:
 * показать устаревшие данные честнее, чем уронить приложение из-за чужого SDK. Отмену корутины
 * функция пробрасывает.
 */
@Suppress("SwallowedException")
private fun safeDiagnostics(
    controller: EinkController,
    detector: io.github.bookshelf.core.eink.device.EinkDeviceDetector,
    current: EinkDiagnosticsUi,
): EinkDiagnosticsUi = try {
    // Железо описывает детектор, а контроллер — только то, что мы умеем с ним делать. Пока это
    // были одни и те же данные, устройство с бумагой без вендорского контроллера выглядело
    // обычным экраном и с неизвестным вендором.
    val device = detector.detect()
    val hasFrontLight = try {
        controller.frontLight() != null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        current.supportsFrontLight
    }
    current.copy(
        hasVendorCommands = controller.hasVendorCommands,
        vendorLabel = device.vendor.displayName,
        screenLabel = device.screen.text(),
        supportsFastMode = controller.supportsFastMode,
        supportsFrontLight = hasFrontLight,
    )
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    current
}

/** Строка читалки: профиль из репозитория + признак «выбрана по умолчанию хотя бы для формата». */
private fun toReaderItem(reader: ReaderProfileSummary, defaultReaderIds: Set<String>): ReaderItemUi =
    ReaderItemUi(
        id = reader.id,
        name = reader.name,
        packageName = reader.packageName,
        isDefault = reader.id in defaultReaderIds,
        supportedExtensions = reader.supportedExtensions,
    )

/** Пересчитать признак «по умолчанию» после смены выбора: профили перечитывать не нужно. */
/**
 * Уровень, на который поднимает кнопка «Назад» в шапке экрана «Читалки».
 *
 * `null` — подниматься некуда: шапка выходит из раздела настроек. Кнопка в шапке одна, своей
 * строки «Назад» в списке нет: её дублирование заметил Александр (01.10.2026).
 */
internal fun readersBackLevelOf(level: ReadersLevelUi): ReadersLevelUi? = when (level) {
    ReadersLevelUi.Categories -> null
    ReadersLevelUi.Defaults -> ReadersLevelUi.Categories
    is ReadersLevelUi.Formats -> ReadersLevelUi.Categories
    is ReadersLevelUi.ReadersOfFormat -> ReadersLevelUi.Formats(formatCategoryOf(level.extension))
    ReadersLevelUi.Progress -> ReadersLevelUi.Categories
}

/**
 * Категории для первого уровня экрана «Читалки».
 *
 * Категория без форматов не показывается: строка «Документы» без единого документа — тупик.
 */
internal fun readerCategoriesOf(formats: List<FormatReaderUi>): List<ReaderCategoryUi> =
    formats.groupBy { row -> row.category }
        .map { (category, rows) ->
            ReaderCategoryUi(
                category = category,
                formats = rows.size,
                bound = rows.count { row -> row.readerId != null },
            )
        }
        .sortedBy { row -> row.category.ordinal }

/**
 * Строки «формат → читалка»: какие форматы вообще есть и чем они открываются.
 *
 * Формат попадает в список, только если его умеет хотя бы одна установленная читалка: строка без
 * вариантов была бы тупиком. Привязка к удалённой читалке показывается как «не выбрано» — строка
 * `format_defaults` при удалении приложения остаётся, и притворяться, что выбор жив, нельзя.
 */
internal fun formatReadersOf(
    readers: List<ReaderItemUi>,
    defaults: Map<String, String>,
): List<FormatReaderUi> = readers
    .flatMap { reader -> reader.supportedExtensions }
    .distinct()
    .sorted()
    .mapNotNull { extension ->
        val candidates = readers.filter { reader -> extension in reader.supportedExtensions }
        if (candidates.isEmpty()) return@mapNotNull null
        val current = candidates.firstOrNull { reader -> reader.id == defaults[extension] }
        FormatReaderUi(
            extension = extension,
            category = formatCategoryOf(extension),
            readerName = current?.name,
            readerId = current?.id,
            candidates = candidates.size,
        )
    }

private fun ReaderItemUi.withDefaultFlag(defaultReaderIds: Set<String>): ReaderItemUi =
    copy(isDefault = id in defaultReaderIds)

/**
 * Как действие-переключатель меняет настройки читалки; `null` — действие не про источник прогресса.
 *
 * Функция вне класса не ради стиля: у `SettingsViewModel` порог detekt по числу методов уже выбран,
 * а «какой флаг включается» — знание о настройках, а не о состоянии экрана.
 */
private fun ReaderProgressAction.sourceSwitch(): ((ReaderSettings) -> ReaderSettings)? = when (this) {
    is ReaderProgressAction.SetEnabled -> { reader -> reader.copy(enableFbReaderProgress = enabled) }
    is ReaderProgressAction.SetKoReaderEnabled -> { reader -> reader.copy(enableKoReaderProgress = enabled) }
    is ReaderProgressAction.SetCoolReaderEnabled -> { reader -> reader.copy(enableCoolReaderProgress = enabled) }
    is ReaderProgressAction.SetPocketBookEnabled -> { reader -> reader.copy(enablePocketBookProgress = enabled) }
    is ReaderProgressAction.SetLibreraEnabled -> { reader -> reader.copy(enableLibreraProgress = enabled) }
    is ReaderProgressAction.SetXReaderEnabled -> { reader -> reader.copy(enableXReaderProgress = enabled) }
    else -> null
}

/**
 * Что меняет пользователь в блоке «Прогресс чтения».
 *
 * @param SetEnabled включение синхронизации с читалкой.
 * @param SetInterval интервал опроса в минутах (значение приводится к `ProgressSyncInterval`).
 */
sealed interface ReaderProgressAction {

    data class SetEnabled(val enabled: Boolean) : ReaderProgressAction

    /**
     * Включить или выключить прогресс из KOReader (sidecar-файл рядом с книгой).
     *
     * Отдельное действие, а не общий «включить прогресс»: у читалок разные условия — KOReader читает
     * файл внутри папки библиотеки, а источникам на общей памяти (CoolReader, PocketBook, Librera,
     * XReader) нужен доступ ко всей памяти. Человек должен решать по каждой читалке.
     */
    data class SetKoReaderEnabled(val enabled: Boolean) : ReaderProgressAction

    /**
     * Включить или выключить прогресс из CoolReader (его база `/sdcard/.cr3/cr3db.sqlite`).
     *
     * Условие доступа другое, чем у KOReader: база лежит вне папки библиотеки, поэтому нужен доступ
     * ко всей памяти. В flavor `play` переключатель включается, но источника там нет — это видно по
     * тому, что процент не появляется; честная пометка «нужен доступ ко всей памяти» появится
     * вместе с экраном диагностики источников.
     */
    data class SetCoolReaderEnabled(val enabled: Boolean) : ReaderProgressAction

    /** Включить или выключить прогресс из PocketBook (его база `/sdcard/.pocketbook/books.db`). */
    data class SetPocketBookEnabled(val enabled: Boolean) : ReaderProgressAction

    /** Включить или выключить прогресс из Librera (её JSON в `/sdcard/Librera/profile.Librera/`). */
    data class SetLibreraEnabled(val enabled: Boolean) : ReaderProgressAction

    /** Включить или выключить прогресс из XReader (его кэш `/sdcard/XRZBookCache/V3`). */
    data class SetXReaderEnabled(val enabled: Boolean) : ReaderProgressAction

    data class SetInterval(val minutes: Int) : ReaderProgressAction

    /**
     * Разрешить или запретить временную копию книги для читалки.
     *
     * Копия нужна только тем читалкам, которые не открывают `content://`: она занимает место
     * на диске, поэтому её включение — решение человека, а не наше.
     */
    data class SetTempExport(val enabled: Boolean) : ReaderProgressAction

    /**
     * Проверить связь с читалкой и показать результат рядом с переключателем.
     *
     * Связь может пропасть по причинам, которых не видно снаружи: читалку обновили, её сервис
     * переименовали, систему ограничила фоновую работу. Без такой проверки человек видит только
     * «процент не обновляется» и не понимает, что именно не работает.
     */
    data object CheckApi : ReaderProgressAction
}

/**
 * Какой лимит диска меняет пользователь.
 *
 * @param SetCoverCacheMb лимит кэша обложек, МБ.
 * @param SetTempCopyMb лимит временной копии книги из облака, МБ.
 */
/**
 * Поведение библиотеки, которое человек включает и выключает.
 *
 * Одним входом, а не отдельным методом на каждый переключатель: у `SettingsViewModel` порог detekt
 * по числу методов, и десять однотипных `onSetX` его исчерпали бы.
 */
sealed interface LibraryBehaviourAction {

    /** Сканировать библиотеку при запуске приложения. */
    data class SetScanOnStart(val enabled: Boolean) : LibraryBehaviourAction

    /**
     * Сохранять данные книги (оценку, статус чтения, полки, прогресс), когда её файл заменили.
     *
     * Выключение означает: заменённый файл — новая книга, и прежние данные уходят. Подпись в
     * интерфейсе говорит это прямо.
     */
    data class SetKeepMetadataOnFileChange(val enabled: Boolean) : LibraryBehaviourAction

    /** Показывать съёмный носитель отдельной полкой: карта памяти или флешка. */
    data class SetShowRemovableShelf(val enabled: Boolean) : LibraryBehaviourAction
}

sealed interface DiskLimitAction {

    data class SetCoverCacheMb(val limitMb: Int) : DiskLimitAction

    data class SetTempCopyMb(val limitMb: Int) : DiskLimitAction
}
