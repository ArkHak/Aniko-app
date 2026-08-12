package com.aniko.ui.component.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Столбчатая диаграмма (например, активность просмотра по дням недели) (Фаза 6, P6.T10).
 * Абстрактные данные — [entries] не привязаны к конкретной семантике, см. KDoc [BarEntry].
 *
 * Высота столбика — доля от максимума ([maxValue], либо максимум среди [entries], если не
 * задан), считается чистой функцией [barHeightFractions] для тестируемости без Compose UI-теста.
 */
@Suppress("LongParameterList") // Публичная сигнатура зафиксирована брифом P6.T10: barColor,
// trackColor, height и maxValue — независимые опциональные настройки внешнего вида одного
// графика, у всех есть токенизированные дефолты (тот же прецедент, что и `AdaptiveScaffold`,
// P5.T5, и `DonutChart` в этом же треке).
@Composable
fun WeeklyBarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = AnixThemeTokens.colors.chartTrack,
    height: Dp = AnixThemeTokens.dimens.chartHeight,
    maxValue: Float? = null,
) {
    val dimens = AnixThemeTokens.dimens
    val fractions = barHeightFractions(entries.map { it.value }, maxValue)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        entries.forEachIndexed { index, entry ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(height)
                            .background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(height * fractions[index])
                                .background(barColor),
                    )
                }
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = dimens.spaceXs),
                )
            }
        }
    }
}

/**
 * Доля высоты каждого столбика [WeeklyBarChart], клэмпнутая в `[0f, 1f]`.
 *
 * [maxValue] — явный потолок графика; если не задан, берётся максимум среди [values] (0f, если
 * список пуст). Эффективный максимум, равный нулю, даёт все доли равными нулю (без деления на
 * ноль).
 */
internal fun barHeightFractions(
    values: List<Float>,
    maxValue: Float?,
): List<Float> {
    val effectiveMax = maxValue ?: (values.maxOrNull() ?: 0f)
    if (effectiveMax == 0f) return values.map { 0f }
    return values.map { (it / effectiveMax).coerceIn(0f, 1f) }
}
