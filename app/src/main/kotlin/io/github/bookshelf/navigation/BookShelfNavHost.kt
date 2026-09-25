package io.github.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.bookshelf.feature.bookdetails.BookDetailsRoute
import io.github.bookshelf.feature.bookdetails.bookDetailsScreen
import io.github.bookshelf.feature.catalogs.catalogsScreen
import io.github.bookshelf.feature.collections.CollectionsRoute
import io.github.bookshelf.feature.collections.collectionsScreen
import io.github.bookshelf.feature.filters.FiltersRoute
import io.github.bookshelf.feature.filters.filtersScreen
import io.github.bookshelf.feature.library.LibraryRoute
import io.github.bookshelf.feature.library.libraryScreen
import io.github.bookshelf.feature.onboarding.onboardingScreen
import io.github.bookshelf.feature.scan.ScanRoute
import io.github.bookshelf.feature.scan.scanScreen
import io.github.bookshelf.feature.search.SearchRoute
import io.github.bookshelf.feature.search.searchScreen
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
@Composable
fun BookShelfNavHost(
    navController: NavHostController = rememberNavController(),
    onExit: () -> Unit = rememberExitAction(),
) {
    NavHost(navController = navController, startDestination = LibraryRoute.route) {

        libraryScreen(
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
            onOpenSearch = { navController.navigate(SearchRoute.route) },
            onOpenFilters = { navController.navigate(FiltersRoute.route) },
            onOpenCollections = { navController.navigate(CollectionsRoute.route) },
            onOpenScan = { navController.navigate(ScanRoute.route) },
            // Аппаратный BACK и Page Up на корневом экране: экран перехватывает BACK,
            // поэтому выход из приложения обрабатывается здесь (docs/eink-ux.md §3.2).
            onExit = onExit,
        )

        bookDetailsScreen(
            onBack = { navController.popBackStack() },
            // STUB(Sprint 5): открытие книги в FBReader через ReaderRepository.openBook.
            onRead = { _ -> },
            onOpenCollections = { navController.navigate(CollectionsRoute.route) },
            // STUB(Sprint 7): экран ручного редактирования метаданных.
            onEditMetadata = { _ -> },
        )

        collectionsScreen(
            onBack = { navController.popBackStack() },
            // STUB(Sprint 7): показ содержимого конкретной полки (сейчас полка не открывается).
            onOpenCollection = { _ -> },
        )

        searchScreen(
            onBack = { navController.popBackStack() },
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
        )

        filtersScreen(
            onBack = { navController.popBackStack() },
            // STUB(Sprint 4): выбранные фильтры должны вернуться на полку и примениться
            // (состояние фильтров — часть навигационного состояния, docs/architecture.md §3.5).
            onApply = { navController.popBackStack() },
        )

        scanScreen(
            onBack = { navController.popBackStack() },
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
        )

        onboardingScreen(
            // STUB(Sprint 2): онбординг показывается при первом запуске (признак — нет источников),
            // после добавления папки уходит с заднего стека.
            onFinished = { navController.popBackStack() },
        )

        catalogsScreen(
            onBack = { navController.popBackStack() },
            onOpenBook = { bookId -> navController.navigate(BookDetailsRoute.createRoute(bookId)) },
        )

        settingsScreen(
            onBack = { navController.popBackStack() },
            // STUB(Sprint 5): переход к конкретному разделу настроек.
            onOpenSection = { _ -> },
        )

        // STUB(Sprint 4): точки входа для «Настройки» и «Каталоги» на полке пока нет — они появятся
        // как действия в верхней панели (docs/eink-ux.md §6.2); онбординг станет стартовым экраном
        // при первом запуске (признак — нет ни одного источника, Sprint 2).
        // Маршруты зарегистрированы заранее, чтобы граф был полным и проверялся компилятором.
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
