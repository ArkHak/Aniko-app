package com.aniko.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aniko.app.feature.auth.LoginScreen
import com.aniko.app.navigation.AnixDestination
import com.aniko.app.navigation.AnixSection
import com.aniko.app.navigation.DeepLinkDispatcher
import com.aniko.app.navigation.DetailPaneStack
import com.aniko.app.navigation.ListDetailHost
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.app.navigation.PendingCatalogFilterLink
import com.aniko.app.navigation.TitleNavigator
import com.aniko.app.navigation.navigateToTabRoot
import com.aniko.app.navigation.parseCatalogFilterLink
import com.aniko.app.navigation.parseDeepLink
import com.aniko.app.navigation.rememberDetailPaneStack
import com.aniko.app.navigation.rememberTitleNavigator
import com.aniko.data.locale.LocaleStore
import com.aniko.data.repository.AuthRepository
import com.aniko.data.repository.NotificationRepository
import com.aniko.data.session.SessionState
import com.aniko.data.sync.SyncCoordinator
import com.aniko.data.theme.ThemeStore
import com.aniko.ui.adaptive.AdaptiveNavItem
import com.aniko.ui.adaptive.AdaptiveScaffold
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.adaptive.rememberAnixWindowSize
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.AnixOfflineBanner
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.ProvideAppStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.image.createAnixImageLoader
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

        // Тема — читается из ThemeStore тем же способом, что и язык выше. null — «не выбран
        // явно»: первый запуск/сброс → светлая тема (макет Home в Claude Design светлый,
        // сверка 2026-09-08 по скриншоту пользователя; системная тема macOS не учитывается).
        // Явный выбор — "light"/"dark" (2026-09-10: AMOLED слит в единственную тёмную тему,
        // см. KDoc `ThemeStore`/`AnixPalette`; legacy "amoled" мигрирует на "dark" прозрачно).
        val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()

        // Единственный авторитет размера окна (P5.T4) — вычисляется один раз на корневом уровне и
        // прокидывается через CompositionLocal, чтобы AdaptiveScaffold/ListDetailHost/TitleNavigator
        // (все ниже по дереву) видели одно и то же значение без повторного вычисления.
        val windowSize = rememberAnixWindowSize()

        AppTheme(darkTheme = themeMode == "dark") {
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
// параметров (localeStore threading, тот же паттерн, что и у SettingsScreen) — группировка
// сторов в отдельный объект ради обхода линта добавила бы косвенность без пользы.
private fun AnixSessionGate(
    authRepository: AuthRepository,
    localeStore: LocaleStore,
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
            is SessionState.Authorized -> AnixAppScaffold(localeStore, themeStore, onBackHandlerReady)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * [AnixSection] → [AdaptiveNavItem] — лейблы из [LocalStrings], иконки — имена Material Symbols
 * Rounded для [com.aniko.ui.component.AnixIcon] (P13/Track B, было material-icons-extended).
 *
 * P13.T1 (сверка с мокапом Claude Design): иконки и лейблы трёх вкладок сменились —
 * `Search`→`grid_view`/«Каталог» (было `search`/«Поиск», сама Kotlin-константа не переименована,
 * см. KDoc [AnixSection]), `Library`→`bookmark`/«Мои списки» (было `video_library`/«Списки»),
 * последняя вкладка теперь `Profile`→`person`/«Профиль» вместо `Settings`→`settings`/«Настройки».
 * `Home`/`Schedule` совпадали с мокапом уже до этой фазы, не менялись.
 */
private fun AnixSection.toNavItem(strings: Strings): AdaptiveNavItem =
    when (this) {
        AnixSection.Home -> AdaptiveNavItem(name, strings.navHome, "home")
        AnixSection.Search -> AdaptiveNavItem(name, strings.navCatalog, "grid_view")
        AnixSection.Library -> AdaptiveNavItem(name, strings.navLibrary, "bookmark")
        AnixSection.Schedule -> AdaptiveNavItem(name, strings.navSchedule, "calendar_month")
        AnixSection.Profile -> AdaptiveNavItem(name, strings.navProfile, "person")
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
    themeStore: ThemeStore,
    onBackHandlerReady: (() -> Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val paneStack = rememberDetailPaneStack()
    // Desktop-проход (2026-09-15, мокап Claude Design, desktop-артборд `showDetail`): на
    // Expanded карточка тайтла открывается правым ящиком 520dp поверх списка (см. KDoc
    // `ListDetailHost`) — pane-механика активна ТОЛЬКО там. Compact/Medium — полноэкранный
    // маршрут, как и раньше (phone/tablet-артборды мокапа, фазы 13–15, не трогаем). Раскладки
    // самих экранов по-прежнему решаются `AnixWindowSize` напрямую (`isTwoPane` там остаётся
    // true на Medium — колонки Schedule/грид Library и т.п.); этот флаг касается только
    // развилки маршрут-vs-панель внутри `AdaptiveTitleNavigator`.
    val panesEnabled = LocalAnixWindowSize.current == AnixWindowSize.Expanded
    val titleNavigator = rememberTitleNavigator(navController, paneStack, isTwoPane = { panesEnabled })

    LaunchedEffect(titleNavigator, onBackHandlerReady) {
        onBackHandlerReady(titleNavigator::back)
    }

    // Миграция открытого тайтла между маршрутом (compact) и панелью (wide) при смене размера окна
    // (P5.T3 — найдено ревью: без этого шага пользователь "терял" бы открытую карточку релиза при
    // изменении размера окна, т.к. NavController и DetailPaneStack — два независимых источника
    // состояния без моста между ними). Реализация — см. [migratePaneRoutes] ниже. С 2026-09-15
    // снова активна — срабатывает при ресайзе окна через границу Expanded (desktop-проход,
    // ящик 520dp включается/выключается на лету).
    LaunchedEffect(panesEnabled) {
        migratePaneRoutes(navController, paneStack, isTwoPane = panesEnabled)
    }

    CollectDeepLinks(navController)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedSection =
        AnixSection.entries.find { section ->
            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(section.destination::class) } == true
        }
    val strings = LocalStrings.current
    val navItems = remember(strings) { AnixSection.entries.map { it.toNavItem(strings) } }

    // Бейдж непрочитанных уведомлений для колокольчика в футере сайдбара (Expanded, 2026-09-18 —
    // кнопки переехали сюда из топбара профиля).
    val unreadNotificationsCount = rememberUnreadNotificationsCount(backStackEntry)

    CompositionLocalProvider(LocalTitleNavigator provides titleNavigator) {
        AdaptiveScaffold(
            items = navItems,
            selectedItemId = selectedSection?.name,
            // Плеер — "поверх" каркаса, см. KDoc `AdaptiveScaffold.showNavigationChrome`.
            showNavigationChrome = backStackEntry?.destination?.hasRoute(AnixDestination.Player::class) != true,
            onItemClick = { item ->
                navController.navigateToTabRoot(AnixSection.valueOf(item.id).destination)
            },
            sidebarFooter = {
                SidebarChromeActions(
                    unreadNotificationsCount = unreadNotificationsCount,
                    onNotificationsClick = { navController.navigate(AnixDestination.Notifications) },
                    onSettingsClick = { navController.navigate(AnixDestination.Settings) },
                )
            },
        ) { innerPadding ->
            AnixAppScaffoldContent(
                innerPadding = innerPadding,
                navController = navController,
                paneStack = paneStack,
                titleNavigator = titleNavigator,
                localeStore = localeStore,
                themeStore = themeStore,
            )
        }
    }
}

/**
 * Deep links (P10.T7): DeepLinkDispatcher.pending — StateFlow, а не одноразовый callback,
 * поэтому эта подписка отрабатывает и ссылку, пришедшую холодным стартом ДО того, как
 * AnixAppScaffold собрался (см. KDoc DeepLinkDispatcher про доставку после логина). Вынесена из
 * [AnixAppScaffold] отдельной функцией — иначе она превышала detekt `LongMethod`.
 */
@Composable
private fun CollectDeepLinks(navController: NavHostController) {
    LaunchedEffect(navController) {
        DeepLinkDispatcher.pending.collect { url ->
            if (url != null) {
                // Порядок: сначала ссылка-набор-фильтров (P16.T2) — у неё свой хост `catalog` и
                // своё назначение (состояние каталога, а не маршрут), затем обычные ссылки.
                val catalogFilter = parseCatalogFilterLink(url)
                if (catalogFilter != null) {
                    PendingCatalogFilterLink.dispatch(catalogFilter)
                    navController.navigateToTabRoot(AnixSection.Search.destination)
                } else {
                    parseDeepLink(url)?.let { destination -> navController.navigate(destination) }
                }
                DeepLinkDispatcher.consume()
            }
        }
    }
}

/**
 * Колокольчик уведомлений (с бейджем непрочитанных) и шестерёнка настроек в футере сайдбара
 * (Expanded). Живой фидбек пользователя (2026-09-18): кнопки переехали сюда из топбара
 * `ProfileScreen` — на десктопе они должны жить в левом нижнем углу окна, а не в шапке профиля.
 * На Compact/Medium сайдбара нет, там те же кнопки по-прежнему рисует топбар профиля
 * (`ProfileTopBarActions`). Стилистика и семантика (`clearAndSetSemantics`, кламп «99+»)
 * намеренно зеркалят её.
 */
@Composable
private fun SidebarChromeActions(
    unreadNotificationsCount: Long,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row {
        IconButton(
            onClick = onNotificationsClick,
            modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription = strings.notificationsIconContentDescription
                },
        ) {
            if (unreadNotificationsCount > 0) {
                BadgedBox(badge = { Badge { Text(unreadCountLabel(unreadNotificationsCount)) } }) {
                    AnixIcon(name = "notifications", contentDescription = null)
                }
            } else {
                AnixIcon(name = "notifications", contentDescription = null)
            }
        }
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.clearAndSetSemantics { contentDescription = strings.settingsTitle },
        ) {
            AnixIcon(name = "settings", contentDescription = null, filled = true)
        }
    }
}

/**
 * Счётчик непрочитанных уведомлений для колокольчика в футере сайдбара. Обновляется на каждую
 * смену назначения — тот же момент, что раньше давал `LaunchedEffect` самого `ProfileScreen`
 * (см. `ProfileViewModel.refreshUnreadNotifications`). Ошибка сети → 0, бейдж просто не рисуется.
 */
@Composable
private fun rememberUnreadNotificationsCount(backStackEntry: NavBackStackEntry?): Long {
    val notificationRepository = koinInject<NotificationRepository>()
    var count by remember { mutableLongStateOf(0L) }
    LaunchedEffect(notificationRepository, backStackEntry) {
        count = runCatching { notificationRepository.unreadBadgeCount() }.getOrDefault(0L)
    }
    return count
}

/** Кламп на «99+» — тот же предел, что в `ProfileScreen.unreadCountLabel`. */
private fun unreadCountLabel(count: Long): String {
    val capped = count > UNREAD_BADGE_MAX
    return if (capped) UNREAD_BADGE_LABEL else count.toString()
}

private const val UNREAD_BADGE_MAX = 99L
private const val UNREAD_BADGE_LABEL = "99+"

/**
 * Тело слота `content` [AdaptiveScaffold] внутри [AnixAppScaffold] — вынесено отдельной функцией
 * (detekt `LongMethod`): считает `contentPadding` без нижней вставки под Liquid Glass bottom bar
 * и рисует [AnixNavGraph]. См. комментарии внутри про Liquid Glass/`consumeWindowInsets` —
 * перенесены без изменения смысла.
 */
@Suppress("LongParameterList") // Тот же координирующий блок, что и AnixAppScaffold — см. её KDoc.
@Composable
private fun AnixAppScaffoldContent(
    innerPadding: PaddingValues,
    navController: NavHostController,
    paneStack: DetailPaneStack,
    titleNavigator: TitleNavigator,
    localeStore: LocaleStore,
    themeStore: ThemeStore,
) {
    // Liquid Glass (2026-09-11, feature/liquid-glass-tab-bar): нижняя часть innerPadding
    // раньше физически обрезала контент НАД таб-баром — под полупрозрачным/блюрящим баром
    // оставалась голая заливка фона `AppTheme`, блюрить было нечего. Теперь вниз идёт
    // ТОЛЬКО top/start/end часть — контент продолжается edge-to-edge ПОД бар (это и есть
    // источник фона для [com.aniko.ui.glass.LiquidGlass], подписанный в `AdaptiveScaffold`
    // через `Modifier.glassBackdropSource`), а высоту бара учитывают сами корневые экраны
    // через `LocalGlassBottomInset` в `contentPadding` своих `LazyColumn`/
    // `LazyVerticalGrid` (Home/Catalog/Library/Schedule/Profile — см. их файлы). Экраны,
    // ещё не переведённые на `LocalGlassBottomInset` (не корневые вкладки таб-бара —
    // Feed/Collections/детали и т.п.), не ломаются: `AnixWindowSize.Medium/Expanded`,
    // `showNavigationChrome == false` и не-таб-роуты внутри Compact просто не получают
    // ничего, кроме нуля из дефолта `LocalGlassBottomInset` — прежнее поведение.
    val contentPadding =
        PaddingValues(
            start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
            bottom = 0.dp,
        )
    AnixNavGraph(
        navController = navController,
        paneStack = paneStack,
        titleNavigator = titleNavigator,
        localeStore = localeStore,
        themeStore = themeStore,
        // `consumeWindowInsets`, не только `padding` — без него `innerPadding` физически
        // сдвигает контент, но не помечает эти insets как уже потреблённые: экраны со
        // своим собственным `Scaffold`/`TopAppBar` внутри графа (Settings, Profile,
        // Comments, Gallery, ReleaseDetails...) заново читают `WindowInsets.safeDrawing` и
        // отступают от статус-бара ВТОРОЙ раз — снаружи уже есть `.padding(innerPadding)`,
        // и поверх него их собственный TopAppBar добавляет то же самое (жалоба живой
        // проверки: "шапка ниже верхней части экрана, как будто лишний отступ"). Тот же
        // приём уже применён точечно у офлайн-баннера чуть выше по файлу (см. её комментарий
        // про `onConsumedWindowInsetsChanged` в `Scaffold`) — здесь тот же механизм, но для
        // всего `AnixNavGraph` целиком. `contentPadding` (не полный `innerPadding`) — см.
        // комментарий выше про Liquid Glass; `consumeWindowInsets` по-прежнему получает
        // ПОЛНЫЙ `innerPadding`, а не урезанный `contentPadding` — низ всё ещё физически
        // относится к системному/бар-инсету и не должен читаться экранами графа второй раз.
        modifier = Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(innerPadding),
    )
}
