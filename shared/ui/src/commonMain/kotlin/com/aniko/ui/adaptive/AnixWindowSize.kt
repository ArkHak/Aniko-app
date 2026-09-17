package com.aniko.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Границы M3 (compact/medium/expanded) — единственный авторитет размера окна в приложении.
 * `ListDetailHost` (P5.T3) дополнительно читает `currentWindowAdaptiveInfo()` из
 * material3-adaptive для posture/hinge складных устройств, но границы 600/840dp ОБЯЗАНЫ
 * совпадать с этим enum — расхождение между ними считается багом.
 */
enum class AnixWindowSize {
    Compact,
    Medium,
    Expanded,
    ;

    /** true — есть место под постоянную панель детали рядом со списком (P5.T3). */
    val isTwoPane: Boolean get() = this != Compact

    companion object {
        const val MEDIUM_MIN_DP = 600
        const val EXPANDED_MIN_DP = 840

        fun fromWidthDp(widthDp: Int): AnixWindowSize =
            when {
                widthDp < MEDIUM_MIN_DP -> Compact
                widthDp < EXPANDED_MIN_DP -> Medium
                else -> Expanded
            }
    }
}

val LocalAnixWindowSize = staticCompositionLocalOf { AnixWindowSize.Compact }

/**
 * Вычисляет текущий [AnixWindowSize] из ширины окна.
 *
 * `WindowInfo.containerDpSize` (compose.ui 1.11.1, `androidx.compose.ui.platform.WindowInfo`)
 * отдаёт ширину окна уже в dp напрямую (реальная реализация `WindowInfoImpl` держит её как
 * отдельный `MutableState<DpSize>`, обновляемый платформенным кодом при ресайзе) — поэтому
 * `LocalDensity`/ручная конвертация из пикселей здесь не нужна, в отличие от более старого
 * `containerSize` (`IntSize` в пикселях).
 */
@Composable
fun rememberAnixWindowSize(): AnixWindowSize {
    val containerDpSize = LocalWindowInfo.current.containerDpSize
    val widthDp = containerDpSize.width.value.toInt()
    return AnixWindowSize.fromWidthDp(widthDp)
}
