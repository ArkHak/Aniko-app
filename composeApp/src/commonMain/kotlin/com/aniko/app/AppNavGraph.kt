package com.aniko.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.aniko.app.feature.collections.CollectionsScreen
import com.aniko.app.feature.comments.ReleaseCommentsScreen
import com.aniko.app.feature.feed.FeedScreen
import com.aniko.app.feature.gallery.TokenGalleryScreen
import com.aniko.app.feature.home.HomeScreen
import com.aniko.app.feature.library.LibraryScreen
import com.aniko.app.feature.notifications.NotificationsScreen
import com.aniko.app.feature.player.PlayerScreen
import com.aniko.app.feature.profile.ProfileScreen
import com.aniko.app.feature.release.ReleaseDetailsScreen
import com.aniko.app.feature.schedule.ScheduleScreen
import com.aniko.app.feature.search.SearchScreen
import com.aniko.app.feature.settings.NotificationSettingsScreen
import com.aniko.app.feature.settings.SettingsScreen
import com.aniko.app.navigation.AnixDestination
import com.aniko.app.navigation.DetailPaneRoute
import com.aniko.app.navigation.DetailPaneStack
import com.aniko.app.navigation.ListDetailHost
import com.aniko.app.navigation.TitleNavigator
import com.aniko.app.navigation.navigateToTabRoot
import com.aniko.data.locale.LocaleStore
import com.aniko.data.theme.ThemeStore

/**
 * Граф маршрутов основного каркаса — вынесен из [App.kt] в отдельный файл (detekt
 * `TooManyFunctions`). Сами маршруты разложены по трём `NavGraphBuilder`-расширениям ниже.
 */
@Suppress("LongParameterList") // themeStore добавлен аддитивно к уже существовавшему набору
// параметров (тот же случай, что и `AnixSessionGate` в App.kt) — граф маршрутов остаётся тонким
// прокси без собственного стейта, дробить дальше означало бы заводить объект конфигурации ради
// самого счётчика.
@Composable
internal fun AnixNavGraph(
    navController: NavHostController,
    paneStack: DetailPaneStack,
    titleNavigator: TitleNavigator,
    localeStore: LocaleStore,
    themeStore: ThemeStore,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AnixDestination.Home,
        modifier = modifier,
    ) {
        listSectionRoutes(navController, paneStack, titleNavigator)
        titleDetailRoutes(navController, titleNavigator)
        chromeRoutes(navController, localeStore, themeStore)
    }
}

/**
 * Переносит открытый тайтл между маршрутом [com.aniko.app.navigation.AdaptiveTitleNavigator]
 * (compact) и [DetailPaneStack] (wide) при смене [isTwoPane] — вынесена из [App.kt] в отдельный
 * файл (detekt `TooManyFunctions`).
 *
 * Известное упрощение: обрабатывает только САМЫЙ ВЕРХНИЙ элемент соответствующего стека. Если на
 * compact параллельно открыты и `Details`, и `Comments` (`Comments` поверх `Details` в истории
 * [navController]), при переходе на wide смигрирует только верхний `Comments` — `Details`
 * останется висеть в истории [navController] непереехавшим. Не создаёт видимых проблем на
 * практике ([DetailPaneStack.open] всё равно требует `Details` под `Comments`, см. её KDoc), но
 * не покрывает произвольную глубину стека — полная миграция потребовала бы обхода всего
 * `NavController.currentBackStack`, что не укладывалось по объёму Фазы 5.
 */
internal fun migratePaneRoutes(
    navController: NavHostController,
    paneStack: DetailPaneStack,
    isTwoPane: Boolean,
) {
    if (isTwoPane) {
        val entry = navController.currentBackStackEntry
        when {
            entry != null && entry.destination.hasRoute(AnixDestination.ReleaseDetails::class) -> {
                val route = entry.toRoute<AnixDestination.ReleaseDetails>()
                paneStack.open(DetailPaneRoute.Details(route.releaseId))
                navController.popBackStack()
            }
            entry != null && entry.destination.hasRoute(AnixDestination.ReleaseComments::class) -> {
                val route = entry.toRoute<AnixDestination.ReleaseComments>()
                paneStack.open(DetailPaneRoute.Comments(route.releaseId))
                navController.popBackStack()
            }
        }
    } else {
        paneStack.entries.forEach { route -> navController.navigate(route.toDestination()) }
        paneStack.clear()
    }
}

internal fun DetailPaneRoute.toDestination(): AnixDestination =
    when (this) {
        is DetailPaneRoute.Details -> AnixDestination.ReleaseDetails(releaseId)
        is DetailPaneRoute.Comments -> AnixDestination.ReleaseComments(releaseId)
    }

/**
 * Четыре list-секции каркаса (P5.T5) — каждая оборачивается в [ListDetailHost] (P5.T3).
 *
 * `enterTransition`/`exitTransition` = `None` на все четыре — иначе действует дефолт самого
 * `NavHost` (не переопределён нигде в [AnixNavGraph]): `androidx.navigation:navigation-compose`
 * с версии 2.8 анимирует КАЖДЫЙ `composable()` без явного оверрайда через `fadeIn(tween(700))`/
 * `fadeOut(tween(700))` (сверено с реальным `NavHost.kt` из sources-jar артефакта
 * `navigation-compose-android:2.9.7`). Живая проверка на эмуляторе (`dumpsys gfxinfo`) подтвердила
 * реальный джанк при переключении вкладок (78–100% janky-кадров), но A/B-тест того же перехода с
 * этими `None` против дефолтного fade НЕ показал измеримой разницы (в пределах шума), а джанк той
 * же интенсивности воспроизвёлся даже на локальной рекомпозиции без единой навигации — то есть
 * жалоба "фриз при переключении вкладок", скорее всего, НЕ объясняется полностью этим 700мс-фейдом
 * (честный вывод расследования, не переоценивать). Оставлено как отдельное валидное улучшение:
 * bottom-таб-бары по конвенции Material Design (см. Now-in-Android) не анимируют переключение
 * между вкладками верхнего уровня — мгновенный переход здесь корректен сам по себе, независимо от
 * того, решает ли он замеченный джанк целиком. `titleDetailRoutes`/`chromeRoutes` ниже (drill-down
 * на карточку релиза/комментарии/плеер/настройки) сохраняют дефолтный fade — он уместен для
 * перехода "вглубь", не между вкладками. Единственное исключение — `AnixDestination.Profile`
 * в `chromeRoutes`: это 5-я вкладка таб-бара (см. [com.aniko.app.navigation.AnixSection]),
 * поэтому она обязана переключаться так же мгновенно, как и эти четыре, хотя физически объявлена
 * в другой функции (её `composable` читает `themeStore`/навигацию в `actions`, которых нет у
 * этой группы маршрутов, см. KDoc `chromeRoutes`).
 */
private fun NavGraphBuilder.listSectionRoutes(
    navController: NavHostController,
    paneStack: DetailPaneStack,
    titleNavigator: TitleNavigator,
) {
    composable<AnixDestination.Home>(
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        ListDetailHost(paneStack) {
            HomeScreen(
                onReleaseClick = titleNavigator::openTitle,
                onCatalogClick = { navController.navigateToTabRoot(AnixDestination.Search) },
                onScheduleClick = { navController.navigateToTabRoot(AnixDestination.Schedule) },
                // Track C (2026-09-04): в макете плитка "Filters" (была "Library") ведёт в тот же
                // Catalog, что и "Popular" — не в `AnixDestination.Library` (он остаётся доступен
                // через нижнюю навигацию, вкладка "Мои списки", просто эта конкретная плитка Home
                // больше туда не ведёт, см. KDoc `HomeQuickActions`).
                onFilterClick = { navController.navigateToTabRoot(AnixDestination.Search) },
                onFeedClick = { navController.navigate(AnixDestination.Feed) },
                onCollectionsClick = { navController.navigate(AnixDestination.Collections) },
            )
        }
    }
    composable<AnixDestination.Search>(
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        ListDetailHost(paneStack) { SearchScreen(onReleaseClick = titleNavigator::openTitle) }
    }
    composable<AnixDestination.Schedule>(
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        ListDetailHost(paneStack) { ScheduleScreen() }
    }
    composable<AnixDestination.Library>(
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        ListDetailHost(paneStack) { LibraryScreen(onReleaseClick = titleNavigator::openTitle) }
    }
}

/**
 * Полноэкранные маршруты карточки релиза/комментариев/плеера. Сюда [TitleNavigator] ведёт на
 * Compact и Medium всегда; на Expanded (Desktop, 2026-09-15) карточка/комментарии живут в правом
 * ящике [ListDetailHost] поверх списка (см. её KDoc), поэтому эти два маршрута там остаются
 * только для входов мимо навигатора (deep link, ссылки из chrome-экранов вроде Profile). Плеер —
 * полноэкранный маршрут на всех размерах окна.
 */
private fun NavGraphBuilder.titleDetailRoutes(
    navController: NavHostController,
    titleNavigator: TitleNavigator,
) {
    composable<AnixDestination.ReleaseDetails> { entry ->
        val route: AnixDestination.ReleaseDetails = entry.toRoute()
        ReleaseDetailsScreen(
            releaseId = route.releaseId,
            pendingEpisodeSourceId = route.pendingEpisodeSourceId,
            pendingEpisodePosition = route.pendingEpisodePosition,
            onEpisodeClick = titleNavigator::openPlayer,
        )
    }
    composable<AnixDestination.ReleaseComments> { entry ->
        val route: AnixDestination.ReleaseComments = entry.toRoute()
        ReleaseCommentsScreen(releaseId = route.releaseId)
    }
    // Без анимаций (как и четыре таб-рута выше, см. KDoc `listSectionRoutes`): дефолтный
    // 700ms fade NavHost при входе/выходе плеера держал в композиции ДВА экрана плеера
    // одновременно (старый entry жив, пока не завершится exit-переход) — два живых WebView с
    // видео поверх друг друга («два плеера дублируются и накладываются», жалоба 2026-09-08,
    // см. журнал). Видео-поверхности переключаются мгновенно, без кросс-фейда — как у всех
    // нормальных видеоплееров.
    composable<AnixDestination.Player>(
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) { entry ->
        val route: AnixDestination.Player = entry.toRoute()
        PlayerScreen(
            releaseId = route.releaseId,
            sourceId = route.sourceId,
            position = route.position,
            hostKey = route.hostKey,
            // Не `titleNavigator.back()`: на wide-экранах он сначала разбирает стек detail-панелей,
            // а плеер лежит полноэкранным маршрутом ПОВЕРХ них — «назад» из плеера обязан снимать
            // именно маршрут (см. KDoc `PlayerScreen.onBack`).
            onBack = { navController.popBackStack() },
        )
    }
}

/**
 * Profile (таб) + Settings и остальные экраны, открываемые из него (NotificationSettings).
 * Маршрут `TokenGallery` тоже объявлен здесь, но из UI недостижим: пункт «Дизайн-токены» убран
 * из `SettingsScreen` по запросу пользователя (2026-09-21), сам экран оставлен в коде.
 *
 * P13.T2 развернула прежний поток: раньше `Settings` был вкладкой таб-бара, а `Profile` —
 * дочерним экраном («Настройки» → «Мой профиль»). Теперь `Profile` сам вкладка таб-бара
 * (см. [AnixSection]), а `Settings` — дочерний маршрут, открываемый шестерёнкой из `TopAppBar`
 * `ProfileScreen.kt` ([ProfileScreen.onSettingsClick]).
 *
 * Живой фидбек пользователя (2026-09-11) развернул часть P13.T2 про тему: `AnixThemePicker`
 * переезжает ОБРАТНО на `SettingsScreen` (был там до P13.T2, затем P13.T2 перенёс его на
 * `ProfileScreen` под мокап — теперь пользователь явно попросил вернуть) — отсюда `themeStore`
 * читается снова для `AnixDestination.Settings`, а не для `Profile`. Язык (`AnixLanguagePicker`)
 * этой правкой не тронут — остаётся в `Settings`, как и был всё это время. Футер сайдбара
 * (`sidebarFooter` `AdaptiveScaffold`) с 2026-09-18 занят кнопками уведомлений/настроек
 * (`SidebarChromeActions` в `App.kt`) — они переехали туда из топбара профиля по запросу
 * пользователя; переключателя языка там больше нет (убран 2026-09-17 как дубль пункта «Язык»).
 *
 * `AnixDestination.Profile` ниже явно оверрайдит `enterTransition`/`exitTransition` на `None` —
 * это 5-я вкладка таб-бара (см. [AnixSection]), обязана переключаться так же мгновенно, как
 * четыре маршрута `listSectionRoutes` (см. её подробный KDoc), просто физически объявлена здесь,
 * а не там, по причинам выше (тема/навигация в `actions`), не по причине другого UX-поведения.
 * Остальные маршруты этой функции (Settings/Notifications/Feed/Collections/TokenGallery/
 * NotificationSettings) — дочерние drill-down экраны, дефолтный fade для них уместен.
 */
private fun NavGraphBuilder.chromeRoutes(
    navController: NavHostController,
    localeStore: LocaleStore,
    themeStore: ThemeStore,
) {
    composable<AnixDestination.Settings> {
        val languageTag by localeStore.languageTag.collectAsStateWithLifecycle()
        val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNotificationsClick = { navController.navigate(AnixDestination.NotificationSettings) },
            languageTag = languageTag,
            onLanguageTagChange = localeStore::setLanguageTag,
            themeMode = themeMode,
            onThemeModeChange = themeStore::setThemeMode,
        )
    }
    composable<AnixDestination.Profile>(
        // Profile — 5-я вкладка таб-бара (см. KDoc функции выше и KDoc `listSectionRoutes`) —
        // должна переключаться так же мгновенно, как Home/Search/Schedule/Library, а не с
        // дефолтным 700ms fade `navigation-compose`.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        ProfileScreen(
            onSettingsClick = { navController.navigate(AnixDestination.Settings) },
            onNotificationsClick = { navController.navigate(AnixDestination.Notifications) },
            // Не `titleNavigator::openTitle`: маршрут профиля не завёрнут в `ListDetailHost`, и на
            // wide-экранах навигатор открыл бы тайтл в панели, которой здесь негде отрисоваться
            // (см. KDoc `ProfileScreen.onReleaseClick`) — отсюда всегда полноэкранный маршрут.
            onReleaseClick = { releaseId ->
                navController.navigate(AnixDestination.ReleaseDetails(releaseId))
            },
            // Track A (точное соответствие макету): ссылка "My Lists →" в шапке профиля ведёт на
            // тот же маршрут, что и вкладка таб-бара `Library` (см. KDoc `ProfileScreen.onOpenLists`).
            onOpenLists = { navController.navigate(AnixDestination.Library) },
        )
    }
    composable<AnixDestination.Notifications> {
        NotificationsScreen(
            onBack = { navController.popBackStack() },
            onReleaseClick = { releaseId ->
                navController.navigate(AnixDestination.ReleaseDetails(releaseId))
            },
        )
    }
    composable<AnixDestination.Feed> {
        FeedScreen(onBack = { navController.popBackStack() })
    }
    composable<AnixDestination.Collections> {
        CollectionsScreen(onBack = { navController.popBackStack() })
    }
    composable<AnixDestination.TokenGallery> {
        TokenGalleryScreen(onBack = { navController.popBackStack() })
    }
    composable<AnixDestination.NotificationSettings> {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
}
