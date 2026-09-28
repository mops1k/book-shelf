package io.github.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.map
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.feature.bookdetails.BookDetailsRoute
import io.github.bookshelf.feature.bookdetails.bookDetailsScreen
import io.github.bookshelf.feature.catalogs.EntryRoute
import io.github.bookshelf.feature.catalogs.catalogsScreen
import io.github.bookshelf.feature.catalogs.opdsEntryScreen
import io.github.bookshelf.feature.catalogs.CatalogsRoute
import io.github.bookshelf.feature.collections.CollectionsRoute
import io.github.bookshelf.feature.collections.collectionsScreen
import io.github.bookshelf.feature.filters.FiltersRoute
import io.github.bookshelf.feature.filters.filtersScreen
import io.github.bookshelf.feature.library.LibraryRoute
import io.github.bookshelf.feature.library.libraryScreen
import io.github.bookshelf.feature.onboarding.OnboardingRoute
import io.github.bookshelf.feature.onboarding.onboardingScreen
import io.github.bookshelf.feature.scan.ScanRoute
import io.github.bookshelf.feature.scan.scanScreen
import io.github.bookshelf.feature.search.SearchRoute
import io.github.bookshelf.feature.search.searchScreen
import io.github.bookshelf.feature.collections.CollectionContentRoute
import io.github.bookshelf.feature.collections.collectionContentScreen
import io.github.bookshelf.feature.settings.SettingsRoute
import io.github.bookshelf.feature.settings.SettingsSectionKind
import io.github.bookshelf.feature.settings.settingsScreen

/**
 * Навигационный граф приложения.
 *
 * `:app` знает только маршруты и переходы между экранами; сами экраны регистрируются
 * feature-модулями через расширения `NavGraphBuilder.<экран>Screen(...)`, поэтому добавление
 * экрана не требует правок этого файла — только новую строку регистрации.
 *
 * Анимации переходов не настраиваются: на e-ink переход — мгновенная смена кадра, а полный
 * перерисон запрашивает `EinkRefreshBridge` (docs/eink-ux.md §4, §5).
 */
/**
 * Стартовый экран выбирается по наличию источников.
 *
 * Раньше старт всегда был полкой, а онбординг оставался недостижимым: добавить папку SAF было
 * нечем, источников ноль, «Сканировать» уходил с «нет источника», и каждый экран показывал своё
 * пустое состояние. Слой данных был готов, но добраться до него из интерфейса было нельзя.
 */
@Composable
fun BookShelfNavHost(
    navController: NavHostController = rememberNavController(),
    sourceRepository: SourceRepository,
    onExit: () -> Unit = rememberExitAction(),
) {
    val hasSources by sourceRepository.observeSources()
        .map { it.isNotEmpty() }
        .collectAsState(initial = true)

    NavHost(
        navController = navController,
        // Пока источников нет, единственный способ их завести — онбординг.
        startDestination = if (hasSources) LibraryRoute.route else OnboardingRoute.route,
    ) {

        libraryScreen(
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
            onOpenSearch = { navController.navigate(SearchRoute.route) },
            onOpenFilters = { navController.navigate(FiltersRoute.route) },
            onOpenCollections = { navController.navigate(CollectionsRoute.route) },
            onOpenCatalogs = { navController.navigate(CatalogsRoute.createRoute()) },
            onOpenScan = { navController.navigate(ScanRoute.route) },
            onOpenSettings = { navController.navigate(SettingsRoute.route) },
            // Аппаратный BACK и Page Up на корневом экране: экран перехватывает BACK,
            // поэтому выход из приложения обрабатывается здесь (docs/eink-ux.md §3.2).
            onExit = onExit,
        )

        bookDetailsScreen(
            onBack = { navController.popBackStack() },
            onOpenCollections = { navController.navigate(CollectionsRoute.route) },
        )

        collectionsScreen(
            onBack = { navController.popBackStack() },
            // Раньше переход вёл на тот же список полок: открыть полку и посмотреть, что
            // в ней, было негде. Теперь это отдельный маршрут.
            onOpenCollection = { id -> navController.navigate(CollectionContentRoute.createRoute(id)) },
        )

        collectionContentScreen(
            onBack = { navController.popBackStack() },
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
        )

        searchScreen(
            onBack = { navController.popBackStack() },
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
        )

        filtersScreen(
            onBack = { navController.popBackStack() },
            // (состояние фильтров — часть навигационного состояния, docs/architecture.md §3.5).
            onApply = { navController.popBackStack() },
        )

        scanScreen(
            onBack = { navController.popBackStack() },
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
        )

        onboardingScreen(
            // После добавления папки уходим на полку: онбординг был стартовым, и popBackStack
            // на нём ничего бы не вернул — только закрыл бы приложение.
            onFinished = {
                navController.navigate(LibraryRoute.route) {
                    popUpTo(OnboardingRoute.route) { inclusive = true }
                }
            },
        )

        catalogsScreen(
            onBack = { navController.popBackStack() },
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
            // Книга в листинге открывается карточкой записи: описание и кнопка «Скачать».
            onOpenEntry = { catalogId, entryId, feedUrl ->
                navController.navigate(EntryRoute.createRoute(catalogId, entryId, feedUrl))
            },
        )

        opdsEntryScreen(
            onBack = { navController.popBackStack() },
            onDownloaded = { navController.popBackStack() },
        )

        settingsScreen(
            onBack = { navController.popBackStack() },
            // Разделы настроек без своих экранов молча игнорируются: ниже открывается только OPDS.
            onOpenSection = { section ->
                // Из настроек достижим только каталоги: остальные разделы ещё без экранов.
                if (section == SettingsSectionKind.OPDS) {
                    navController.navigate(CatalogsRoute.createRoute())
                }
            },
        )

    }
}

/**
 * Действие «выход» по умолчанию для корневого экрана.
 *
 * STUB(Sprint 4): в настройках появится выбор «свернуть вместо выхода», и это действие начнёт
 * читать настройку; сейчас — закрытие Activity, как ожидает пользователь от кнопки «Назад»
 * в корне.
 */
@Composable
private fun rememberExitAction(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { (context as? android.app.Activity)?.finish() }
    }
}
