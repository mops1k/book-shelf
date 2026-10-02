package io.github.bookshelf.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.export.LibraryExportRepository
import io.github.bookshelf.core.data.export.libraryExportFileName
import io.github.bookshelf.core.designsystem.text.AppText
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние строки «Экспорт библиотеки».
 *
 * @param isExporting идёт выгрузка: строка показывает «Выгружаем…» и не запускает вторую.
 * @param message итог успешной выгрузки: сколько книг и полок записано.
 * @param error причина сбоя; показывается вместо итога, чтобы неудача не выглядела успехом.
 */
data class LibraryExportUiState(
    val isExporting: Boolean = false,
    val message: AppText? = null,
    val error: AppText? = null,
)

/**
 * Выгрузка библиотеки в JSON-файл.
 *
 * Отдельная ViewModel, а не метод общей: у `SettingsViewModel` уже девять зависимостей при пороге
 * detekt десять, и десятый параметр (репозиторий выгрузки) валил гейт. Рядом с ней живёт
 * `DatabaseBackupViewModel` по той же причине — «Хранилище и кэш» состоит из независимых команд,
 * и каждая со своим состоянием читается лучше, чем один общий снимок на всё.
 */
@HiltViewModel
class LibraryExportViewModel @Inject constructor(
    private val libraryExport: LibraryExportRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryExportUiState())
    val uiState: StateFlow<LibraryExportUiState> = _uiState.asStateFlow()

    /**
     * Имя файла, которое предлагается в системном диалоге.
     *
     * Дата берётся у [clock], а не у `System.currentTimeMillis()`: время в приложении идёт через
     * одни часы, и имя файла проверяется тестом с фиксированным временем.
     */
    fun suggestedFileName(): String = libraryExportFileName(clock.nowMillis())

    /**
     * Выгрузить библиотеку в документ, который человек выбрал в диалоге SAF.
     *
     * Признак «идёт выгрузка» ставится **до** запуска корутины: два нажатия подряд (человек не
     * увидел мгновенной реакции и нажал снова) иначе запустили бы две записи в один документ,
     * и файл получился бы битым.
     */
    fun onExport(destination: Uri) {
        if (_uiState.value.isExporting) return
        _uiState.update { state -> state.copy(isExporting = true, message = null, error = null) }
        viewModelScope.launch {
            val run = libraryExportReport(libraryExport.exportTo(destination))
            _uiState.update { state ->
                state.copy(isExporting = false, message = run.message, error = run.error)
            }
        }
    }
}
