package com.anixkmp.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.anixkmp.app.feature.home.HomeScreen
import com.anixkmp.app.feature.library.LibraryScreen
import com.anixkmp.app.feature.settings.SettingsScreen
import com.anixkmp.app.navigation.AnixDestination
import com.anixkmp.ui.image.createAnixImageLoader
import com.anixkmp.ui.theme.AnixTheme
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
        val platformContext = LocalPlatformContext.current

        // Coil ходит в сеть тем же Ktor-клиентом, что и API.
        remember(httpClient, platformContext) {
            SingletonImageLoader.setSafe { context ->
                createAnixImageLoader(context, httpClient)
            }
        }

        AnixTheme {
            AnixAppScaffold()
        }
    }
}

private val bottomTabs = listOf(
    BottomTab("Главная", AnixDestination.Home),
    BottomTab("Списки", AnixDestination.Library),
    BottomTab("Настройки", AnixDestination.Settings),
)

private data class BottomTab(val title: String, val destination: AnixDestination)

@Composable
private fun AnixAppScaffold() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }

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
            composable<AnixDestination.Home> { HomeScreen() }
            composable<AnixDestination.Library> { LibraryScreen() }
            composable<AnixDestination.Settings> { SettingsScreen() }
        }
    }
}
