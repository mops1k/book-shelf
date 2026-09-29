package io.github.bookshelf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.sync.LibrarySync
import io.github.bookshelf.core.data.sync.SyncReadingProgress
import io.github.bookshelf.work.DatabaseBackupScheduler
import io.github.bookshelf.work.ReadingProgressSyncScheduler
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.designsystem.theme.EinkTheme
import io.github.bookshelf.core.designsystem.theme.LocalInvertCovers
import io.github.bookshelf.core.designsystem.theme.LocalVolumeKeysAsPageTurn
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.eink.RefreshMode
import io.github.bookshelf.navigation.BookShelfNavHost
import io.github.bookshelf.ui.EinkRefreshBridge
import io.github.bookshelf.ui.MainViewModel
import io.github.bookshelf.ui.ThemeState
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Единственная Activity.
 *
 * Здесь сходятся три вещи, которые больше нигде не должны встречаться:
 * * тема применяется ко всему приложению — смена плотности/кегля/тёмного режима работает
 *   без перезапуска (docs/eink-ux.md §2);
 * * `EinkRefreshBridge` связывает дизайн-систему с вендорским слоем e-ink (docs/architecture.md §6);
 * * навигационный граф собирается из feature-модулей, сама Activity про экраны не знает.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var readerEventsRegistered = false

    private var progressSyncRunning = false

    /** События читалки: «книгу открыли» — повод прочитать позицию немедленно. */
    private val readerEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            launchProgressSync()
        }
    }

    @Inject
    lateinit var einkController: EinkController

    /** Решает, показывать ли онбординг: пока источников нет, это единственный вход в приложение. */
    @Inject
    lateinit var sourceRepository: SourceRepository

    /** Синхронизация библиотеки с файловой системой при возврате в приложение. */
    @Inject
    lateinit var librarySync: LibrarySync

    /** Прогресс чтения из читалки: читается при возврате в приложение и по событию читалки. */
    @Inject
    lateinit var readingProgressSync: SyncReadingProgress

    /** Расписание опроса читалки: цепочка фоновых работ по настройке интервала. */
    @Inject
    lateinit var readingProgressSchedule: ReadingProgressSyncScheduler

    @Inject
    lateinit var databaseBackupSchedule: DatabaseBackupScheduler

    /**
     * Возврат в приложение — самый частый случай «я добавил книгу на устройство».
     *
     * Синхронизация инкрементальная: обход отмечает неизменённые файлы «виденными», добавляет
     * новые и убирает исчезнувшие, поэтому вызов на каждый `onStart` дешёв. Повторы чаще
     * 30 секунд отсекает сам `LibrarySync.syncIfStale` — переключение между окнами не должно
     * запускать обход дерева заново.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { librarySync.syncIfStale() }
        // Человек мог только что закрыть читалку: прогресс читаем сразу, не дожидаясь таймера.
        launchProgressSync()
        lifecycleScope.launch { readingProgressSchedule.startIfEnabled() }
        // Расписание копий пересматривается при каждом запуске: если настройку выключили, работа
        // должна быть снята, а не сработать после запрета.
        lifecycleScope.launch { databaseBackupSchedule.review() }
    }

    override fun onDestroy() {
        unregisterReaderEvents()
        super.onDestroy()
    }

    /**
     * Подписка на события читалки, пока жив процесс.
     *
     * Читалка присылает `event.type=startReading`, когда книгу открыли (проверено на устройстве):
     * это лучший момент прочитать позицию. Событие приходит от другого приложения, поэтому приёмник
     * регистрируется как экспортированный, а не только «для своих».
     *
     * Манифестный приёмник тут не поможет: фоновая доставка неявных широковещательных сообщений
     * ограничена системой, а динамическая подписка живого процесса ограничений не знает.
     */
    private fun registerReaderEvents() {
        if (readerEventsRegistered) return
        ContextCompat.registerReceiver(
            this,
            readerEvents,
            IntentFilter(READER_EVENT_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        readerEventsRegistered = true
    }

    private fun unregisterReaderEvents() {
        if (!readerEventsRegistered) return
        runCatching { unregisterReceiver(readerEvents) }
        readerEventsRegistered = false
    }

    /**
     * Запустить синхронизацию прогресса, если она уже не идёт.
     *
     * События читалки приходят пачками, а каждая синхронизация открывает и закрывает привязку к её
     * сервису. Наложение двух синхронизаций означало бы две привязки на один и тот же сервис, а
     * отмена на середине могла бы оставить привязку незакрытой, поэтому лишние события просто
     * пропускаются: позицию всё равно прочитает текущая синхронизация.
     */
    private fun launchProgressSync() {
        if (progressSyncRunning) return
        progressSyncRunning = true
        lifecycleScope.launch {
            try {
                readingProgressSync.syncNow()
            } finally {
                progressSyncRunning = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // На e-ink splash — статичный белый кадр без иконки и анимации.
        installSplashScreen()
        // Подписка на события читалки живёт столько же, сколько процесс, а не экран: книгу
        // открывают как раз тогда, когда наше приложение уже свёрнуто (`onStop`), и привязка
        // подписки к видимому экрану означала бы, что событий мы не увидим никогда.
        registerReaderEvents()
        super.onCreate(savedInstanceState)
        enterImmersiveMode()

        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            // Тот же корневой view, что и у `EinkRefreshBridge`: вендорские команды действуют
            // на экран целиком, а не на отдельный экран навигации.
            val rootView = LocalView.current

            ApplyEinkSettings(controller = einkController, view = rootView, state = themeState)

            EinkTheme(
                darkTheme = themeState.themeMode.isDarkTheme(),
                density = themeState.density,
                fontScale = themeState.fontScale,
            ) {
                // Настройки, которые читают компоненты дизайн-системы сами: инверсия обложек
                // (`BookCover`) и клавиши громкости (`EinkScaffold`). Прокидывать их параметрами
                // пришлось бы через каждый экран, включая чужие feature-модули.
                CompositionLocalProvider(
                    LocalInvertCovers provides themeState.invertCovers,
                    LocalVolumeKeysAsPageTurn provides themeState.volumeKeysAsPageTurn,
                ) {
                    EinkRefreshBridge(controller = einkController, settings = themeState.refresh) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            BookShelfNavHost(sourceRepository = sourceRepository)
                        }
                    }
                }
            }
        }
    }

    /**
     * Полноэкранный режим: статусная панель и навигация скрыты.
     *
     * На e-ink обе панели съедают часть полосы чтения и перерисовываются при каждом
     * касании. Полностью их не убираем: без системной навигации не выйти из приложения
     * и не открыть уведомления, поэтому панели возвращаются свайпом от края.
     */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Возврат приложения из фона снова полноэкранный. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }
}

/**
 * Тёмная тема с учётом системной настройки.
 *
 * `SYSTEM` нельзя разрешить в `MainViewModel`: для этого нужен `isSystemInDarkTheme()`, то есть
 * композиция. Поэтому `ThemeState` хранит выбор пользователя, а не готовый флаг, и один и тот же
 * выбор даёт разный результат при смене системной темы — без перезапуска приложения.
 */
@Composable
private fun ThemeMode.isDarkTheme(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * Применяет настройки экрана к [EinkController] — единственному месту, которое знает про вендора.
 *
 * `LaunchedEffect` на каждое значение, а не один общий: смена подсветки не должна трогать режим
 * обновления, а GC-интервал не нужно переустанавливать при изменении любого другого поля.
 *
 * Все вызовы в `runCatching` осознанно: вендорские реализации пока бросают `NotImplementedError`
 * из заглушек (`OnyxEinkController`), а подсветки на конкретном устройстве может не быть вовсе.
 * Настройка не должна ронять приложение — правила вызова прямо разрешают деградацию до no-op
 * (docs/eink-ux.md §8.4).
 */
@Composable
private fun ApplyEinkSettings(
    controller: EinkController,
    view: View,
    state: ThemeState,
) {
    LaunchedEffect(controller, state.gcInterval) {
        runCatching { controller.setGcInterval(state.gcInterval) }
    }
    LaunchedEffect(controller, view, state.refreshMode) {
        runCatching { controller.setRefreshMode(view, state.refreshMode.toCoreRefreshMode()) }
    }
    LaunchedEffect(controller, state.frontLightLevel) {
        state.frontLightLevel?.let { level -> runCatching { controller.setFrontLight(level) } }
    }
}

/**
 * Режим обновления из настроек → режим контроллера.
 *
 * `AUTO` — «как решит платформа»: у дизайн-системы для этого есть `NORMAL`, а вендорский слой сам
 * деградирует до `invalidate()`, если быстрый режим недоступен (`supportsFastMode`). Поэтому
 * проверять поддержку здесь не нужно — контракт `EinkController` это уже гарантирует.
 */
private fun RefreshModeLike.toCoreRefreshMode(): RefreshMode = when (this) {
    RefreshModeLike.AUTO -> RefreshMode.NORMAL
    RefreshModeLike.FULL -> RefreshMode.FULL
    RefreshModeLike.FAST -> RefreshMode.FAST
}

/** Действие события читалки: «книгу открыли», «позиция изменилась» (спайк 29.09.2026). */
private const val READER_EVENT_ACTION = "com.fbreader.action.API_CALLBACK"
