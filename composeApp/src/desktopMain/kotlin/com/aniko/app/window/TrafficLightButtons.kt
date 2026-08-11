package com.aniko.app.window

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Системные цвета macOS traffic lights (close/minimize/zoom) — это НЕ часть дизайн-системы
 * приложения ([com.aniko.ui.theme.AppTheme] / `AnixThemeTokens`), а фиксированная палитра ОС,
 * которую пользователь узнаёт по кнопкам любого нативного macOS-окна. Намеренно не берутся из
 * токенов темы: они не должны меняться со светлой/тёмной темой приложения или локализацией.
 */
@Suppress("MagicNumber")
private object MacTrafficLightColors {
    val Close = Color(0xFFFF5F57)
    val Minimize = Color(0xFFFEBC2E)
    val Zoom = Color(0xFF28C840)
}

private val TrafficLightDiameter = 12.dp
private val TrafficLightSpacing = 8.dp

/**
 * Три круглые кнопки в стиле macOS traffic lights (close/minimize/zoom) для кастомного
 * оконного хрома (`undecorated = true`, см. `AnikoDesktopChrome.kt`, P5.T6).
 *
 * "Zoom" здесь — это [onToggleMaximize] (переключение между `WindowPlacement.Floating` и
 * `WindowPlacement.Maximized`), как и у нативной зелёной кнопки в macOS. Без индикации нажатия
 * (ripple) намеренно — нативные traffic lights её тоже не показывают, только hover-глиф, которым
 * здесь сознательно жертвуем ради простоты первой версии (см. отчёт по P5.T6).
 */
@Composable
fun TrafficLightButtons(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(horizontal = TrafficLightSpacing)) {
        TrafficLightDot(color = MacTrafficLightColors.Close, onClick = onClose)
        Spacer(Modifier.size(TrafficLightSpacing))
        TrafficLightDot(color = MacTrafficLightColors.Minimize, onClick = onMinimize)
        Spacer(Modifier.size(TrafficLightSpacing))
        TrafficLightDot(color = MacTrafficLightColors.Zoom, onClick = onToggleMaximize)
    }
}

@Composable
private fun TrafficLightDot(
    color: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(TrafficLightDiameter)
                .background(color = color, shape = CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    )
}
