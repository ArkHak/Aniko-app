package com.aniko.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aniko.app.feature.auth.LoginScreen
import com.aniko.app.feature.comments.ReleaseCommentsScreen
import com.aniko.app.feature.gallery.TokenGalleryScreen
import com.aniko.app.feature.home.HomeScreen
import com.aniko.app.feature.library.LibraryScreen
import com.aniko.app.feature.player.PlayerScreen
import com.aniko.app.feature.profile.ProfileScreen
import com.aniko.app.feature.release.ReleaseDetailsScreen
import com.aniko.app.feature.schedule.ScheduleScreen
import com.aniko.app.feature.search.SearchScreen
import com.aniko.app.feature.settings.NotificationSettingsScreen
import com.aniko.app.feature.settings.SettingsScreen
import com.aniko.app.navigation.AnixDestination
import com.aniko.app.navigation.AnixSection
import com.aniko.app.navigation.DeepLinkDispatcher
import com.aniko.app.navigation.DetailPaneRoute
import com.aniko.app.navigation.DetailPaneStack
import com.aniko.app.navigation.ListDetailHost
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.app.navigation.TitleNavigator
import com.aniko.app.navigation.parseDeepLink
import com.aniko.app.navigation.rememberDetailPaneStack
import com.aniko.app.navigation.rememberTitleNavigator
import com.aniko.data.locale.LocaleStore
import com.aniko.data.repository.AuthRepository
import com.aniko.data.session.SessionState
import com.aniko.data.sync.SyncCoordinator
import com.aniko.data.theme.ThemeStore
import com.aniko.ui.adaptive.AdaptiveNavItem
import com.aniko.ui.adaptive.AdaptiveScaffold
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.adaptive.rememberAnixWindowSize
import com.aniko.ui.component.AnixLanguagePicker
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.AnixOfflineBanner
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.ProvideAppStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.image.createAnixImageLoader
import com.aniko.ui.theme.AnixThemeTokens
import com.aniko.ui.theme.AppTheme
import io.ktor.client.HttpClient
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

/**
 * Корневой Composable приложения. Одинаков для Android, Desktop и iOS —
 * платформы отличаются только точкой входа (Activity / main() / MainViewController).
 *
 * [onBackHandlerReady] — мост наружу для desktop-хрома (P5.T6/P5.T1): `Main.kt` вызывает
 * `AnixMenuBar`/меню "Go → Back" СНАРУЖИ дерева `App()` (сиблинг внутри `Window { }`, не потомок),
 * поэтому `LocalTitleNavigator` ему физически не виден через CompositionLocal — единственный
 * стабильный способ добраться до актуального `TitleNavigator.back()` оттуда — получить ссылку на
 * функцию через этот колбэк, вызываемый один раз, как только навигатор создан внутри
 * `AnixAppScaffold`. На Android/iOS параметр не передаётся (дефолт `{}` — no-op), там нет
 * системного меню, которому это нужно.
 */
@Composable
fun App(onBackHandlerReady: (() -> Boolean) -> Unit = {}) {
    KoinContext {
        val httpClient = koinInject<HttpClient>()
        val authRepository = koinInject<AuthRepository>()
        val localeStore = koinInject<LocaleStore>()
        val themeStore = koinInject<ThemeStore>()
        val syncCoordinator = koinInject<SyncCoordinator>()
        val platformContext = LocalPlatformContext.current

        // Coil ходит в сеть тем же Ktor-клиентом, что и API.
        remember(httpClient, platformContext) {
            SingletonImageLoader.setSafe { context ->
                createAnixImageLoader(context, httpClient)
            }
        }

        // Владелец bootstrap() — корневой уровень: гейтинг навигации ниже зависит от
        // sessionState, поэтому чтение токена должно стартовать здесь, а не в фичах.
        // bootstrap() идемпотентен, повторный вызов из HomeViewModel (если он там остался) — no-op.
        LaunchedEffect(authRepository) {
            authRepository.bootstrap()
        }

        // Фаза 10 (P10.T1/T2). Заменяет разовый `syncQueueWorker.drain()` времён P4.T7: тот
        // единичный дренаж теперь входит в [SyncCoordinator] как переход Unknown → Online, и
        // отдельным вызовом стал бы дублем. `start()` идемпотентен, рекомпозиция его не повторит.
        LaunchedEffect(syncCoordinator) {
            syncCoordinator.start()
        }

        // P10.T3 — единственный источник сетевого статуса для UI.
        val connectivity by syncCoordinator.connectivity.collectAsStateWithLifecycle()

        // Язык — читается из LocaleStore (P2.T11) и прокидывается в ProvideAppStrings (P2.T7/T8),
        // а не наоборот: shared/ui ничего не знает про DI/Settings, только про Compose-механику
        // Lyricist (см. KDoc ProvideAppStrings). null — «следовать системной локали».
        val languageTag by localeStore.languageTag.collectAsStateWithLifecycle()

        // Тема — читается из ThemeStore тем же способом, что и язык выше. null — «следовать
        // системной теме» (тогда используется isSystemInDarkTheme(), как и раньше до появления
        // ручного переключателя).
        val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()

        // Единственный авторитет размера окна (P5.T4) — вычисляется один раз на корневом уровне и
        // прокидывается через CompositionLocal, чтобы AdaptiveScaffold/ListDetailHost/TitleNavigator
        // (все ниже по дереву) видели одно и то же значение без повторного вычисления.
        val windowSize = rememberAnixWindowSize()

        AppTheme(
            darkTheme =
                when (themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                },
        ) {
            ProvideAppStrings(languageTag = languageTag) {
                CompositionLocalProvider(LocalAnixWindowSize provides windowSize) {
                    // Баннер офлайна (P10.T3) — над гейтом сессии, а не внутри него: он должен быть
                    // виден и на экране входа (без сети войти нельзя, и это надо объяснить), и во
                    // всём основном каркасе. Column, а не Box: баннер раздвигает контент, а не
                    // накрывает его — см. KDoc AnixOfflineBanner.
                    val isOffline = connectivity.isOffline
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnixOfflineBanner(visible = isOffline)
                        AnixSessionGate(
                            authRepository = authRepository,
                            localeStore = localeStore,
                            languageTag = languageTag,
                            themeStore = themeStore,
                            onBackHandlerReady = onBackHandlerReady,
                            // Пока баннер виден, зону статус-бара занимает он — об этом надо
                            // сообщить поддереву ниже, иначе `Scaffold` внутри `AdaptiveScaffold`
                            // отступит на неё второй раз и между баннером и контентом появится
                            // пустая полоса. Scaffold вычитает уже поглощённые предками insets
                            // (`onConsumedWindowInsetsChanged` в его реализации), поэтому одного
                            // [consumeWindowInsets] достаточно — трогать сам AdaptiveScaffold не
                            // нужно. Ветка `Modifier` (no-op) обязательна: без баннера отступ
                            // статус-бара должен остаться за Scaffold, как и был.
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .then(
                                        if (isOffline) {
                                            Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                        } else {
                                            Modifier
                                        },
                                    ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Реактивный гейт по [AuthRepository.sessionState]:
 * - [SessionState.Loading] — сплэш-лоадер, пока не прочитан токен;
 * - [SessionState.Unauthorized] — экран входа;
 * - [SessionState.Authorized] — основной граф из пяти секций (P5.T2: добавлена Schedule).
 *
 * Также слушает [AuthRepository.sessionExpired] (401/403 от бэкенда) и показывает
 * одноразовый снекбар — сама навигация на логин при этом переключается через sessionState.
 */
@Composable
@Suppress("LongParameterList") // themeStore добавлен аддитивно к уже существовавшему набору
// параметров (localeStore/languageTag threading, тот же паттерн, что и у SettingsScreen) —
// группировка сторов в отдельный объект ради обхода линта добавила бы косвенность без пользы.
private fun AnixSessionGate(
    authRepository: AuthRepository,
    localeStore: LocaleStore,
    languageTag: String?,
    themeStore: ThemeStore,
    onBackHandlerReady: (() -> Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionState by authRepository.sessionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val sessionExpiredMessage = LocalStrings.current.sessionExpiredMessage
    LaunchedEffect(authRepository, sessionExpiredMessage) {
        authRepository.sessionExpired.collect {
            snackbarHostState.showSnackbar(sessionExpiredMessage)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (sessionState) {
            SessionState.Loading -> AnixLoadingBox()
            SessionState.Unauthorized -> LoginScreen()
            is SessionState.Authorized -> AnixAppScaffold(localeStore, languageTag, themeStore, onBackHandlerReady)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * [AnixSection] → [AdaptiveNavItem] — лейблы из [LocalStrings], иконки из material-icons-extended.
 *
 * P13.T1 (сверка с мокапом Claude Design): иконки и лейблы трёх вкладок сменились —
 * `Search`→`grid_view`/«Каталог» (было `search`/«Поиск», сама Kotlin-константа не переименована,
 * см. KDoc [AnixSection]), `Library`→`bookmark`/«Мои списки» (было `video_library`/«Списки»),
 * последняя вкладка теперь `Profile`→`person`/«Профиль» вместо `Settings`→`settings`/«Настройки».
 * `Home`/`Schedule` совпадали с мокапом уже до этой фазы, не менялись.
 */
private fun AnixSection.toNavItem(strings: Strings): AdaptiveNavItem =
    when (this) {
        AnixSection.Home -> AdaptiveNavItem(name, strings.navHome, Icons.Outlined.Home, Icons.Filled.Home)
        AnixSection.Search -> AdaptiveNavItem(name, strings.navCatalog, Icons.Outlined.GridView, Icons.Filled.GridView)
        AnixSection.Library -> AdaptiveNavItem(name, strings.navLibrary, Icons.Outlined.Bookmark, Icons.Filled.Bookmark)
        AnixSection.Schedule ->
            AdaptiveNavItem(name, strings.navSchedule, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth)
        AnixSection.Profile -> AdaptiveNavItem(name, strings.navProfile, Icons.Outlined.Person, Icons.Filled.Person)
    }

/**
 * Основной каркас после логина (P5.T5 адаптивный каркас + P5.T3 list-detail).
 *
 * [com.aniko.app.navigation.DetailPaneStack] живёт здесь, на уровне каркаса (не внутри отдельных
 * экранов) — она общая для всех четырёх list-секций (Home/Search/Schedule/Library), которые все
 * оборачиваются в один и тот же [ListDetailHost]. [LocalTitleNavigator] предоставляется отсюда же:
 * единственная точка входа для экранов, чтобы попасть на карточку релиза/комментарии/плеер, не
 * зная, идёт ли переход через маршрут или через панель (см. KDoc `TitleNavigator`).
 */
@Composable
private fun AnixAppScaffold(
    localeStore: LocaleStore,
    languageTag: String?,
    themeStore: ThemeStore,
    onBackHandlerReady: (() -> Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val paneStack = rememberDetailPaneStack()
    val windowSize = LocalAnixWindowSize.current
    val titleNavigator = rememberTitleNavigator(navController, paneStack, isTwoPane = { windowSize.isTwoPane })

    LaunchedEffect(titleNavigator, onBackHandlerReady) {
        onBackHandlerReady(titleNavigator::back)
    }

    // Миграция открытого тайтла между маршрутом (compact) и панелью (wide) при смене размера окна
    // (P5.T3 — найдено ревью: без этого шага пользователь "терял" бы открытую карточку релиза при
    // изменении размера окна, т.к. NavController и DetailPaneStack — два независимых источника
    // состояния без моста между ними). Реализация — см. [migratePaneRoutes] ниже.
    LaunchedEffect(windowSize.isTwoPane) {
        migratePaneRoutes(navController, paneStack, isTwoPane = windowSize.isTwoPane)
    }

    // Deep links (P10.T7): DeepLinkDispatcher.pending — StateFlow, а не одноразовый callback,
    // поэтому эта подписка отрабатывает и ссылку, пришедшую холодным стартом ДО того, как
    // AnixAppScaffold собрался (см. KDoc DeepLinkDispatcher про доставку после логина).
    LaunchedEffect(navController) {
        DeepLinkDispatcher.pending.collect { url ->
            if (url != null) {
                parseDeepLink(url)?.let { destination -> navController.navigate(destination) }
                DeepLinkDispatcher.consume()
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedSection =
        AnixSection.entries.find { section ->
            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(section.destination::class) } == true
        }
    val strings = LocalStrings.current
    val navItems = remember(strings) { AnixSection.entries.map { it.toNavItem(strings) } }
    val dimens = AnixThemeTokens.dimens

    CompositionLocalProvider(LocalTitleNavigator provides titleNavigator) {
        AdaptiveScaffold(
            items = navItems,
            selectedItemId = selectedSection?.name,
            // Плеер — "поверх" каркаса, см. KDoc `AdaptiveScaffold.showNavigationChrome`.
            showNavigationChrome = backStackEntry?.destination?.hasRoute(AnixDestination.Player::class) != true,
            onItemClick = { item ->
                val section = AnixSection.valueOf(item.id)
                navController.navigate(section.destination) {
                    // Канонический рецепт сохранения состояния табов (скролл, введённый поиск)
                    // при переключении между секциями — раньше отсутствовал, состояние терялось
                    // при каждом клике (найдено в журнале Фазы 5, P5.T1).
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            sidebarFooter = {
                AnixLanguagePicker(
                    currentTag = languageTag,
                    onSelect = localeStore::setLanguageTag,
                    modifier = Modifier.padding(dimens.spaceM),
                )
            },
        ) { innerPadding ->
            AnixNavGraph(
                navController = navController,
                paneStack = paneStack,
                titleNavigator = titleNavigator,
                localeStore = localeStore,
                themeStore = themeStore,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

/**
 * Переносит открытый тайтл между маршрутом [com.aniko.app.navigation.AdaptiveTitleNavigator]
 * (compact) и [DetailPaneStack] (wide) при смене [isTwoPane] — вынесена из [AnixAppScaffold]
 * отдельной функцией (detekt `LongMethod`).
 *
 * Известное упрощение: обрабатывает только САМЫЙ ВЕРХНИЙ элемент соответствующего стека. Если на
 * compact параллельно открыты и `Details`, и `Comments` (`Comments` поверх `Details` в истории
 * [navController]), при переходе на wide смигрирует только верхний `Comments` — `Details`
 * останется висеть в истории [navController] непереехавшим. Не создаёт видимых проблем на
 * практике ([DetailPaneStack.open] всё равно требует `Details` под `Comments`, см. её KDoc), но
 * не покрывает произвольную глубину стека — полная миграция потребовала бы обхода всего
 * `NavController.currentBackStack`, что не укладывалось по объёму Фазы 5.
 */
private fun migratePaneRoutes(
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

private fun DetailPaneRoute.toDestination(): AnixDestination =
    when (this) {
        is DetailPaneRoute.Details -> AnixDestination.ReleaseDetails(releaseId)
        is DetailPaneRoute.Comments -> AnixDestination.ReleaseComments(releaseId)
    }

/**
 * Граф маршрутов основного каркаса — вынесен из [AnixAppScaffold] отдельной функцией (detekt
 * `LongMethod`), сами маршруты дополнительно разложены по трём `NavGraphBuilder`-расширениям
 * ниже (detekt `LongParameterList`/`LongMethod` на них самих).
 */
@Composable
@Suppress("LongParameterList") // themeStore добавлен аддитивно к уже существовавшему набору
// параметров (тот же случай, что и AnixSessionGate выше) — граф маршрутов остаётся тонким
// прокси без собственного стейта, дробить дальше означало бы заводить объект конфигурации ради
// самого счётчика.
private fun AnixNavGraph(
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

/** Четыре list-секции каркаса (P5.T5) — каждая оборачивается в [ListDetailHost] (P5.T3). */
private fun NavGraphBuilder.listSectionRoutes(
    navController: NavHostController,
    paneStack: DetailPaneStack,
    titleNavigator: TitleNavigator,
) {
    composable<AnixDestination.Home> {
        ListDetailHost(paneStack) {
            HomeScreen(
                onReleaseClick = titleNavigator::openTitle,
                onCatalogClick = { navController.navigate(AnixDestination.Search) },
                onScheduleClick = { navController.navigate(AnixDestination.Schedule) },
                onLibraryClick = { navController.navigate(AnixDestination.Library) },
            )
        }
    }
    composable<AnixDestination.Search> {
        ListDetailHost(paneStack) { SearchScreen(onReleaseClick = titleNavigator::openTitle) }
    }
    composable<AnixDestination.Schedule> {
        ListDetailHost(paneStack) { ScheduleScreen() }
    }
    composable<AnixDestination.Library> {
        ListDetailHost(paneStack) { LibraryScreen(onReleaseClick = titleNavigator::openTitle) }
    }
}

/**
 * Полноэкранные маршруты карточки релиза/комментариев/плеера (compact-размер) — на wide-экранах
 * тот же контент рисуется внутри [ListDetailHost] (см. `DetailPaneContent` в `ListDetailHost.kt`),
 * сюда попадают только когда [TitleNavigator] решил, что панели нет места (см. `AdaptiveTitleNavigator`).
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
    composable<AnixDestination.Player> { entry ->
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
 * Profile (таб) + Settings и остальные экраны, открываемые из него (TokenGallery,
 * NotificationSettings).
 *
 * P13.T2 развернула прежний поток: раньше `Settings` был вкладкой таб-бара, а `Profile` —
 * дочерним экраном («Настройки» → «Мой профиль»). Теперь `Profile` сам вкладка таб-бара
 * (см. [AnixSection]), а `Settings` — дочерний маршрут, открываемый шестерёнкой из `TopAppBar`
 * `ProfileScreen.kt` ([ProfileScreen.onSettingsClick]). Тема (`AnixThemePicker`) физически
 * переехала на `ProfileScreen` вместе с `AchievementsSection` (мокап рисует переключатель темы
 * прямо под шапкой профиля) — поэтому здесь `themeStore` читается уже для `AnixDestination.Profile`,
 * а не для `Settings`. Язык (`AnixLanguagePicker`) остался в `Settings` — решение по умолчанию:
 * мокап явно требует переноса только Theme, а на Desktop переключатель языка и так уже дублируется
 * в `sidebarFooter` `AdaptiveScaffold` (см. `AnixAppScaffold`), так что дополнительного переезда на
 * Profile не требуется для консистентности.
 */
private fun NavGraphBuilder.chromeRoutes(
    navController: NavHostController,
    localeStore: LocaleStore,
    themeStore: ThemeStore,
) {
    composable<AnixDestination.Settings> {
        val languageTag by localeStore.languageTag.collectAsStateWithLifecycle()
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onDesignGalleryClick = { navController.navigate(AnixDestination.TokenGallery) },
            onNotificationsClick = { navController.navigate(AnixDestination.NotificationSettings) },
            languageTag = languageTag,
            onLanguageTagChange = localeStore::setLanguageTag,
        )
    }
    composable<AnixDestination.Profile> {
        val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()
        ProfileScreen(
            onSettingsClick = { navController.navigate(AnixDestination.Settings) },
            // Не `titleNavigator::openTitle`: маршрут профиля не завёрнут в `ListDetailHost`, и на
            // wide-экранах навигатор открыл бы тайтл в панели, которой здесь негде отрисоваться
            // (см. KDoc `ProfileScreen.onReleaseClick`) — отсюда всегда полноэкранный маршрут.
            onReleaseClick = { releaseId -> navController.navigate(AnixDestination.ReleaseDetails(releaseId)) },
            themeMode = themeMode,
            onThemeModeChange = themeStore::setThemeMode,
        )
    }
    composable<AnixDestination.TokenGallery> {
        TokenGalleryScreen(onBack = { navController.popBackStack() })
    }
    composable<AnixDestination.NotificationSettings> {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
}
