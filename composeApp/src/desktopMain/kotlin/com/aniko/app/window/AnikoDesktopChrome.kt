package com.aniko.app.window

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.aniko.player.LocalDesktopWindow
import com.aniko.ui.theme.LocalAnixColors
import com.aniko.ui.theme.anixColorScheme
import com.aniko.ui.theme.anixExtraColors
import java.awt.Frame
import java.awt.event.WindowEvent

private val TitleBarHeight = 38.dp

/** Левый отступ первой кнопки от края окна — как у нативных macOS-окон. */
private val TrafficLightsStartPadding = 12.dp

/**
 * Кастомный оконный хром для `undecorated = true` Window (P5.T6): своя draggable-полоса сверху
 * (системный заголовок `undecorated = true` убирает вместе с кнопками).
 *
 * Полоса совмещает четыре функции:
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
 * 4. **Нейтральная заливка + hairline-разделитель снизу** (2026-09-17, второй пересмотр в тот же
 *    день — см. следующий абзац про отмену более раннего цветного решения). Полоса красится в
 *    `colorScheme.background` — тот же токен, что [com.aniko.ui.theme.AppTheme] использует как
 *    базовый фон окна (её корневой `Box.background(colorScheme.background)`, см. её KDoc) — БЕЗ
 *    альфы и БЕЗ акцентного тона, так что заливка полосы буквально совпадает с фоном контента под
 *    ней и глаз не видит шва между ними. Отделяет её от контента только тонкая
 *    `HorizontalDivider()` (дефолты `DividerDefaults.Thickness`/`color = outlineVariant`) снизу —
 *    тот же приглушённый hairline-паттерн, что `VerticalDivider()` между сайдбаром и контентом в
 *    `AdaptiveScaffold.kt`. Такой связкой (полоса неотличима по заливке от контента + чёткая, но
 *    едва заметная линия снизу) размечают тайтлбар большинство современных dev-инструментов
 *    (VS Code, Linear, сам Claude Code).
 *
 * **Отмена цветного редизайна того же дня.** Первая правка 2026-09-17 (коммит `04fd884`)
 * перекрашивала полосу в тонированную `colorScheme.surface` с альфой
 * [com.aniko.ui.theme.AnixColors.glassTintAlpha] («современный цветной аниме-стиль» —
 * фиолетовый/золотой/кримзон, сэмплированные из брендовых акцентов иконки приложения). Владелец
 * продукта увидел результат вживую и явно передумал: «нет, давай вот эту полоску где топбар
 * сделаем как в приложении Claude Code например» — просьба заменить яркий акцентный вид на
 * сдержанный нейтральный, по образцу тайтлбаров современных dev-инструментов. Эта правка отменяет
 * тонированную заливку (п.4 выше) и акцентные цвета кружков (см. KDoc [TrafficLightButtons]) в
 * пользу нейтральных токенов; сама инфраструктура темизации (полоса и кнопки видят реальную тему
 * приложения через [anixColorScheme]/[anixExtraColors], а не дефолтную M3-схему — см. следующий
 * абзац) остаётся: это была реальная архитектурная починка, не часть отменённого цветного решения.
 *
 * **Тема**: полоса рисуется в `Main.kt` СНАРУЖИ `App() { AppTheme { ... } }`
 * (сиблинг, не потомок — `AppTheme` видит только своё поддерево), поэтому `MaterialTheme.
 * colorScheme`/[LocalAnixColors] в её месте иначе разрешались бы в дефолтную (не Anix) схему M3,
 * а не в реальную тему приложения. [darkTheme] прокидывается из `Main.kt` (тот же `ThemeStore`,
 * что читает `App()`) и оборачивает полосу в [MaterialTheme] + [LocalAnixColors] через
 * [anixColorScheme]/[anixExtraColors] — лёгкие, не связанные с [com.aniko.ui.theme.AppTheme]
 * функции без побочного `Box.fillMaxSize()` (сам `AppTheme` для этого не годится: его
 * background-Box без веса растянулся бы на всю оставшуюся высоту `Column`, вытеснив `content()`
 * снизу). И полоса, и кнопки внутри неё (см. KDoc [TrafficLightButtons]) действительно
 * адаптируются под обе темы — прежнее решение «фиксированные OS-цвета/без фона, тема не важна»
 * пересмотрено по прямому запросу продукта, а затем (см. абзац выше) сам стиль внутри этой
 * инфраструктуры пересмотрен ещё раз — с яркого на нейтральный.
 *
 * **Единый путь управления окном — AWT `Frame`** (2026-09-17, ревью F1): и кнопки, и двойной
 * клик работают через [LocalDesktopWindow]: close — синтетический `WINDOW_CLOSING` (попадает
 * в `onCloseRequest` окна, как и системная кнопка), minimize/zoom — `extendedState`
 * (`ComposeWindow` — наследник `JFrame`). Compose `WindowState.placement` не узнает о прямом
 * изменении `Frame`, поэтому второй «конкурирующей» системы через `WindowState` быть не должно.
 *
 * @param darkTheme текущая тема приложения (см. `ThemeStore`/`AppTheme`) — определяет, какая из
 *   пары Anix-схем красит полосу и кнопки.
 * @param content контент приложения под полосой.
 */
@Composable
fun WindowScope.AnikoDesktopChrome(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val window = LocalDesktopWindow.current
    val frame = window as? Frame
    Column(modifier = Modifier.fillMaxSize()) {
        val extraColors = anixExtraColors(darkTheme)
        MaterialTheme(colorScheme = anixColorScheme(darkTheme)) {
            CompositionLocalProvider(LocalAnixColors provides extraColors) {
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
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(TitleBarHeight)
                                .background(MaterialTheme.colorScheme.background),
                    ) {
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
                HorizontalDivider()
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}
