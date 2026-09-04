package com.aniko.ui.component.chart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Гистограмма распределения оценок 1-5 звёзд (Фаза 6, P6.T11): пять строк
 * «число звёзд — `LinearProgressIndicator` (доля от максимального [counts]) — счётчик числом».
 *
 * Обычные Compose-примитивы, а не `Canvas` — в отличие от [DonutChart]/[WeeklyBarChart], здесь
 * нет произвольной геометрии, которую было бы оправдано рисовать вручную; план фиксирует именно
 * «гистограмма», а `Canvas` в этом треке привязан только к donut/bar графикам.
 *
 * [counts] — ровно 5 элементов, индекс 0 = 1 звезда … индекс 4 = 5 звёзд.
 */
@Composable
fun RatingHistogram(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    averageLabel: String? = null,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val maxCount = counts.maxOrNull() ?: 0

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = strings.ratingHistogramTitle, style = MaterialTheme.typography.titleMedium)
        if (averageLabel != null) {
            Text(text = averageLabel, style = MaterialTheme.typography.bodyMedium)
        }
        counts.forEachIndexed { index, count ->
            val stars = index + 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            ) {
                Text(text = stars.toString(), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { progressFraction(count, maxCount) },
                    modifier = Modifier.weight(1f),
                )
                Text(text = count.toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Ряд из 5 кликабельных звёзд для ввода личной оценки (Фаза 6, P6.T11). Каждая звезда —
 * `AnixIcon(name = "star", filled = true)` (закрашена, если её порядковый номер не больше
 * [myRating]) либо `filled = false` (не закрашена, включая случай `myRating == null`).
 *
 * Каждая звезда обёрнута в `Modifier.defaultMinSize(minTouchTarget, minTouchTarget)` — сама
 * иконка визуально меньше токена, но интерактивная область — не меньше минимального тач-таргета
 * (WCAG/Material, см. KDoc [com.aniko.ui.theme.AnixDimens.minTouchTarget]).
 */
@Composable
fun RatingInput(
    myRating: Int?,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = strings.ratingYourScore, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
            repeat(RATING_STARS_COUNT) { index ->
                val stars = index + 1
                val filled = stars <= (myRating ?: 0)
                val description = strings.ratingStarsContentDescription(stars)

                Box(
                    modifier =
                        Modifier
                            .defaultMinSize(
                                minWidth = dimens.minTouchTarget,
                                minHeight = dimens.minTouchTarget,
                            ).clickable(
                                enabled = enabled,
                                onClickLabel = description,
                                role = Role.Button,
                                onClick = { onRate(stars) },
                            )
                            // Подтверждено на устройстве (Фаза 11, T9): contentDescription на
                            // вложенном Icon не сливается с кликабельным Box сам по себе —
                            // TalkBack фокусировал звезду без имени, только onClickLabel-подсказку.
                            .clearAndSetSemantics {
                                contentDescription = description
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    AnixIcon(
                        name = "star",
                        contentDescription = null,
                        filled = filled,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Доля прогресс-бара в [RatingHistogram], клэмпнутая в `[0f, 1f]`. `maxCount <= 0` даёт 0f
 * (не деление на ноль/на отрицательное число).
 */
internal fun progressFraction(
    count: Int,
    maxCount: Int,
): Float {
    if (maxCount <= 0) return 0f
    return (count.toFloat() / maxCount).coerceIn(0f, 1f)
}

private const val RATING_STARS_COUNT = 5
