package com.aniko.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.di.initKoinOnce
import com.aniko.app.window.AnikoDesktopChrome
import com.aniko.app.window.AnixMenuBar
import com.aniko.data.locale.LocaleStore
import org.koin.compose.koinInject

/**
 * Флаг отката на нативный оконный хром macOS (P5.T6). `true` — своя draggable-полоса +
 * [com.aniko.app.window.TrafficLightButtons] вместо системных traffic lights (`undecorated =
 * true`). Если на реальном железе всплывут проблемы (например, конфликт `undecorated` с
 * `apple.laf.useScreenMenuBar` — при проверке в этой среде не воспроизвёлся, см. отчёт по
 * задаче), интегратор может откатиться одной строкой: `USE_CUSTOM_CHROME = false`.
 */
private const val USE_CUSTOM_CHROME = true

fun main() {
    // Должно быть выставлено ДО старта Compose/AWT — иначе Swing JMenuBar рисуется как часть
    // окна, а не в системной менюбаре сверху экрана macOS.
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    initKoinOnce()

    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 860.dp))
        Window(
            onCloseRequest = ::exitApplication,
            title = "Aniko",
            state = windowState,
            undecorated = USE_CUSTOM_CHROME,
        ) {
            // App() вызывается сиблингом ниже, а не предком AnixMenuBar — LocalTitleNavigator
            // недоступен здесь напрямую через CompositionLocal, поэтому App() сам передаёт сюда
            // актуальную TitleNavigator.back() через onBackHandlerReady (см. KDoc App() в App.kt).
            var backHandler by remember { mutableStateOf<() -> Boolean>({ false }) }

            // LocaleStore — тот же синглтон, что читает/пишет App()/SettingsScreen; читаем его
            // здесь напрямую (не через App(), по той же причине, что и backHandler выше), чтобы
            // пункт меню "View → Language" отражал и менял тот же язык, что и остальной UI.
            val localeStore = koinInject<LocaleStore>()
            val languageTag by localeStore.languageTag.collectAsStateWithLifecycle()

            AnixMenuBar(
                onExit = ::exitApplication,
                onBack = { backHandler() },
                currentLanguageTag = languageTag,
                onLanguageTagChange = localeStore::setLanguageTag,
            )
            if (USE_CUSTOM_CHROME) {
                AnikoDesktopChrome(
                    windowState = windowState,
                    onClose = ::exitApplication,
                    onMinimize = { windowState.isMinimized = true },
                ) {
                    App(onBackHandlerReady = { backHandler = it })
                }
            } else {
                App(onBackHandlerReady = { backHandler = it })
            }
        }
    }
}
