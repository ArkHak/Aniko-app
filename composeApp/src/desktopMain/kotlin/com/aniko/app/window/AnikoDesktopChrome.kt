package com.aniko.app.window

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.aniko.player.LocalDesktopWindow
import java.awt.Frame

private val TitleBarHeight = 38.dp

/**
 * Кастомный оконный хром для `undecorated = true` Window (P5.T6): своя draggable-полоса сверху
 * (системный заголовок `undecorated = true` убирает вместе с кнопками).
 *
 * **Кнопки окна здесь больше не рисуются** (desktop-проход 2026-09-15/16). Desktop-артборд мокапа
 * Claude Design кладёт traffic lights ВНУТРЬ сайдбара (`MacTrafficLights` в его шапке, строка 738),
 * и приложение теперь рисует их там же — [com.aniko.app.AppSidebarTrafficLights]. Полоса остаётся
 * только как область перетаскивания: без неё `undecorated`-окно не сдвинуть с места. Живая
 * проверка 2026-09-16 на реальном окне показала ДВА набора кнопок одновременно (полоса + сайдбар) —
 * это и было причиной убрать их отсюда, а не из сайдбара.
 *
 * Двойной клик по полосе разворачивает/восстанавливает окно — стандартное поведение заголовка
 * окна в macOS, которое при `undecorated = true` тоже приходится реализовывать вручную:
 * [WindowDraggableArea] сама по себе двойной клик не обрабатывает, только перетаскивание.
 *
 * **Единый путь управления окном — AWT `Frame`** (2026-09-17, ревью F1): toggle делается через
 * [LocalDesktopWindow] тем же `extendedState xor MAXIMIZED_BOTH`, что и кнопки сайдбара
 * ([com.aniko.app.AppSidebarTrafficLights]) — иначе две «конкурирующие» системы рассинхронизируют
 * друг друга (Compose `WindowState.placement` не узнает о прямом изменении `Frame`).
 *
 * @param content контент приложения под полосой.
 */
@Composable
fun WindowScope.AnikoDesktopChrome(content: @Composable () -> Unit) {
    val window = LocalDesktopWindow.current
    Column(modifier = Modifier.fillMaxSize()) {
        WindowDraggableArea(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TitleBarHeight)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                val frame = window as? Frame
                                if (frame != null) {
                                    frame.extendedState = frame.extendedState xor Frame.MAXIMIZED_BOTH
                                }
                            },
                        )
                    },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(TitleBarHeight))
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}
