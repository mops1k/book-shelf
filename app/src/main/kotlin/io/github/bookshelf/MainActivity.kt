package io.github.bookshelf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.data.sync.LibrarySync
import io.github.bookshelf.core.data.sync.SyncReadingProgress
import io.github.bookshelf.work.DatabaseBackupScheduler
import io.github.bookshelf.work.ReadingProgressSyncScheduler
import io.github.bookshelf.core.applist.rule.HomeRoleController
import io.github.bookshelf.core.datastore.launcher.LauncherStore
import io.github.bookshelf.core.datastore.model.HomeScreen
import io.github.bookshelf.core.datastore.model.SystemBarsMode
import io.github.bookshelf.feature.library.LibraryRoute
import io.github.bookshelf.feature.launcher.AppsRoute
import io.github.bookshelf.feature.launcher.LauncherViewModel
import io.github.bookshelf.navigation.BookShelfNavHost
import io.github.bookshelf.ui.BookShelfThemeRoot
import io.github.bookshelf.ui.MainViewModel
import io.github.bookshelf.ui.SystemBarsPolicy
import io.github.bookshelf.ui.HomeRolePolicy
import io.github.bookshelf.ui.LauncherTabs
import io.github.bookshelf.ui.ThemeState
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Единственная Activity.
 *
 * Здесь сходятся три вещи, которые больше нигде не должны встречаться:
 * * тема применяется ко всему приложению — смена плотности/кегля/тёмного режима работает
 *   без перезапуска («E-ink UX» §2);
 * * навигационный граф собирается из feature-модулей, сама Activity про экраны не знает.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * Общий на Activity ViewModel списка приложений: вкладки переключаются навигацией, и запись
     * экрана приложений уничтожается — без общего экземпляра поиск и список терялись бы.
     */
    private val launcherViewModel: LauncherViewModel by viewModels()

    private var readerEventsRegistered = false

    private var progressSyncRunning = false

    /** События читалки: «книгу открыли» — повод прочитать позицию немедленно. */
    private val readerEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            launchProgressSync()
        }
    }

    /** Решает, показывать ли онбординг: пока источников нет, это единственный вход в приложение. */
    @Inject
    lateinit var sourceRepository: SourceRepository

    /** Синхронизация библиотеки с файловой системой при возврате в приложение. */
    @Inject
    lateinit var librarySync: LibrarySync

    /**
     * Сессия чтения: пока читалка открыта, тяжёлые работы уступают.
     *
     * Ставится при запуске читалки (`ReaderRepositoryImpl.openBook`), снимается здесь — когда
     * человек вернулся в приложение.
     */
    @Inject
    lateinit var readerSession: ReaderSession

    /** Прогресс чтения из читалки: читается при возврате в приложение и по событию читалки. */
    @Inject
    lateinit var readingProgressSync: SyncReadingProgress

    /** Расписание опроса читалки: цепочка фоновых работ по настройке интервала. */
    @Inject
    lateinit var readingProgressSchedule: ReadingProgressSyncScheduler

    @Inject
    lateinit var databaseBackupSchedule: DatabaseBackupScheduler

    /** Настройка «сворачивать вместо выхода» действует только в роли домашнего экрана. */
    @Inject
    lateinit var launcherStore: LauncherStore

    /**
     * Возврат в приложение — самый частый случай «я добавил книгу на устройство».
     *
     * Синхронизация инкрементальная: обход отмечает неизменённые файлы «виденными», добавляет
     * новые и убирает исчезнувшие, поэтому вызов на каждый `onStart` дешёв. Повторы чаще
     * 30 секунд отсекает сам `LibrarySync.syncIfStale` — переключение между окнами не должно
     * запускать обход дерева заново.
     */
    override fun onResume() {
        super.onResume()
        // Роль могла измениться, пока мы были в фоне (человек выбрал другой лаунчер).
        homeRoleHeld = homeRole.isHomeRoleHeld()
    }

    override fun onStart() {
        super.onStart()
        // Сначала «мы на экране»: иначе синхронизация ниже решила бы, что человек всё ещё читает,
        // и не стала бы работать, пока приложение открыто.
        readerSession.onAppForeground()
        lifecycleScope.launch { librarySync.syncIfStale() }
        // Человек мог только что закрыть читалку: прогресс читаем сразу, не дожидаясь таймера.
        launchProgressSync()
        lifecycleScope.launch { readingProgressSchedule.startIfEnabled() }
        // Расписание копий пересматривается при каждом запуске: если настройку выключили, работа
        // должна быть снята, а не сработать после запрета.
        lifecycleScope.launch { databaseBackupSchedule.review() }
    }

    override fun onStop() {
        // Ушли в фон — возможно, в читалку: с этого момента тяжёлые работы уступают ей дорогу.
        readerSession.onAppBackground()
        super.onStop()
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
        // Отдельная активити «Приложения» открывает настройки через нас: маршрут приходит в интенте.
        // Нажали Home: открываем выбранный экран сразу, без промежуточной полки.
        homeStartRoute = if (isHomeLaunch(intent)) HomeRolePolicy.startScreen(preferred = homeScreen) else null
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        lifecycleScope.launch {
            launcherStore.state.collect { state ->
                collapseInsteadOfExit = state.collapseInsteadOfExit
                homeScreen = state.homeScreen
            }
        }
        applySystemBars()

        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            // Режим лаунчера можно выключить: тогда нижней панели на полке нет вовсе.
            val launcherState by launcherViewModel.uiState.collectAsStateWithLifecycle()
            // Маршрут нужен политике панелей: «скрывать только на полке» и «кроме приложений»
            // без текущего экрана не отличить друг от друга (ADR-015).
            var currentRoute by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(themeState.systemBarsMode, currentRoute) {
                systemBarsMode = themeState.systemBarsMode
                route = currentRoute
                applySystemBars()
            }

            BookShelfThemeRoot(themeState = themeState) {
                BookShelfNavHost(
                    sourceRepository = sourceRepository,
                    opdsEnabled = themeState.opdsEnabled,
                    routeListener = { route -> currentRoute = route },
                    launcherViewModel = launcherViewModel,
                    showTabBar = LauncherTabs.tabBarVisible(
                        launcherEnabled = launcherState.launcherEnabled,
                        hideTabBar = launcherState.hideTabBar,
                    ),
                    onExit = ::handleExit,
                    startRoute = homeStartRoute,
                    pendingRoute = pendingRoute,
                    onPendingRouteHandled = ::clearPendingRoute,
                )
            }
        }
    }

    /**
     * Маршрут, который нужно открыть после поднятия графа.
     *
     * Отдельная активити «Приложения» не содержит графа настроек: шестерёнка там запускает
     * основное приложение сразу на «Настройках» (ADR-015, фаза 6).
     */
    private var pendingRoute by mutableStateOf<String?>(null)

    /** Экран, с которого открыть граф при запуске по Home; `null` — обычный запуск. */
    private var homeStartRoute: String? = null

    /** Роль домашнего экрана: пересчитывается при каждом возврате на экран и при старте. */
    private var homeRoleHeld = false

    /** «Сворачивать вместо выхода» из лаунчерных настроек; по умолчанию свернуть. */
    private var collapseInsteadOfExit = true

    /** Что открывать по Home: список приложений или полку (настройка лаунчера). */
    private var homeScreen: HomeScreen = HomeScreen.APPS

    /** Контроллер роли HOME: alias и запрос роли живут в :core:applist. */
    private val homeRole by lazy { HomeRoleController(this) }

    /** Маршрут из интента отработан: повторный запуск не должен открывать экран заново. */
    private fun clearPendingRoute() {
        pendingRoute = null
    }

    /** Повторный запуск (singleTop): интент пришёл в живой экземпляр, маршрут берём из него. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeOf(intent)
    }

    /**
     * Маршрут из интента: явный (шестерёнка отдельного экрана) или Home.
     *
     * По Home открывается последний экран из памяти процесса, а если его нет — список приложений
     * [HomeRolePolicy.startScreen]. Интент нашей иконки маршрута не задаёт: остаёмся на полке.
     */
    private fun isHomeLaunch(intent: Intent?): Boolean =
        intent?.hasCategory(Intent.CATEGORY_HOME) == true

    /**
     * Маршрут для навигации после запуска: явный из интента или экран по Home.
     *
     * Отдельная активити «Приложения» открывает настройки через нас. Если Home нажали в уже
     * запущенном приложении (`onNewIntent`), граф уже поднят — идём навигацией; при холодном
     * старте тот же экран задаётся стартовым ([homeStartRoute]).
     */
    private fun routeOf(intent: Intent?): String? {
        val explicit = intent?.getStringExtra(EXTRA_ROUTE)
        if (explicit != null) return explicit
        return HomeRolePolicy.launchScreen(
            isHomeLaunch = isHomeLaunch(intent),
            isLauncherLaunch = intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true,
            preferred = homeScreen,
        )
    }

    /** BACK в корне: в роли HOME сворачиваем приложение, иначе выходим (ADR-015). */
    private fun handleExit() {
        if (HomeRolePolicy.shouldCollapse(homeRoleHeld, collapseInsteadOfExit)) {
            moveTaskToBack(true)
        } else {
            finish()
        }
    }

    /** Режим панелей из настроек; до первой подписки — «скрывать всегда», как раньше. */
    private var systemBarsMode: SystemBarsMode = SystemBarsMode.HIDE_ALWAYS

    /** Текущий маршрут графа; `null` — граф ещё не поднялся, считаем полкой. */
    private var route: String? = null

    /**
     * Применить режим системных панелей к текущему экрану (ADR-015).
     *
     * На e-ink панели съедают полосу у контента и перерисовываются при каждом касании, поэтому
     * по умолчанию они скрыты; но на карточке книги и в списке приложений пользователю нужны
     * заряд и сеть, и режимы «только на полке» / «кроме приложений» показывают их там.
     * Полностью не убираем: без системной навигации не выйти из приложения, поэтому панели
     * возвращаются свайпом от края.
     */
    private fun applySystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val hideBars = SystemBarsPolicy.shouldHideBars(
            mode = systemBarsMode,
            isShelf = (route ?: LibraryRoute.route) == LibraryRoute.route,
            isApps = route == AppsRoute.route,
        )
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideBars) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Возврат приложения из фона снова применяет режим панелей. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }
}

/** Действие события читалки: «книгу открыли», «позиция изменилась» (спайк 29.09.2026). */
private const val READER_EVENT_ACTION = "com.fbreader.action.API_CALLBACK"

/** Маршрут для основного приложения: открыть конкретный экран после старта (фаза 6). */
const val EXTRA_ROUTE: String = "io.github.bookshelf.extra.ROUTE"
