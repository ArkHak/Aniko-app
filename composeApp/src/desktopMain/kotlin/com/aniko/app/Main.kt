package com.aniko.app

import androidx.compose.runtime.CompositionLocalProvider
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
import com.aniko.app.navigation.DeepLinkDispatcher
import com.aniko.app.window.AnikoDesktopChrome
import com.aniko.app.window.AnixMenuBar
import com.aniko.data.locale.LocaleStore
import com.aniko.data.theme.ThemeStore
import com.aniko.player.LocalDesktopWindow
import org.koin.compose.koinInject

/**
 * Флаг отката на нативный оконный хром macOS (P5.T6). `true` — своя draggable-полоса +
 * [com.aniko.app.window.TrafficLightButtons] вместо системных traffic lights (`undecorated =
 * true`). Если на реальном железе всплывут проблемы (например, конфликт `undecorated` с
 * `apple.laf.useScreenMenuBar` — при проверке в этой среде не воспроизвёлся, см. отчёт по
 * задаче), интегратор может откатиться одной строкой: `USE_CUSTOM_CHROME = false`.
 */
private const val USE_CUSTOM_CHROME = true

/**
 * Deep links на Desktop (P10.T7): у Compose Desktop/JVM нет системной концепции "открыть уже
 * запущенное приложение по ссылке из ОС" без дополнительной интеграции — полноценная регистрация
 * custom protocol handler означала бы отдельные, платформенно ненадёжные шаги для каждой ОС
 * (`.desktop`-файл с `MimeType=x-scheme-handler/aniko;` + `xdg-mime` на Linux, ключи реестра
 * `HKEY_CLASSES_ROOT` на Windows, `CFBundleURLTypes` в `Info.plist` пакета на macOS — при том, что
 * этот проект и так собирает Desktop только под macOS, см. `compose.desktop.nativeDistributions`
 * в `composeApp/build.gradle.kts`) — вне разумного объёма этой задачи.
 *
 * Прагматичное решение для v1: [args] — если первый аргумент похож на deep link URL, он
 * разбирается тем же `parseDeepLink` (`DeepLink.kt`), что и Android/iOS, сразу при старте JVM —
 * покрывает сценарий "что-то извне вызвало jar/app-бандл с URL аргументом" (например, обёртка-
 * скрипт после ручной регистрации протокола средствами ОС). Ссылки, пришедшие УЖЕ запущенному
 * процессу (второй `open aniko://...` при живом приложении), этим не покрываются — у JVM нет
 * единого кросс-платформенного API для приёма такого события без второй инстанции процесса.
 */
fun main(args: Array<String>) {
    runAnikoApp(args)
}

private fun runAnikoApp(args: Array<String>) {
    // УБРАНО (Step 2/3 пересмотра P8.T1, живая проверка feature/desktop-video-player, см. журнал
    // `docs/REELWAVE_PLAN.md`): `compose.interop.blending=true` исторически чинил z-order JCEF-
    // `SwingPanel` под Compose-оверлеем (баг JetBrains CMP-6001) — актуально было только пока JCEF
    // сам РЕНДЕРИЛ видео. После Step 2/3 (`EmbedPlayer.desktop.kt`) единственный оставшийся
    // `SwingPanel` (VLCJ `CallbackMediaPlayerComponent`) живёт в СОБСТВЕННОМ top-level `Window` без
    // единого Compose-соседа — компоузить там нечего, флаг для него больше не нужен.
    // ПРОВЕРЕНО живьём (тот же эпизод, повторный прогон после удаления флага): чёрный экран
    // видео-/оверлей-окон НЕ исчез — это НЕ было причиной черноты (гипотеза отклонена, не
    // подтверждена). Оставлено удалённым как честная уборка мёртвой настройки (единственный
    // адресат флага — SwingPanel в общем дереве с Compose — для него не осталось ни одного
    // случая с Step 2/3), а не как претензия на фикс самой черноты. Настоящая причина и фикс — см.
    // KDoc [PlayerOverlayHost.desktop.kt] (desktop-actual, `composeApp`).
    //
    // Step 3 пересмотра (`feature/desktop-video-player`, см. `DesktopStreamResolver.kt` в
    // `:shared:player`): headless-JCEF резолвер, который раньше запускался здесь на старте
    // приложения (`DesktopWebEngine.initialize()`/`.dispose()`), заменён на чистый HTTP и удалён
    // целиком — на Desktop с этого момента браузерный движок не поднимается вообще ни для чего.

    // Должно быть выставлено ДО старта Compose/AWT — иначе Swing JMenuBar рисуется как часть
    // окна, а не в системной менюбаре сверху экрана macOS.
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    initKoinOnce()

    args.firstOrNull()?.let(DeepLinkDispatcher::dispatch)

    application {
        val windowState = rememberWindowState(size = DpSize(1080.dp, 720.dp))
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

            // Тема хрома (P5.T6 редизайн 2026-09-17): та же логика выбора, что и в App()/
            // AppTheme (null/не "dark" → светлая, системная тема macOS не учитывается, см. KDoc
            // AppTheme) — читается тут же напрямую, отдельно от App(), по той же причине, что и
            // localeStore/backHandler выше (AnikoDesktopChrome рисуется СНАРУЖИ App(), не видит
            // его CompositionLocal). См. AnikoDesktopChrome.kt/TrafficLightButtons.kt.
            val themeStore = koinInject<ThemeStore>()
            val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode == "dark"

            AnixMenuBar(
                onExit = ::exitApplication,
                onBack = { backHandler() },
                currentLanguageTag = languageTag,
                onLanguageTagChange = localeStore::setLanguageTag,
            )
            // Step 2/3 (P8.T1 пересмотр, `feature/desktop-video-player`): видео-/оверлей-окна
            // VLCJ-плеера (`EmbedPlayer.desktop.kt`/`PlayerOverlayHost.desktop.kt`) — отдельные
            // top-level `Window`, синхронизирующие свои границы с местом видео-области в ЭТОМ
            // окне через `trackScreenBounds` (`:shared:player`, `DesktopWindowLocal.kt`) — ему
            // нужна ссылка на само это окно (`ComponentListener` на перемещение/ресайз, см. её
            // KDoc), которую `androidx.compose.ui.window.LocalWindow` не отдаёт (`internal` в
            // модуле `compose-ui`, недоступен отсюда) — заводим свой публичный аналог.
            CompositionLocalProvider(LocalDesktopWindow provides window) {
                if (USE_CUSTOM_CHROME) {
                    AnikoDesktopChrome(darkTheme = darkTheme) {
                        App(onBackHandlerReady = { backHandler = it })
                    }
                } else {
                    App(onBackHandlerReady = { backHandler = it })
                }
            }
        }
    }
}
