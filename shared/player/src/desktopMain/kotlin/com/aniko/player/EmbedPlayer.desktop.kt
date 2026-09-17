package com.aniko.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent

/**
 * Desktop: VLCJ (`uk.co.caprica:vlcj`, libVLC-биндинг) вместо встроенного Chromium как РЕНДЕРЕРА
 * (Step 2/3 пересмотра P8.T1, см. журнал `docs/REELWAVE_PLAN.md`) — JCEF не тянет кодеки H.264/AAC
 * (`me.friwi:jcefmaven`, подтверждено `canPlayType()` внутри реальной embed-страницы), libVLC тянет
 * оба. [DesktopStreamResolver] резолвит реальный URL потока за embed-страницей чистым HTTP (Step 3
 * пересмотра, `feature/desktop-video-player`, см. её KDoc) — JCEF/headless-браузер для этого
 * больше не поднимается вовсе, поэтому этот composable запускает [DesktopVlcjPlayer] сразу, не
 * дожидаясь готовности никакого браузерного движка.
 *
 * **Тестовый режим (`aniko.playerTestMode`).** Смоук-тесты `:composeApp:desktopTest` доводят
 * композицию до `PlayerScreen` (P11.T2, `BrowseDetailPlaySmokeTest`). Настоящий
 * `CallbackMediaPlayerComponent` там не нужен — тест проверяет навигацию, а не видео. Хуже того,
 * на CI-раннере (ubuntu, без libVLC) `NativeDiscovery` vlcj уходит в бесконечный обход дерева
 * каталогов прямо на EDT: тест формально зелёный, но его поток крутится на 100% CPU вечно,
 * тестовая JVM не завершается, и CI-джоб висел до системного 6-часового таймаута (прогоны
 * 2026-09-15/17, диагностировано jstack-дампом). Поэтому под системным свойством
 * `aniko.playerTestMode=true` (ставится Test-таскам в `composeApp/build.gradle.kts`) рисуется
 * только placeholder — без нативного плеера и без сетевого резолва [DesktopStreamResolver].
 *
 * **Топология трёх слоёв** (см. отчёт задачи за разбором альтернатив, которые не сработали —
 * `CallbackMediaPlayerComponent` внутри `SwingPanel` с Compose-соседями, `EmbeddedMediaPlayerComponent`
 * — обе на этой ветке подтверждённые вживую тупики):
 * 1. Главное окно приложения (`Main.kt`) — здесь этот composable рисует только чёрный placeholder
 *    (сам видео-кадр сюда не попадает никак, ни как heavyweight-компонент, ни как что-либо ещё).
 * 2. Видео-окно — ОТДЕЛЬНЫЙ top-level `Window` (не `SwingPanel` внутри главного — см. следующий
 *    абзац), хостит ровно один Swing-компонент — [CallbackMediaPlayerComponent] — без единого
 *    Compose-соседа в том же дереве.
 * 3. Оверлей-окно — ещё один top-level `Window`, ТРАНСПАРЕНТНЫЙ, поверх видео-окна, заведён
 *    `composeApp` (`PlayerOverlayHost` desktop actual, `:shared:player` не видит `PlayerOverlay`/
 *    `CompactPlayerChrome` — граница модулей, `composeApp` зависит от `:shared:player`, не наоборот).
 *    Оба вторых окна синхронизируют границы через один и тот же [trackScreenBounds].
 *
 * **Почему видео — отдельный `Window`, а не `SwingPanel` в этом же дереве, как раньше JCEF.**
 * Живые спайки этой ветки (`661b1a2`, `f0efa47`) подтвердили: `CallbackMediaPlayerComponent`
 * активно перерисовывает себя поверх ЛЮБОГО Compose/Swing-соседа в ОДНОМ дереве компонентов —
 * `compose.interop.blending=true`, которое чинило это же для JCEF, для VLCJ не работает вообще
 * (проверено вживую с реальным frontmost-кликом). Отдельное top-level окно обходит эту проблему
 * ПОЛНОСТЬЮ, а не боком: у видео-окна нет ни одного соседа, конкурировать за z-order не с кем.
 * Это тот же принцип, что у штатного класса vlcj `AbstractJWindowOverlayComponent`
 * (см. `VlcjJWindowOverlaySpike.kt`, удалён этой веткой как исчерпавший роль чекпойнта) — тут просто
 * используется готовый Compose Desktop примитив (`Window(...)` composable — легитимно вызываемый из
 * любой точки композиции, не только из `application {}`) вместо ручного AWT/`JWindow`.
 */
@Composable
actual fun EmbedPlayerView(
    url: String,
    referer: String?,
    controller: EmbedVideoController?,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!isPlayerTestMode()) {
            DesktopVlcjPlayer(url = url, referer = referer, controller = controller, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * `true` под системным свойством `aniko.playerTestMode=true` — см. KDoc [EmbedPlayerView].
 * Читается на каждую композицию намеренно: свойство задаётся Test-таском до старта JVM, но
 * чтение на месте не зависит от порядка загрузки классов и не требует remember.
 */
private fun isPlayerTestMode(): Boolean = System.getProperty("aniko.playerTestMode") == "true"

/**
 * Владеет [CallbackMediaPlayerComponent] на время жизни композиции этого узла — переживает смену
 * [url]/[referer] (переключение аудиодорожки не должно пересоздавать плеер/видео-окно), тот же
 * принцип, что раньше был у `DesktopEmbedSession` с `CefBrowser`.
 *
 * Placeholder в основном дереве composition — ТОЛЬКО ради [trackScreenBounds] (главное окно должно
 * знать, где реально сейчас видео-область, чтобы синхронизировать видео-окно и, через
 * `composeApp`, оверлей-окно) — сам он ничего не рисует поверх фона.
 */
@Composable
private fun DesktopVlcjPlayer(
    url: String,
    referer: String?,
    controller: EmbedVideoController?,
    modifier: Modifier,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Box(modifier = modifier.trackScreenBounds { bounds = it })

    val mediaPlayerComponent = remember { CallbackMediaPlayerComponent() }
    DisposableEffect(mediaPlayerComponent) {
        controller?.attach(mediaPlayerComponent.mediaPlayer())
        onDispose {
            controller?.detach()
            runCatching { mediaPlayerComponent.mediaPlayer().controls().stop() }
            runCatching { mediaPlayerComponent.release() }
        }
    }

    // Резолв — один раз на КАЖДУЮ смену url/referer (аудиодорожка/эпизод), не при каждой
    // рекомпозиции: `LaunchedEffect(url, referer)` перезапускается только когда реально сменился
    // адрес — тот же принцип, что раньше был у `LaunchedEffect(url, referer)` в JCEF-версии.
    LaunchedEffect(url, referer) {
        controller?.setExpectedSource(url)
        val resolved = DesktopStreamResolver.resolve(url, referer)
        if (resolved != null) {
            // Качества и referer для будущего переключения качества — контроллеру (см. KDoc
            // [EmbedVideoController.onStreamsResolved]): чип качества на Desktop заполняется из
            // controller-списка, а setQuality перезапускает поток с этим же referer.
            controller?.onStreamsResolved(resolved)
            // `:http-referrer=` — media-опция libVLC (модуль access/http) для ФИНАЛЬНОГО запроса
            // потока самим libVLC. `resolved.referer`, а НЕ внешний параметр `referer` этой
            // функции — они могут не совпадать (Kodik: партнёрский Referer нужен только чтобы
            // получить страницу, а CDN-хост потока ждёт свой собственный, см. KDoc
            // [DesktopStreamResolver.Resolved.referer] за разбором). Явное ветвление вместо
            // vararg-спреда из пустого/одноэлементного массива — так проще читается, чем собирать
            // массив ради одного опционального аргумента (заодно не ловит detekt `SpreadOperator`).
            val media = mediaPlayerComponent.mediaPlayer().media()
            val streamReferer = resolved.referer
            if (streamReferer != null) {
                media.play(resolved.streamUrl, ":http-referrer=$streamReferer")
            } else {
                media.play(resolved.streamUrl)
            }
        }
        // resolved == null — резолв не нашёл поток (мёртвая ссылка/неподдерживаемый хост/таймаут
        // сети): остаёмся с isVideoFound=false, PlayerOverlay честно деградирует до одной кнопки
        // "назад" (см. её KDoc про bridgeActive) вместо притворства, что видео есть.
    }

    val currentBounds = bounds
    if (currentBounds != null) {
        val density = LocalDensity.current
        // `remember { ... }` БЕЗ ключа, инициализированный УЖЕ верным размером/позицией из первого
        // известного `currentBounds` — та же живая находка, что и в `PlayerOverlayHost.desktop.kt`
        // (см. её KDoc за полным разбором): `remember { WindowState() }` (дефолт 800×600) +
        // последующая мутация в `LaunchedEffect` создавали вырожденный первый кадр AWT-окна, Skia-
        // поверхность которого затем никогда не переинициализировалась под реальный размер — окно
        // оставалось на экране, но не презентовало ни одного валидного кадра (сплошной чёрный,
        // подтверждено даже с хардкодным непрозрачным фоном вместо контента). Конструирование
        // `WindowState` сразу с верными значениями убирает сам вырожденный первый кадр.
        val windowState =
            remember {
                with(density) {
                    WindowState(
                        position = WindowPosition(currentBounds.left.toDp(), currentBounds.top.toDp()),
                        size = DpSize(currentBounds.width.toDp(), currentBounds.height.toDp()),
                    )
                }
            }
        // Последующие изменения границ (анимация `videoHeight` compact↔fullscreen, P13) — окно уже
        // существует, `windowState.size`/`.position` мутируются как обычно.
        LaunchedEffect(currentBounds) {
            with(density) {
                windowState.position = WindowPosition(currentBounds.left.toDp(), currentBounds.top.toDp())
                windowState.size = DpSize(currentBounds.width.toDp(), currentBounds.height.toDp())
            }
        }
        Window(
            onCloseRequest = {},
            state = windowState,
            undecorated = true,
            resizable = false,
            // Видео не должно перехватывать клавиатурный фокус у главного окна — иначе
            // `PlayerKeyboardShortcuts.desktop.kt` (слушает на главном окне) перестал бы получать
            // space/←→/↑↓ после того, как это окно однажды стало бы активным.
            focusable = false,
            alwaysOnTop = true,
        ) {
            SwingPanel(modifier = Modifier.fillMaxSize(), factory = { mediaPlayerComponent })
        }
    }
}
