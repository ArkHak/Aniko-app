package com.aniko.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Кастомный индикатор прогресса просмотра серий (замена стандартному `LinearProgressIndicator`
 * в `ProgressRow`, 2026-09-28): «аниме»-стиль под бренд — капсула с градиентной заливкой
 * `primary → secondary` (фиолет → кримзон иконки приложения) и мягким свечением (glow) на
 * переднем фронте прогресса.
 *
 * Рисуется на [Canvas], а не композицией Box'ов, потому что glow должен выходить за пределы
 * самой полоски (полотно выше бара, бар отцентрован по вертикали, свечение не клипается).
 * Трек — `overlay08` (та же подложка, что у гистограммы рейтинга, см. `RatingHistogram.kt`).
 *
 * Прогресс анимируется ([spring]) — при обновлении «просмотрено» полоска доезжает плавно.
 * Семантику (доступность) компонент сознательно НЕ задаёт: единственный потребитель
 * (`ProgressRow`) уже отдаёт текстовое «N из M» на уровне всей строки через
 * `clearAndSetSemantics`, дублировать прогресс-узел под ним не нужно.
 */
@Composable
fun WatchProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = AnixThemeTokens.dimens.progressBarHeight,
) {
    val startColor = MaterialTheme.colorScheme.primary
    val endColor = MaterialTheme.colorScheme.secondary
    val trackColor = AnixThemeTokens.colors.overlay08

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "watchProgress",
    )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                // Полотно выше бара — место под glow, который рисуется вокруг фронта прогресса
                // и не должен обрезаться границами Canvas.
                .height(barHeight + GLOW_PADDING * 2),
    ) {
        val barTop = (size.height - barHeight.toPx()) / 2f
        val barSize = Size(size.width, barHeight.toPx())
        val barCorner = CornerRadius(barHeight.toPx() / 2f)
        val centerY = size.height / 2f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barTop),
            size = barSize,
            cornerRadius = barCorner,
        )

        val fillWidth = size.width * animatedProgress
        if (fillWidth <= 0f) return@Canvas

        drawRoundRect(
            // Градиент растянут на длину ЗАЛИВКИ (не всего трека): фронт прогресса всегда
            // кримзоновый, хвост — фиолетовый, независимо от процента просмотра.
            brush = Brush.horizontalGradient(listOf(startColor, endColor), endX = fillWidth),
            topLeft = Offset(0f, barTop),
            size = Size(fillWidth, barHeight.toPx()),
            cornerRadius = barCorner,
        )

        // Glow-фронт: радиальный градиент из конечного цвета заливки в прозрачный.
        val tip = Offset(fillWidth, centerY)
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(endColor.copy(alpha = GLOW_ALPHA), Color.Transparent),
                    center = tip,
                    radius = GLOW_RADIUS_MULTIPLIER * barHeight.toPx(),
                ),
            radius = GLOW_RADIUS_MULTIPLIER * barHeight.toPx(),
            center = tip,
        )
    }
}

/** Вертикальный запас полотна над/под баром, в который помещается glow. */
private val GLOW_PADDING = 4.dp

/** Радиус свечения относительно высоты бара. */
private const val GLOW_RADIUS_MULTIPLIER = 2.2f

/** Непрозрачность центра glow (к краю уходит в 0 радиальным градиентом). */
private const val GLOW_ALPHA = 0.55f
