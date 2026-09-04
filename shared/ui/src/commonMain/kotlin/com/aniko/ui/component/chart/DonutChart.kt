package com.aniko.ui.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Кольцевая диаграмма долей [slices] (Фаза 6, P6.T10). Чисто презентационный компонент — не
 * форматирует значения и не знает единиц измерения, см. KDoc [ChartSlice].
 *
 * Если [slices] пуст или сумма всех [ChartSlice.value] равна нулю — вместо графика показывается
 * [com.aniko.ui.i18n.Strings.chartNoData].
 *
 * Математика углов сегментов вынесена в чистую (без Compose) функцию [donutSweepAngles], чтобы
 * её можно было покрыть unit-тестом без Compose UI-теста.
 */
@Suppress("LongParameterList") // Публичная сигнатура зафиксирована брифом P6.T10: strokeWidth,
// palette, centerContent и legend — независимые опциональные настройки внешнего вида одного
// графика, у всех есть токенизированные дефолты. Группировка части из них в конфиг-data class
// добавила бы косвенность ради обхода линта, а не ради читаемости (тот же прецедент, что и
// `AdaptiveScaffold`, P5.T5). [size] добавлен аддитивно (Track A, точное соответствие макету
// Профиля — кольцо 88dp вместо дефолтных 160dp) с дефолтом [DONUT_SIZE], поэтому существующие
// вызовы (Profile-легенда снизу, `ComponentsGallerySection`) не меняются визуально.
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = AnixThemeTokens.dimens.donutStrokeWidth,
    palette: List<Color> = AnixThemeTokens.colors.chartSeries,
    centerContent: (@Composable () -> Unit)? = null,
    legend: Boolean = true,
    size: Dp = DONUT_SIZE,
) {
    val strings = LocalStrings.current
    val total = slices.sumOf { it.value.toDouble() }.toFloat()

    if (slices.isEmpty() || total <= 0f) {
        Text(text = strings.chartNoData, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }

    val dimens = AnixThemeTokens.dimens
    val sweepAngles = donutSweepAngles(slices.map { it.value })

    Column(modifier = modifier) {
        Box(
            modifier = Modifier.size(size).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                val strokeWidthPx = strokeWidth.toPx()
                val inset = strokeWidthPx / 2f
                val arcTopLeft = Offset(inset, inset)
                // `this.size` — явная квалификация обязательна: одноимённый параметр `size: Dp`
                // функции (Track A, Профиль) затеняет `DrawScope.size` (Size, размер холста в px)
                // внутри этой лямбды, `size.width`/`size.height` без `this.` резолвились в Dp
                // (compile error) вместо площади рисования. Не относится к работе этой задачи
                // (Player) — точечный фикс существовавшей до неё поломки сборки :shared:ui.
                val arcSize = Size(this.size.width - strokeWidthPx, this.size.height - strokeWidthPx)

                slices.forEachIndexed { index, slice ->
                    val angleRange = sweepAngles[index]
                    val color = slice.color ?: palette[index % palette.size]
                    drawArc(
                        color = color,
                        startAngle = angleRange.start,
                        sweepAngle = angleRange.endInclusive - angleRange.start,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx),
                    )
                }
            }
            centerContent?.invoke()
        }

        if (legend) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = dimens.spaceS),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            ) {
                slices.forEachIndexed { index, slice ->
                    val color = slice.color ?: palette[index % palette.size]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(LEGEND_SWATCH_SIZE)
                                    .background(color, RoundedCornerShape(dimens.cornerS)),
                        )
                        Text(text = slice.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * Углы дуг для [DonutChart] в градусах, по порядку [values], начиная от 0°, пропорционально доле
 * каждого значения от суммы всех [values]. Пустой список или нулевая сумма значений — пустой
 * результат (нет корректных углов для рисования).
 *
 * Возвращает диапазон `[startAngle, startAngle + sweepAngle)` для каждого значения — визуальный
 * поворот всего графика (например, «начинать сверху, а не справа») применяется отдельно в самом
 * `Canvas`, эта функция намеренно не знает про него.
 */
internal fun donutSweepAngles(values: List<Float>): List<ClosedFloatingPointRange<Float>> {
    val total = values.sumOf { it.toDouble() }.toFloat()
    if (values.isEmpty() || total <= 0f) return emptyList()

    var start = 0f
    return values.map { value ->
        val sweep = DONUT_FULL_CIRCLE_DEGREES * (value / total)
        val range = start..(start + sweep)
        start += sweep
        range
    }
}

private const val DONUT_FULL_CIRCLE_DEGREES = 360f
private val DONUT_SIZE = 160.dp
private val LEGEND_SWATCH_SIZE = 12.dp
