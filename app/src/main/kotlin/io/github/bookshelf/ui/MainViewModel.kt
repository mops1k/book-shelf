package io.github.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.sync.MetadataBackfill
import io.github.bookshelf.core.datastore.SettingsKeys
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.designsystem.theme.EinkDensity
import io.github.bookshelf.core.designsystem.theme.FontScale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Состояние отображения всего приложения, собранное из `AppSettings` одним снимком.
 *
 * Значения по умолчанию берутся из [SettingsKeys.Defaults] там, где типы совпадают: так
 * документированное умолчание невозможно поменять в одном месте и забыть в другом. Для плотности
 * и кегля это невозможно — у них тип дизайн-системы, а `:core:datastore` про Compose не знает
 * (см. `EinkDensityLike`), поэтому там указано то же значение с комментарием.
 *
 * @param themeMode выбор пользователя, а не готовое «тёмно/светло»: `SYSTEM` разрешается при
 *   отрисовке (`MainActivity`), потому что для этого нужен `isSystemInDarkTheme()`.
 * @param refresh какие события заканчиваются полным кадром (`EinkRefreshSettings`, docs/eink-ux.md §4).
 * @param refreshMode режим обновления экрана для `EinkController.setRefreshMode`.
 * @param gcInterval полный GC каждые N частичных обновлений (docs/eink-ux.md §4).
 * @param frontLightLevel уровень подсветки 0..100; `null` — пользователь её не настраивал.
 */
data class ThemeState(
    val themeMode: ThemeMode = SettingsKeys.Defaults.THEME_MODE,
    /** Крупная плотность — документированное умолчание (`SettingsKeys.Defaults.DENSITY`). */
    val density: EinkDensity = EinkDensity.LARGE,
    /** 100 % — документированное умолчание (`SettingsKeys.Defaults.FONT_SCALE`). */
    val fontScale: FontScale = FontScale.S100,
    val invertCovers: Boolean = SettingsKeys.Defaults.INVERT_COVERS,
    val volumeKeysAsPageTurn: Boolean = SettingsKeys.Defaults.VOLUME_KEYS_AS_PAGE_TURN,
    val refresh: EinkRefreshSettings = EinkRefreshSettings(),
    val refreshMode: RefreshModeLike = SettingsKeys.Defaults.REFRESH_MODE,
    val gcInterval: Int = SettingsKeys.Defaults.GC_INTERVAL,
    val frontLightLevel: Int? = null,
)

/**
 * Настройки отображения на уровне приложения.
 *
 * Подписка, а не одноразовое чтение: DoD Sprint 5 требует, чтобы смена темы, плотности, кегля
 * и режима экрана применялась без перезапуска (docs/roadmap.md). Поэтому [themeState] — это
 * `Flow` из DataStore, переведённый в состояние экрана.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    /**
     * Разовый бэкфилл жанров: без него у уже разобранных книг жанров в базе не появится,
     * и умная полка «по жанру» останется пустой (см. `MetadataBackfill`).
     */
    private val metadataBackfill: MetadataBackfill,
) : ViewModel() {

    private val _themeState = MutableStateFlow(ThemeState())

    /** Текущее состояние отображения для `collectAsStateWithLifecycle` в Activity. */
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    init {
        viewModelScope.launch { metadataBackfill.runOnce() }
        viewModelScope.launch {
            // Ошибки чтения хранилища разбирает сам репозиторий (`DataStoreSettingsRepository`):
            // недоступный файл настроек не должен ронять приложение, работаем на умолчаниях.
            settingsRepository.settings.collect { settings ->
                _themeState.value = settings.toThemeState()
            }
        }
    }
}

/** Снимок настроек → состояние отображения; вызывается на каждое изменение DataStore. */
private fun AppSettings.toThemeState(): ThemeState = ThemeState(
    themeMode = appearance.themeMode,
    density = densityOf(appearance.density),
    fontScale = fontScaleOf(appearance.fontScale),
    invertCovers = appearance.invertCovers,
    volumeKeysAsPageTurn = eink.volumeKeysAsPageTurn,
    refresh = EinkRefreshSettings(
        onScreenChange = eink.refreshOnScreenChange,
        onDialog = eink.refreshOnDialog,
        onPageChange = eink.refreshOnPageChange,
        // Полного кадра на события сканирования настройки нет: скан и так идёт редко и упирается
        // в полный перерисон сам, поэтому значение оставлено умолчанием `EinkRefreshSettings`.
    ),
    refreshMode = eink.refreshMode,
    gcInterval = eink.gcInterval,
    frontLightLevel = eink.frontLightLevel,
)

/** Плотность слоя данных → плотность дизайн-системы; `Like` существует ровно ради этого перевода. */
private fun densityOf(density: EinkDensityLike): EinkDensity = when (density) {
    EinkDensityLike.LARGE -> EinkDensity.LARGE
    EinkDensityLike.NORMAL -> EinkDensity.NORMAL
    EinkDensityLike.COMPACT -> EinkDensity.COMPACT
}

/**
 * Масштаб шрифта в процентах → кегль дизайн-системы (docs/eink-ux.md §2.2).
 *
 * Неизвестное значение — не повод падать: оно может прийти из более новой версии приложения или
 * из импортированного JSON. Показываем базовый кегль, как и предписано для неподдерживаемого
 * значения; молча подставлять случайный масштаб хуже, чем честный 100 %.
 */
private fun fontScaleOf(percent: Int): FontScale = when (percent) {
    FONT_SCALE_115 -> FontScale.S115
    FONT_SCALE_130 -> FontScale.S130
    FONT_SCALE_150 -> FontScale.S150
    else -> FontScale.S100
}

/** Проценты масштаба из docs/eink-ux.md §2.2: имена нужны, чтобы числа не считались «магией». */
private const val FONT_SCALE_115 = 115
private const val FONT_SCALE_130 = 130
private const val FONT_SCALE_150 = 150
