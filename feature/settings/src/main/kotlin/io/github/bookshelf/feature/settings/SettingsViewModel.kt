package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.eink.EinkController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel настроек (docs/eink-ux.md §6.8, Sprint 5).
 *
 * На этапе каркаса отдаёт только начальное состояние: репозитории и `EinkController`
 * внедрены как контракты и не вызываются.
 *
 * После Sprint 5 появятся действия:
 * * изменение настроек внешнего вида, экрана, сканирования и метаданных;
 * * выбор читалки по умолчанию для формата и проверка на тестовом файле;
 * * очистка кэша обложек и экспорт/импорт библиотеки;
 * * чтение диагностики вендора e-ink и управление подсветкой (Sprint 10).
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

    /** Выбрать плотность интерфейса: Крупный / Обычный / Плотный. */
    fun onSetDensity(density: String) {
        // STUB(Sprint 5): записать плотность и применить тему без перезапуска
        TODO("STUB(Sprint 5): settingsRepository.setDensity($density)")
    }

    /** Выбрать режим отображения полки по умолчанию. */
    fun onSetViewMode(viewMode: String) {
        // STUB(Sprint 5): записать режим отображения по умолчанию
        TODO("STUB(Sprint 5): settingsRepository.setDefaultViewMode($viewMode)")
    }

    /** Задать масштаб шрифта интерфейса в процентах. */
    fun onSetFontScale(percent: Int) {
        // STUB(Sprint 5): записать масштаб шрифта
        TODO("STUB(Sprint 5): settingsRepository.setFontScale(percent = $percent)")
    }

    /** Выбрать режим обновления экрана: Авто / Полный / Быстрый. */
    fun onSetRefreshMode(mode: String) {
        // STUB(Sprint 5): записать режим и применить через EinkRefreshHost
        TODO("STUB(Sprint 5): settingsRepository.setRefreshMode($mode)")
    }

    /** Включить или выключить инверсию обложек. */
    fun onSetInvertCovers(enabled: Boolean) {
        // STUB(Sprint 5): инверсия применяется при декодировании, файлы кэша не трогаются
        TODO("STUB(Sprint 5): settingsRepository.setInvertCovers($enabled)")
    }

    /** Включить или выключить тёмную тему. */
    fun onSetDarkTheme(enabled: Boolean) {
        // STUB(Sprint 5): переключить тему без перезапуска
        TODO("STUB(Sprint 5): settingsRepository.setDarkTheme($enabled)")
    }

    /** Полный refresh при смене страницы списка. */
    fun onSetFullRefreshOnPage(enabled: Boolean) {
        // STUB(Sprint 5): записать флаг события полного перерисона
        TODO("STUB(Sprint 5): settingsRepository.setFullRefreshOnPage($enabled)")
    }

    /** Запускать сканирование при старте приложения. */
    fun onSetScanOnStart(enabled: Boolean) {
        // STUB(Sprint 5): записать настройку автоскана
        TODO("STUB(Sprint 5): settingsRepository.setScanOnStart($enabled)")
    }

    /** Назначить читалку по умолчанию для формата. */
    fun onSetDefaultReader(readerId: String) {
        // STUB(Sprint 5): записать format_defaults для выбранного профиля
        TODO("STUB(Sprint 5): readerRepository.setDefault(format, readerId = $readerId)")
    }

    /** Проверить читалку на тестовом файле. */
    fun onCheckReader(readerId: String) {
        // STUB(Sprint 5): открыть тестовый файл и показать результат проверки
        TODO("STUB(Sprint 5): readerRepository.openTestFile(readerId = $readerId)")
    }

    /** Очистить дисковый кэш обложек. */
    fun onClearCoverCache() {
        // STUB(Sprint 9): очистить кэш обложек и обновить подпись «Занято кэшем»
        TODO("STUB(Sprint 9): covers.clearCache()")
    }

    /** Экспортировать библиотеку и настройки в JSON (без паролей OPDS). */
    fun onExportLibrary() {
        // STUB(Sprint 9): экспорт библиотеки и настроек
        TODO("STUB(Sprint 9): exportLibrary()")
    }

    /** Перечитать диагностику вендора e-ink. */
    fun onRefreshDiagnostics() {
        // STUB(Sprint 10): прочитать вендора и возможности через EinkController
        TODO("STUB(Sprint 10): einkController.vendor / supportsFastMode")
    }

    /** Изменить уровень подсветки, если устройство её поддерживает. */
    fun onSetFrontLight(level: Int) {
        // STUB(Sprint 10): на устройстве без подсветки вызов становится no-op
        TODO("STUB(Sprint 10): einkController.setFrontLight(level = $level)")
    }
}
