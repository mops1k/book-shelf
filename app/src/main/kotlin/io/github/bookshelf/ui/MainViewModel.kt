package io.github.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bookshelf.core.data.sync.MetadataBackfill
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.designsystem.theme.EinkDensity
import io.github.bookshelf.core.designsystem.theme.FontScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Состояние темы всего приложения. */
data class ThemeState(
    val darkTheme: Boolean = false,
    val density: EinkDensity = EinkDensity.LARGE,
    val fontScale: FontScale = FontScale.S100,
    val invertCovers: Boolean = false,
    val volumeKeysAsPageTurn: Boolean = true,
)

/**
 * Настройки отображения на уровне приложения.
 *
 * STUB(Sprint 4): значения должны приходить из `SettingsRepository.settings`
 * (DataStore) и применяться без перезапуска. Сейчас отдаются значения по умолчанию,
 * чтобы тема, плотность и масштаб уже были частью каркаса.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    /**
     * Разовый бэкфилл жанров: без него у уже разобранных книг жанров в базе не появится,
     * и умная полка «по жанру» останется пустой (см. `MetadataBackfill`).
     */
    private val metadataBackfill: MetadataBackfill,
) : ViewModel() {

    init {
        viewModelScope.launch { metadataBackfill.runOnce() }
    }

    private val _themeState = MutableStateFlow(ThemeState())
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()
}
