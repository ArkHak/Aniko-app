package com.aniko.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.min

/**
 * Аниме-заглушка под картинки: минималистичный лайн-арт неко-девочки (голова с кошачьими
 * ушами, закрытые довольные глаза `^^`, ротик и усы), нарисованный штрихами на мягком
 * вертикальном градиенте из цветов темы.
 *
 * Используется как `loading`/`error`-слот [AnixAsyncImage] (и любых других Coil-обёрток):
 * пока [loading] == true, поверх градиента прокатывается лёгкий shimmer-отблеск; в состоянии
 * ошибки ([loading] == false) заглушка статична.
 *
 * Компонент чисто декоративный: никакой семантики не добавляет (Canvas не порождает
 * accessibility-узлов), текст внутри отсутствует, масштабируется от маленьких ячеек
 * (52dp, `LibraryExpandedLayout`) до hero-обложек — толщина штриха и все пропорции
 * считаются от минимального габарита контейнера.
 */
@Composable
fun AnimeImagePlaceholder(
    modifier: Modifier = Modifier,
    loading: Boolean = true,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val background =
        remember(surfaceVariant, primaryContainer) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        surfaceVariant,
                        lerp(surfaceVariant, primaryContainer, BACKGROUND_TINT_FRACTION),
                    ),
            )
        }

    Box(modifier = modifier.background(background)) {
        NekoLineArt(
            strokeColor = onSurfaceVariant,
            modifier = Modifier.matchParentSize(),
        )
        if (loading) {
            ShimmerSweep(modifier = Modifier.matchParentSize())
        }
    }
}

/** Shimmer: диагональная полупрозрачная светлая полоса, бесконечно прокатывающаяся слева
 *  направо поверх заглушки. Статика в error-слоте достигается тем, что композиция просто
 *  не вызывается (`loading = false`). */
@Composable
private fun ShimmerSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = SHIMMER_TRANSITION_LABEL)
    val progress by transition.animateFloat(
        initialValue = SHIMMER_PROGRESS_START,
        targetValue = SHIMMER_PROGRESS_END,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
            ),
        label = SHIMMER_ANIMATION_LABEL,
    )
    val bandBrush =
        remember {
            Brush.linearGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = SHIMMER_BAND_ALPHA),
                        Color.Transparent,
                    ),
            )
        }

    Canvas(modifier = modifier) {
        val bandWidth = size.width * SHIMMER_BAND_WIDTH_FRACTION
        val travel = size.width + bandWidth
        val bandLeft = -bandWidth + progress * travel
        rotate(degrees = SHIMMER_ANGLE_DEGREES, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawRect(
                brush = bandBrush,
                topLeft = Offset(bandLeft, -size.height),
                size = Size(bandWidth, size.height * SHIMMER_RECT_HEIGHT_FACTOR),
            )
        }
    }
}

/** Лайн-арт неко-девочки: круглое лицо, треугольные уши, глаза-дуги `^^`, ротик и усы.
 *  Все размеры — доли от минимального габарита [DrawScope.size], поэтому рисунок сохраняет
 *  пропорции при любом соотношении сторон контейнера. */
@Composable
private fun NekoLineArt(
    strokeColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val m = min(size.width, size.height)
        if (m < MIN_DRAWABLE_SIZE_PX) return@Canvas // Слишком мало места — не рисуем мусор.
        val strokeWidth = m * STROKE_WIDTH_FRACTION
        val stroke =
            Stroke(
                width = strokeWidth,
                miter = STROKE_MITER,
            )
        val faceCenter = Offset(size.width / 2f, size.height * FACE_CENTER_Y_FRACTION)
        val faceRadius = m * FACE_RADIUS_FRACTION
        val lineColor = strokeColor.copy(alpha = STROKE_ALPHA)
        val softColor = strokeColor.copy(alpha = STROKE_SOFT_ALPHA)

        // Лёгкая заливка лица, чтобы штрих читался на градиенте любой темы.
        drawCircle(
            color = strokeColor.copy(alpha = FACE_FILL_ALPHA),
            radius = faceRadius,
            center = faceCenter,
        )
        drawEars(
            center = faceCenter,
            radius = faceRadius,
            color = lineColor,
            stroke = stroke,
        )
        drawCircle(color = lineColor, radius = faceRadius, center = faceCenter, style = stroke)
        drawHappyEyes(
            center = faceCenter,
            radius = faceRadius,
            color = lineColor,
            strokeWidth = strokeWidth,
        )
        drawMouth(
            center = faceCenter,
            radius = faceRadius,
            color = lineColor,
            strokeWidth = strokeWidth,
        )
        drawWhiskers(
            center = faceCenter,
            radius = faceRadius,
            color = softColor,
            strokeWidth = strokeWidth * WHISKER_WIDTH_FACTOR,
        )
    }
}

/** Кошачьи уши: два контура-треугольника над головой + меньший внутренний треугольник. */
private fun DrawScope.drawEars(
    center: Offset,
    radius: Float,
    color: Color,
    stroke: Stroke,
) {
    drawEar(center = center, radius = radius, mirror = -1f, color = color, stroke = stroke)
    drawEar(center = center, radius = radius, mirror = 1f, color = color, stroke = stroke)
}

/** Один ушной контур: внешний треугольник `baseOuter → tip → baseInner` + внутренний,
 *  стянутый к основанию. [mirror] = -1f — левое ухо, 1f — правое (зеркально по X). */
private fun DrawScope.drawEar(
    center: Offset,
    radius: Float,
    mirror: Float,
    color: Color,
    stroke: Stroke,
) {
    val baseY = center.y - radius * EAR_BASE_Y_FRACTION
    val baseInner = Offset(center.x + mirror * radius * EAR_BASE_INNER_FRACTION, baseY)
    val tip = Offset(center.x + mirror * radius * EAR_TIP_X_FRACTION, center.y - radius * EAR_TIP_Y_FRACTION)
    val baseOuter = Offset(center.x + mirror * radius * EAR_BASE_OUTER_FRACTION, baseY)

    val outer =
        Path().apply {
            moveTo(baseOuter.x, baseOuter.y)
            lineTo(tip.x, tip.y)
            lineTo(baseInner.x, baseInner.y)
        }
    drawPath(path = outer, color = color, style = stroke)

    val inner =
        Path().apply {
            val innerBase = lerpOffset(baseOuter, baseInner, EAR_INNER_INSET_FRACTION)
            val innerTip = lerpOffset(tip, baseInner, EAR_INNER_INSET_FRACTION * EAR_TIP_INSET_FACTOR)
            moveTo(innerBase.x, innerBase.y)
            lineTo(innerTip.x, innerTip.y)
            lineTo(baseInner.x, baseInner.y)
        }
    drawPath(path = inner, color = color.copy(alpha = STROKE_SOFT_ALPHA), style = stroke)
}

/** Закрытые довольные глаза: пары дугообразных штрихов `^^` под линией центра лица. */
private fun DrawScope.drawHappyEyes(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
) {
    val eyeY = center.y - radius * EYE_Y_OFFSET_FRACTION
    val eyeHalf = radius * EYE_HALF_WIDTH_FRACTION
    val eyeRise = radius * EYE_RISE_FRACTION

    drawHappyEye(
        left = Offset(center.x - radius * EYE_SPREAD_FRACTION - eyeHalf, eyeY),
        right = Offset(center.x - radius * EYE_SPREAD_FRACTION + eyeHalf, eyeY),
        rise = eyeRise,
        color = color,
        strokeWidth = strokeWidth,
    )
    drawHappyEye(
        left = Offset(center.x + radius * EYE_SPREAD_FRACTION - eyeHalf, eyeY),
        right = Offset(center.x + radius * EYE_SPREAD_FRACTION + eyeHalf, eyeY),
        rise = eyeRise,
        color = color,
        strokeWidth = strokeWidth,
    )
}

/** Один глаз-дуга: квадратичная кривая с пиком вверх (вид `^`). */
private fun DrawScope.drawHappyEye(
    left: Offset,
    right: Offset,
    rise: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path =
        Path().apply {
            moveTo(left.x, left.y)
            quadraticTo((left.x + right.x) / 2f, left.y - rise * 2f, right.x, right.y)
        }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

/** Маленький ротик-дуга `∪` под глазами. */
private fun DrawScope.drawMouth(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
) {
    val mouthY = center.y + radius * MOUTH_Y_OFFSET_FRACTION
    val mouthHalf = radius * MOUTH_HALF_WIDTH_FRACTION
    val mouthDrop = radius * MOUTH_DROP_FRACTION
    val path =
        Path().apply {
            moveTo(center.x - mouthHalf, mouthY)
            quadraticTo(center.x, mouthY + mouthDrop, center.x + mouthHalf, mouthY)
        }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

/** Усы: по два коротких штриха с каждой стороны мордочки, мягкой (полупрозрачной) линией. */
private fun DrawScope.drawWhiskers(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
) {
    val whiskerY = center.y + radius * WHISKER_Y_OFFSET_FRACTION
    val whiskerLength = radius * WHISKER_LENGTH_FRACTION
    val cheekX = radius * WHISKER_CHEEK_FRACTION
    val spreadStep = radius * WHISKER_SPREAD_FRACTION

    for (side in intArrayOf(-1, 1)) {
        for (row in 0 until WHISKER_ROWS) {
            val offsetY = (row - (WHISKER_ROWS - 1) / 2f) * spreadStep
            val startX = center.x + side * cheekX
            drawLine(
                color = color,
                start = Offset(startX, whiskerY + offsetY),
                end = Offset(startX + side * whiskerLength, whiskerY + offsetY * WHISKER_TILT_FACTOR),
                strokeWidth = strokeWidth,
            )
        }
    }
}

/** Линейная интерполяция между двумя точками (дробная позиция [fraction] вдоль отрезка). */
private fun lerpOffset(
    start: Offset,
    end: Offset,
    fraction: Float,
): Offset = Offset(start.x + (end.x - start.x) * fraction, start.y + (end.y - start.y) * fraction)

// ==== Подбор пропорций лайн-арта (все значения — доли от min(width, height) контейнера) ====

/** Нижний стоп градиента: насколько `primaryContainer` подмешан к `surfaceVariant`. */
private const val BACKGROUND_TINT_FRACTION = 0.45f

/** Альфа светлой полосы shimmer. */
private const val SHIMMER_BAND_ALPHA = 0.14f

/** Ширина shimmer-полосы как доля ширины контейнера. */
private const val SHIMMER_BAND_WIDTH_FRACTION = 0.55f

/** Угол наклона shimmer-полосы, градусы. */
private const val SHIMMER_ANGLE_DEGREES = 20f

/** Высота прямоугольника shimmer: запас под поворотом (в 3 раза выше контейнера). */
private const val SHIMMER_RECT_HEIGHT_FACTOR = 3f

private const val SHIMMER_DURATION_MS = 1400
private const val SHIMMER_PROGRESS_START = 0f
private const val SHIMMER_PROGRESS_END = 1f
private const val SHIMMER_TRANSITION_LABEL = "animePlaceholderShimmer"
private const val SHIMMER_ANIMATION_LABEL = "animePlaceholderShimmerProgress"

/** Минимальный габарит (px), при котором лайн-арт вообще рисуется. */
private const val MIN_DRAWABLE_SIZE_PX = 24f

/** Толщина основного штриха как доля минимального габарита. */
private const val STROKE_WIDTH_FRACTION = 0.028f

/** Альфа основного контура. */
private const val STROKE_ALPHA = 0.7f

/** Альфа второстепенных штрихов (усы, внутренние уши). */
private const val STROKE_SOFT_ALPHA = 0.45f

/** Альфа заливки лица. */
private const val FACE_FILL_ALPHA = 0.05f

private const val STROKE_MITER = 4f

/** Вертикальная позиция центра лица (0 = верх контейнера). */
private const val FACE_CENTER_Y_FRACTION = 0.52f

/** Радиус лица как доля минимального габарита. */
private const val FACE_RADIUS_FRACTION = 0.26f

// Уши
private const val EAR_BASE_INNER_FRACTION = 0.40f
private const val EAR_BASE_OUTER_FRACTION = 1.02f
private const val EAR_BASE_Y_FRACTION = 0.72f
private const val EAR_TIP_X_FRACTION = 1.08f
private const val EAR_TIP_Y_FRACTION = 1.62f
private const val EAR_INNER_INSET_FRACTION = 0.32f
private const val EAR_TIP_INSET_FACTOR = 1.6f

// Глаза
private const val EYE_Y_OFFSET_FRACTION = -0.02f
private const val EYE_SPREAD_FRACTION = 0.42f
private const val EYE_HALF_WIDTH_FRACTION = 0.16f
private const val EYE_RISE_FRACTION = 0.14f

// Рот
private const val MOUTH_Y_OFFSET_FRACTION = 0.30f
private const val MOUTH_HALF_WIDTH_FRACTION = 0.10f
private const val MOUTH_DROP_FRACTION = 0.10f

// Усы
private const val WHISKER_Y_OFFSET_FRACTION = 0.22f
private const val WHISKER_CHEEK_FRACTION = 0.55f
private const val WHISKER_LENGTH_FRACTION = 0.30f
private const val WHISKER_SPREAD_FRACTION = 0.14f
private const val WHISKER_WIDTH_FACTOR = 0.7f
private const val WHISKER_TILT_FACTOR = 1.4f
private const val WHISKER_ROWS = 2
