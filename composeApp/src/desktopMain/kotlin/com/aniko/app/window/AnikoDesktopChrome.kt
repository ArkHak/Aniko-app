package com.aniko.app.window

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.aniko.player.LocalDesktopWindow
import java.awt.Frame
import java.awt.event.WindowEvent

private val TitleBarHeight = 38.dp

/** Левый отступ первой кнопки от края окна — как у нативных macOS-окон. */
private val TrafficLightsStartPadding = 12.dp

/**
 * Кастомный оконный хром для `undecorated = true` Window (P5.T6): своя draggable-полоса сверху
 * (системный заголовок `undecorated = true` убирает вместе с кнопками).
 *
 * Полоса совмещает три функции:
 * 1. **Traffic lights (close/minimize/zoom) в левом верхнем углу** — стандартное расположение
 *    кнопок управления окном в macOS. До 2026-09-17 они рисовались в шапке сайдбара
 *    (по мокапу Claude Design), но вживую это выглядело чужеродно: кнопки «плавали» ниже
 *    верхнего края окна, а сама полоса была пустой. Теперь кнопки живут здесь, а шапка
 *    сайдбара (`sidebarHeader` слот) пустует.
 * 2. **Перетаскивание окна** — без `WindowDraggableArea` `undecorated`-окно не сдвинуть.
 *    Кнопки внутри полосы перетаскиванию не мешают: их `clickable` поглощает события нажатия.
 * 3. **Двойной клик разворачивает/восстанавливает окно** — стандартное поведение заголовка
 *    окна в macOS, которое при `undecorated = true` приходится реализовывать вручную:
 *    [WindowDraggableArea] сама по себе двойной клик не обрабатывает, только перетаскивание.
 *
 * **Единый путь управления окном — AWT `Frame`** (2026-09-17, ревью F1): и кнопки, и двойной
 * клик работают через [LocalDesktopWindow]: close — синтетический `WINDOW_CLOSING` (попадает
 * в `onCloseRequest` окна, как и системная кнопка), minimize/zoom — `extendedState`
 * (`ComposeWindow` — наследник `JFrame`). Compose `WindowState.placement` не узнает о прямом
 * изменении `Frame`, поэтому второй «конкурирующей» системы через `WindowState` быть не должно.
 *
 * @param content контент приложения под полосой.
 */
@Composable
fun WindowScope.AnikoDesktopChrome(content: @Composable () -> Unit) {
    val window = LocalDesktopWindow.current
    val frame = window as? Frame
    Column(modifier = Modifier.fillMaxSize()) {
        WindowDraggableArea(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TitleBarHeight)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (frame != null) {
                                    frame.extendedState = frame.extendedState xor Frame.MAXIMIZED_BOTH
                                }
                            },
                        )
                    },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(TitleBarHeight)) {
                TrafficLightButtons(
                    onClose = { window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
                    onMinimize = { frame?.extendedState = Frame.ICONIFIED },
                    onToggleMaximize = {
                        if (frame != null) {
                            frame.extendedState = frame.extendedState xor Frame.MAXIMIZED_BOTH
                        }
                    },
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = TrafficLightsStartPadding),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}
