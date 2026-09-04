package com.aniko.ui.component.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
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
// P5.T5, и `DonutChart` в этом же треке). [barShape]/[gap]/[maxBarWidth] добавлены аддитивно
// (Track A, точное соответствие макету Профиля) — все с дефолтами, равными прежнему поведению
// (`RectangleShape`/`dimens.spaceS`/`Dp.Unspecified` = full width), поэтому существующие вызовы
// (`ComponentsGallerySection`) не меняются визуально.
@Composable
fun WeeklyBarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = AnixThemeTokens.colors.chartTrack,
    height: Dp = AnixThemeTokens.dimens.chartHeight,
    maxValue: Float? = null,
    barShape: Shape = RectangleShape,
    gap: Dp = AnixThemeTokens.dimens.spaceS,
    maxBarWidth: Dp = Dp.Unspecified,
) {
    val dimens = AnixThemeTokens.dimens
    val fractions = barHeightFractions(entries.map { it.value }, maxValue)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
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
                            (if (maxBarWidth.isSpecified) Modifier.width(maxBarWidth) else Modifier.fillMaxWidth())
                                .height(height * fractions[index])
                                .clip(barShape)
                                .background(barColor, barShape),
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
 * Доля высоты каждого столбика [WeeklyBarChart], клэмпнутая в `[MIN_VISIBLE_FRACTION, 1f]`.
 *
 * [maxValue] — явный потолок графика; если не задан, берётся максимум среди [values] (0f, если
 * список пуст).
 *
 * Найдено живым запуском на iOS-симуляторе (Track A, точное соответствие макету Профиля,
 * 2026-09-04): у нулевого/пустого столбика раньше высота была ровно `0f` — на реальном аккаунте
 * с нулевой активностью за все 7 последних дней это давало ПОЛНОСТЬЮ невидимый график (ни одного
 * столбика, только подписи дней под пустым местом) — выглядит как сломанный компонент, хотя
 * данные корректны. Референсный макет (`Reelwave Prototype.dc.html`,
 * `Math.max(6, Math.round(v/activityMax*100))`) всегда даёт видимый минимальный "огрызок"
 * столбика — [MIN_VISIBLE_FRACTION] воспроизводит это же поведение здесь, включая случай
 * `effectiveMax == 0f` (раньше отдельно возвращавший все нули).
 */
internal fun barHeightFractions(
    values: List<Float>,
    maxValue: Float?,
): List<Float> {
    val effectiveMax = maxValue ?: (values.maxOrNull() ?: 0f)
    if (effectiveMax == 0f) return values.map { MIN_VISIBLE_FRACTION }
    return values.map { (it / effectiveMax).coerceIn(MIN_VISIBLE_FRACTION, 1f) }
}

/** 6% — та же минимальная видимая доля столбика, что и в макете (см. KDoc [barHeightFractions]). */
private const val MIN_VISIBLE_FRACTION = 0.06f
