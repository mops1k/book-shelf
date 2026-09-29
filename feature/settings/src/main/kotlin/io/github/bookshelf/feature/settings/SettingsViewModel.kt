package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.sync.ProgressSyncSchedule
import io.github.bookshelf.core.datastore.ProgressSyncInterval
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.readerlink.fbreader.FbReaderApi
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.opds.download.DOWNLOAD_FILE_NAME_TEMPLATES
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
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
    private val bookRepository: BookRepository,
    private val einkController: EinkController,
    private val einkDeviceDetector: io.github.bookshelf.core.eink.device.EinkDeviceDetector,
    private val coverCache: CoverCache,
    private val progressSchedule: ProgressSyncSchedule,
    private val fbReaderApi: FbReaderApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    /**
     * Пересканировать все папки: забыть разобранное и собрать заново.
     *
     * Команда живёт в настройках, рядом с папками, и означает именно «забудь и собери заново»
     * ([BookRepository.rescanAll] очищает таблицу книг перед сканом). В обычной жизни она не нужна:
     * библиотека обновляется сама при возврате в приложение и раз в 15 минут. Место в настройках
     * выбрано потому, что рядом с книгами такая кнопка выглядела как безобидное «обновить»
     * (решение Александра, 28.09.2026).
     */
    fun onRescanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRescanning = true, error = null) }
            val result = bookRepository.rescanAll()
            _uiState.update { state ->
                state.copy(
                    isRescanning = false,
                    error = if (result is AppResult.Failure) {
                        result.error.message ?: "Не удалось пересканировать папки"
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
                        error = error.message ?: "Не удалось сбросить настройки",
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
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            diagnostics = safeDiagnostics(einkController, einkDeviceDetector, _uiState.value.diagnostics),
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
            _uiState.showError("Неизвестный режим плотности: $density")
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
        when (action) {
            is ReaderProgressAction.SetEnabled -> {
                updateSettings(
                    settingsRepository,
                    _uiState,
                    viewModelScope,
                    afterWrite = { progressSchedule.review() },
                ) { settings ->
                    settings.copy(reader = settings.reader.copy(enableFbReaderProgress = action.enabled))
                }
            }

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
                            !available -> "Читалка не отвечает: API недоступен"
                            openBook == null -> "API отвечает, книга не открыта"
                            else -> "API отвечает: открыто «${openBook.title ?: openBook.path}»"
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
        }
    }

    /** Задать масштаб шрифта интерфейса в процентах. */
    fun onSetFontScale(percent: Int) {
        if (percent !in MIN_FONT_SCALE..MAX_FONT_SCALE) {
            _uiState.showError("Масштаб шрифта должен быть от $MIN_FONT_SCALE до $MAX_FONT_SCALE %")
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
            _uiState.showError("Неизвестный режим обновления экрана: $mode")
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
     * пользователь поставил (docs/eink-ux.md §2.1).
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

                is LibraryBehaviourAction.SetKeepMetadataOnFileChange -> settings.copy(
                    advanced = settings.advanced.copy(
                        keepMetadataOnFileChange = action.enabled,
                    ),
                )
            }
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
                    _uiState.showError(
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
                _uiState.showError(error.userMessage("Не удалось назначить читалку по умолчанию."))
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
                _uiState.showError(
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
                _uiState.showError(appErrorMessage(result.errorOrNull(), "Не удалось убрать папку."))
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

    /** Перечитать диагностику вендора e-ink. */
    fun onRefreshDiagnostics() {
        _uiState.value = _uiState.value.copy(
            diagnostics = safeDiagnostics(einkController, einkDeviceDetector, _uiState.value.diagnostics),
            isLoading = false,
            error = null,
        )
    }

    /**
     * Задать интервал полного обновления: полный GC каждые [interval] частичных обновлений.
     *
     * Значение меньше [MIN_GC_INTERVAL] смысла не имеет: «полный кадр каждые 0 обновлений» — это
     * либо тихое отключение механизма против «призраков», либо деление на ноль в вендорском слое.
     */
    fun onSetGcInterval(interval: Int) {
        if (interval < MIN_GC_INTERVAL) {
            _uiState.showError("Интервал полного обновления должен быть не меньше $MIN_GC_INTERVAL")
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
                        "Лимит кэша обложек: допустимо " +
                            COVER_CACHE_LIMIT_CHOICES_MB.joinToString(" / ") + " МБ",
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
                        "Лимит временной копии: допустимо " +
                            TEMP_COPY_LIMIT_CHOICES_MB.joinToString(" / ") + " МБ",
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
     * Это начальное значение: полка читает его при открытии (`LibraryViewModel`), а дальше
     * порядком управляет выбор на самой полке — иначе настройка перебивала бы его.
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
            _uiState.showError("Такой шаблон имени файла не поддерживается")
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
                "Возраст кэша каталогов: допустимо " +
                    FEED_CACHE_CHOICES_MINUTES.joinToString(" / ") + " мин",
            )
            return
        }
        updateSettings(settingsRepository, _uiState, viewModelScope) { settings ->
            settings.copy(opds = settings.opds.copy(feedCacheMaxAgeMinutes = minutes))
        }
    }

    private companion object {
        const val DEFAULT_READER_FORMAT = "fb2"
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

/** Человеческое объяснение ошибки репозитория: у `AccessLost` и `Unsupported` свои подсказки. */
private fun appErrorMessage(error: AppError?, fallback: String): String = when {
    error == null -> fallback
    error is AppError.AccessLost -> "Доступ к папке потерян, выберите её заново."
    error is AppError.Unsupported -> error.message ?: "Операция пока не поддерживается."
    !error.message.isNullOrBlank() -> error.message.orEmpty()
    else -> fallback
}

private fun AppSettingsUi.withSettings(settings: AppSettings): AppSettingsUi = copy(
    isFbReaderProgressEnabled = settings.reader.enableFbReaderProgress,
    progressSyncIntervalMinutes = settings.reader.progressSyncIntervalMinutes,
    isTempExportEnabled = settings.reader.tempExportEnabled,
    isKeepMetadataOnFileChange = settings.advanced.keepMetadataOnFileChange,
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
    themeMode = settings.appearance.themeMode,
    gcInterval = settings.eink.gcInterval,
    frontLightLevel = settings.eink.frontLightLevel,
    volumeKeysAsPageTurn = settings.eink.volumeKeysAsPageTurn,
    scanOnStart = settings.library.autoScanOnStart,
    viewPreset = settings.library.viewPreset,
    sortOrder = settings.library.sortOrder,
    sortDescending = settings.library.sortDescending,
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
            state.showError(error.userMessage("Не удалось прочитать настройки."))
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
            repository.installedReaders().collect { readers ->
                state.value = state.value.copy(
                    installedReaders = readers.map { reader ->
                        toReaderItem(reader, defaultReaderIds())
                    },
                    error = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state.showError(error.userMessage("Не удалось получить список читалок."))
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
            state.showError(error.userMessage("Не удалось сохранить настройку."))
        }
    }
}

/** Показать баннер ошибки и снять признак загрузки: других способов сообщить о сбое у экрана нет. */
private fun MutableStateFlow<SettingsUiState>.showError(message: String) {
    value = value.copy(isLoading = false, error = message)
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
        screenLabel = device.screen.displayName,
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
    )

/** Пересчитать признак «по умолчанию» после смены выбора: профили перечитывать не нужно. */
private fun ReaderItemUi.withDefaultFlag(defaultReaderIds: Set<String>): ReaderItemUi =
    copy(isDefault = id in defaultReaderIds)

/** Текст ошибки из исключения: пустое сообщение (или его отсутствие) важнее заменить подсказкой. */
private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

/**
 * Что меняет пользователь в блоке «Прогресс чтения».
 *
 * @param SetEnabled включение синхронизации с читалкой.
 * @param SetInterval интервал опроса в минутах (значение приводится к `ProgressSyncInterval`).
 */
sealed interface ReaderProgressAction {

    data class SetEnabled(val enabled: Boolean) : ReaderProgressAction

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
}

sealed interface DiskLimitAction {

    data class SetCoverCacheMb(val limitMb: Int) : DiskLimitAction

    data class SetTempCopyMb(val limitMb: Int) : DiskLimitAction
}
