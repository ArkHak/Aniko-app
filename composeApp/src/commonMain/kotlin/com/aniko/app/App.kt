package com.aniko.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aniko.app.feature.auth.LoginScreen
import com.aniko.app.feature.gallery.TokenGalleryScreen
import com.aniko.app.feature.home.HomeScreen
import com.aniko.app.feature.library.LibraryScreen
import com.aniko.app.feature.player.PlayerScreen
import com.aniko.app.feature.profile.ProfileScreen
import com.aniko.app.feature.release.ReleaseDetailsScreen
import com.aniko.app.feature.search.SearchScreen
import com.aniko.app.feature.settings.SettingsScreen
import com.aniko.app.navigation.AnixDestination
import com.aniko.data.locale.LocaleStore
import com.aniko.data.repository.AuthRepository
import com.aniko.data.session.SessionState
import com.aniko.data.sync.SyncQueueWorker
import com.aniko.model.VideoHost
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.ProvideAppStrings
import com.aniko.ui.image.createAnixImageLoader
import com.aniko.ui.theme.AppTheme
import io.ktor.client.HttpClient
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

/**
 * Корневой Composable приложения. Одинаков для Android, Desktop и iOS —
 * платформы отличаются только точкой входа (Activity / main() / MainViewController).
 */
@Composable
fun App() {
    KoinContext {
        val httpClient = koinInject<HttpClient>()
        val authRepository = koinInject<AuthRepository>()
        val localeStore = koinInject<LocaleStore>()
        val syncQueueWorker = koinInject<SyncQueueWorker>()
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

        // Разовый прогон офлайн-очереди на старте — не потерять то, что скопилось за время
        // оффлайна между запусками (P4.T7). Полноценный фоновый воркер с реакцией на
        // восстановление сети — Фаза 10, здесь только этот единичный дренаж.
        LaunchedEffect(syncQueueWorker) {
            syncQueueWorker.drain()
        }

        // Язык — читается из LocaleStore (P2.T11) и прокидывается в ProvideAppStrings (P2.T7/T8),
        // а не наоборот: shared/ui ничего не знает про DI/Settings, только про Compose-механику
        // Lyricist (см. KDoc ProvideAppStrings). null — «следовать системной локали».
        val languageTag by localeStore.languageTag.collectAsStateWithLifecycle()

        AppTheme {
            ProvideAppStrings(languageTag = languageTag) {
                AnixSessionGate(authRepository)
            }
        }
    }
}

/**
 * Реактивный гейт по [AuthRepository.sessionState]:
 * - [SessionState.Loading] — сплэш-лоадер, пока не прочитан токен;
 * - [SessionState.Unauthorized] — экран входа;
 * - [SessionState.Authorized] — основной граф из трёх табов.
 *
 * Также слушает [AuthRepository.sessionExpired] (401/403 от бэкенда) и показывает
 * одноразовый снекбар — сама навигация на логин при этом переключается через sessionState.
 */
@Composable
private fun AnixSessionGate(authRepository: AuthRepository) {
    val sessionState by authRepository.sessionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val sessionExpiredMessage = LocalStrings.current.sessionExpiredMessage
    LaunchedEffect(authRepository, sessionExpiredMessage) {
        authRepository.sessionExpired.collect {
            snackbarHostState.showSnackbar(sessionExpiredMessage)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (sessionState) {
            SessionState.Loading -> AnixLoadingBox()
            SessionState.Unauthorized -> LoginScreen()
            is SessionState.Authorized -> AnixAppScaffold()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun bottomTabs(): List<BottomTab> {
    val strings = LocalStrings.current
    return listOf(
        BottomTab(strings.navHome, AnixDestination.Home),
        BottomTab(strings.navSearch, AnixDestination.Search),
        BottomTab(strings.navLibrary, AnixDestination.Library),
        BottomTab(strings.navSettings, AnixDestination.Settings),
    )
}

private data class BottomTab(
    val title: String,
    val destination: AnixDestination,
)

@Composable
private fun AnixAppScaffold() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }
    val bottomTabs = bottomTabs()

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            navController.navigate(tab.destination) {
                                popUpTo(AnixDestination.Home) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Text(tab.title.take(1)) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AnixDestination.Home,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable<AnixDestination.Home> {
                HomeScreen(onReleaseClick = navController::navigateToRelease)
            }
            composable<AnixDestination.Search> {
                SearchScreen(onReleaseClick = navController::navigateToRelease)
            }
            composable<AnixDestination.Library> {
                LibraryScreen(onReleaseClick = navController::navigateToRelease)
            }
            composable<AnixDestination.Settings> {
                SettingsScreen(
                    onProfileClick = { navController.navigate(AnixDestination.Profile) },
                    onDesignGalleryClick = { navController.navigate(AnixDestination.TokenGallery) },
                )
            }
            composable<AnixDestination.Profile> {
                ProfileScreen(onBack = { navController.popBackStack() })
            }
            composable<AnixDestination.TokenGallery> {
                TokenGalleryScreen(onBack = { navController.popBackStack() })
            }
            composable<AnixDestination.ReleaseDetails> { backStackEntry ->
                val route: AnixDestination.ReleaseDetails = backStackEntry.toRoute()
                ReleaseDetailsScreen(
                    releaseId = route.releaseId,
                    onEpisodeClick = navController::navigateToPlayer,
                )
            }
            composable<AnixDestination.Player> { backStackEntry ->
                val route: AnixDestination.Player = backStackEntry.toRoute()
                PlayerScreen(
                    releaseId = route.releaseId,
                    sourceId = route.sourceId,
                    position = route.position,
                    hostKey = route.hostKey,
                )
            }
        }
    }
}

private fun NavController.navigateToRelease(releaseId: Int) {
    if (releaseId <= 0) return
    navigate(AnixDestination.ReleaseDetails(releaseId))
}

private fun NavController.navigateToPlayer(
    releaseId: Int,
    sourceId: Int,
    position: Int,
    host: VideoHost,
) {
    navigate(
        AnixDestination.Player(releaseId = releaseId, sourceId = sourceId, position = position, hostKey = host.key),
    )
}
