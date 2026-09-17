package com.aniko.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.app.window.TrafficLightButtons
import com.aniko.player.LocalDesktopWindow
import java.awt.Frame
import java.awt.event.WindowEvent

/**
 * Desktop-actual [AppSidebarTrafficLights]: те же [TrafficLightButtons], что рисует
 * draggable-полоса `AnikoDesktopChrome` (P5.T6), но действия берутся из AWT-окна напрямую
 * через [LocalDesktopWindow] (`:shared:player`, провайдится в `Main.kt` внутри `Window { }`) —
 * `WindowState` сюда не достать без сквозной прокидки из `Main.kt`, а всё необходимое окно
 * умеет само: close — синтетический `WINDOW_CLOSING` (попадает в `onCloseRequest` окна,
 * как и системная кнопка), minimize/maximize — `Frame.extendedState` (`ComposeWindow` —
 * наследник `JFrame`).
 *
 * Кнопки рисуются ВСЕГДА, даже когда [LocalDesktopWindow] ещё/уже `null` (композиции без
 * реального окна — офскрин-дампы `DesktopVisualDump`, превью): колбэки в этом случае просто
 * no-op, а визуал остаётся — иначе диагностические кадры сайдбара теряли бы traffic lights.
 */
@Composable
actual fun AppSidebarTrafficLights(modifier: Modifier) {
    val window = LocalDesktopWindow.current
    val frame = window as? Frame
    TrafficLightButtons(
        onClose = { window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
        onMinimize = { frame?.extendedState = Frame.ICONIFIED },
        onToggleMaximize = {
            if (frame != null) {
                frame.extendedState = frame.extendedState xor Frame.MAXIMIZED_BOTH
            }
        },
        modifier = modifier,
    )
}
