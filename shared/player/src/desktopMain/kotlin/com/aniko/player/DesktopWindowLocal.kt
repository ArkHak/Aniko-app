package com.aniko.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * `java.awt.Window` хоста текущей композиции (Step 2/3, `feature/desktop-video-player`).
 *
 * Нужен ровно для одной вещи — [trackScreenBounds]: `LayoutCoordinates.positionOnScreen()`
 * (публичный API `androidx.compose.ui.layout`) даёт абсолютные экранные координаты узла в
 * МОМЕНТ вызова, но не сообщает, когда их надо пересчитать заново из-за того, что пользователь
 * подвинул/зарезайзил САМО окно — `onGloballyPositioned` реагирует только на изменения layout
 * внутри дерева композиции, не на перемещение хост-окна на экране (внутренний layout при этом
 * не меняется вовсе). Официальный `androidx.compose.ui.window.LocalWindow` решал бы это же, но
 * объявлен `internal` в модуле `compose-ui` — недоступен отсюда; этот файл — наш паблик-аналог,
 * заводимый явно в `Main.kt` (`CompositionLocalProvider(LocalDesktopWindow provides window)`
 * внутри `Window { ... }`, где `window: ComposeWindow` даёт `FrameWindowScope`).
 *
 * `null` по умолчанию — композиции без явного провайдера (тесты, превью) получают выключенный
 * трекинг, а не краш.
 */
val LocalDesktopWindow = compositionLocalOf<Window?> { null }

/**
 * Абсолютные экранные баунды узла, на который навешен модификатор — переиспользуется и видео-окном
 * VLCJ ([EmbedPlayerView] desktop), и оверлей-окном `composeApp` (`PlayerOverlayHost` desktop actual):
 * оба вторых top-level `Window` обязаны отслеживать положение того же самого места в главном окне
 * (см. KDoc обоих мест использования).
 *
 * Пересчитывается по ДВУМ независимым триггерам — этим двум место в проекте и понадобилось, а не
 * одному только `onGloballyPositioned`, которого хватает Android/iOS, но не Desktop с отдельным
 * top-level окном (см. KDoc [LocalDesktopWindow]):
 * - `onGloballyPositioned` — смена размера/позиции УЗЛА внутри дерева композиции (анимация
 *   `videoHeight` compact↔fullscreen, P13);
 * - `ComponentListener` на [LocalDesktopWindow] — пользователь подвинул/зарезайзил само окно
 *   приложения, при этом внутренний layout не поменялся вовсе.
 *
 * `onBoundsChanged(null)`, если хост-окно ещё не известно ([LocalDesktopWindow] `null`) или узел
 * уже вышел из композиции ([LayoutCoordinates.isAttached] `false`) — оба места, потребляющие
 * бaунды, обязаны в этом случае прятать свои top-level окна, а не показывать их с устаревшими
 * координатами.
 */
@Composable
fun Modifier.trackScreenBounds(onBoundsChanged: (Rect?) -> Unit): Modifier {
    val hostWindow = LocalDesktopWindow.current
    val tracker = remember { BoundsTracker() }

    DisposableEffect(hostWindow) {
        val window = hostWindow
        if (window == null) {
            onDispose {}
        } else {
            val listener =
                object : ComponentAdapter() {
                    override fun componentMoved(e: ComponentEvent) = tracker.publish(onBoundsChanged)

                    override fun componentResized(e: ComponentEvent) = tracker.publish(onBoundsChanged)
                }
            window.addComponentListener(listener)
            onDispose { window.removeComponentListener(listener) }
        }
    }

    return onGloballyPositioned { coordinates ->
        tracker.coordinates = coordinates
        tracker.publish(onBoundsChanged)
    }
}

/** Хранит последние известные [LayoutCoordinates] между вызовами `onGloballyPositioned` и
 *  `ComponentListener` — см. KDoc [trackScreenBounds]. Обычный `remember`, не `mutableStateOf`:
 *  публикация идёт императивным колбэком, а не через рекомпозицию, лишний snapshot-стейт не нужен. */
private class BoundsTracker {
    var coordinates: LayoutCoordinates? = null

    fun publish(onBoundsChanged: (Rect?) -> Unit) {
        val c = coordinates
        onBoundsChanged(
            if (c != null && c.isAttached) Rect(c.positionOnScreen(), c.size.toSize()) else null,
        )
    }
}
