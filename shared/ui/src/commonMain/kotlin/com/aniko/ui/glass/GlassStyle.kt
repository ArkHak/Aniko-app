package com.aniko.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Три плотности материала Liquid Glass (Apple iOS 26 `UIGlassEffect.Style`-аналог) — влияют на
 * силу блюра и плотность тонирующей заливки, см. [LiquidGlassDefaults.style]. Regular — дефолт,
 * тот же, что использует `AnixNavigationBar`.
 */
enum class GlassIntensity { Thin, Regular, Thick }

/**
 * Полный набор параметров отрисовки [com.aniko.ui.glass.LiquidGlass] — намеренно "плоский" data
 * class (тот же приём, что [com.aniko.ui.theme.AnixColors]/[com.aniko.ui.theme.AnixDimens]): все
 * поля — уже готовые к отрисовке значения (Dp/Color/Float), сам модификатор не решает, ОТКУДА они
 * взялись — это ответственность [LiquidGlassDefaults.style].
 *
 * @param blurRadius радиус backdrop-blur (реальный, до даунсемплинга — см.
 *   [downsampleFactor]).
 * @param tint базовый цвет тонирующей заливки (обычно `MaterialTheme.colorScheme.surface`).
 * @param tintAlpha альфа [tint] ПОВЕРХ размытого фона (лёгкая, когда блюр доступен).
 * @param fallbackAlpha альфа [tint], когда реального блюра нет (API<31 Android, либо источник не
 *   задан) — плотнее [tintAlpha], т.к. заменяет собой весь визуальный вес фона.
 * @param specular цвет блика по верхней кромке.
 * @param specularHeight высота полосы блика.
 * @param rim цвет волосяной обводки по контуру [shape].
 * @param rimWidth толщина обводки.
 * @param shape форма стекла (клип + обводка + форма источника для blur-семплинга).
 * @param downsampleFactor во сколько раз (доля от 1) уменьшается разрешение слоя перед блюром —
 *   см. KDoc [com.aniko.ui.glass.LiquidGlass] про производительность.
 */
@Immutable
data class LiquidGlassStyle(
    val blurRadius: Dp,
    val tint: Color,
    val tintAlpha: Float,
    val fallbackAlpha: Float,
    val specular: Color,
    val specularHeight: Dp,
    val rim: Color,
    val rimWidth: Dp,
    val shape: Shape,
    val downsampleFactor: Float = DEFAULT_DOWNSAMPLE_FACTOR,
)

/** Даунсемплинг слоя стекла по умолчанию — см. KDoc [LiquidGlassStyle.downsampleFactor]. */
const val DEFAULT_DOWNSAMPLE_FACTOR: Float = 0.25f

/**
 * Дефолтные стили Liquid Glass — читает токены темы ([AnixThemeTokens]), а не хардкодит цвета/
 * размеры внутри самого модификатора (по конвенции проекта, см. KDoc [com.aniko.ui.theme.
 * AnixDimens]/[com.aniko.ui.theme.AnixColors]).
 */
object LiquidGlassDefaults {
    @Composable
    fun style(
        shape: Shape = RectangleShape,
        intensity: GlassIntensity = GlassIntensity.Regular,
    ): LiquidGlassStyle {
        val dimens = AnixThemeTokens.dimens
        val colors = AnixThemeTokens.colors
        val (blurMultiplier, tintMultiplier) =
            when (intensity) {
                GlassIntensity.Thin -> THIN_BLUR_MULTIPLIER to THIN_TINT_MULTIPLIER
                GlassIntensity.Regular -> REGULAR_MULTIPLIER to REGULAR_MULTIPLIER
                GlassIntensity.Thick -> THICK_BLUR_MULTIPLIER to THICK_TINT_MULTIPLIER
            }
        return LiquidGlassStyle(
            blurRadius = dimens.glassBlurRadius * blurMultiplier,
            tint = MaterialTheme.colorScheme.surface,
            tintAlpha = (colors.glassTintAlpha * tintMultiplier).coerceIn(0f, 1f),
            fallbackAlpha = FALLBACK_ALPHA,
            specular = colors.glassSpecular,
            specularHeight = dimens.glassSpecularHeight,
            rim = colors.glassRim,
            rimWidth = dimens.glassRimWidth,
            shape = shape,
        )
    }
}

private const val REGULAR_MULTIPLIER = 1f
private const val THIN_BLUR_MULTIPLIER = 0.65f
private const val THIN_TINT_MULTIPLIER = 0.8f
private const val THICK_BLUR_MULTIPLIER = 1.4f
private const val THICK_TINT_MULTIPLIER = 1.2f

/** Плотность заливки без реального блюра (API<31 Android/нет источника) — см. KDoc [LiquidGlassStyle.fallbackAlpha]. */
private const val FALLBACK_ALPHA = 0.94f
