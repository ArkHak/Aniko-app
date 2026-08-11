package com.aniko.app.window

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState

private val TitleBarHeight = 38.dp

/** Переключает между `Maximized` и `Floating` — общая логика для двойного клика и zoom-кнопки. */
private fun WindowState.toggleMaximized() {
    placement = if (placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
}

/**
 * Кастомный оконный хром для `undecorated = true` Window (P5.T6): своя draggable-полоса сверху
 * с [TrafficLightButtons] вместо нативных macOS-кнопок (которые `undecorated = true` убирает
 * вместе с системным заголовком).
 *
 * Двойной клик по полосе разворачивает/восстанавливает окно — стандартное поведение заголовка
 * окна в macOS, которое при `undecorated = true` тоже приходится реализовывать вручную:
 * [WindowDraggableArea] сама по себе двойной клик не обрабатывает, только перетаскивание.
 *
 * @param windowState состояние окна из `rememberWindowState()` в `Main.kt` — читаем/пишем
 *   [WindowState.placement] напрямую для toggle maximize/restore.
 */
@Composable
fun WindowScope.AnikoDesktopChrome(
    windowState: WindowState,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WindowDraggableArea(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TitleBarHeight)
                    .pointerInput(windowState) {
                        detectTapGestures(onDoubleTap = { windowState.toggleMaximized() })
                    },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(TitleBarHeight)) {
                TrafficLightButtons(
                    onClose = onClose,
                    onMinimize = onMinimize,
                    onToggleMaximize = { windowState.toggleMaximized() },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}
