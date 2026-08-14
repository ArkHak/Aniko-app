package com.aniko.app.feature.release.rating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aniko.model.CommunityListCounts
import com.aniko.model.ListStatus
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Горизонтальная сегментная полоса распределения релиза по спискам сообщества (P7.T10, D5-a
 * брифа Трека D Фазы 7): 5 сегментов — [ListStatus.WATCHING]/[ListStatus.PLANNED]/
 * [ListStatus.COMPLETED]/[ListStatus.ON_HOLD]/[ListStatus.DROPPED] — пропорционально долям
 * [counts], плюс легенда снизу (цвет — подпись статуса — число). `favorites`/`collection` из
 * [CommunityListCounts] сюда не входят — это не статусы списка, а отдельные независимые флаги.
 *
 * НЕ [com.aniko.ui.component.chart.DonutChart] (D5-a): donut зарезервирован за экраном профиля
 * будущей Фазы 9, чтобы в приложении не было двух визуально одинаковых «бубликов» с разным
 * смыслом. Локальный компонент прямо в `feature/release/rating`, а не в `shared/ui` — общие
 * модули заморожены на время Фазы 7 (см. бриф); при появлении второго потребителя переезд в
 * `shared/ui` — задача другой фазы.
 *
 * Подписи сегментов переиспользуют [ListStatus.displayName] (`shared/ui/i18n/ListStatusStrings`)
 * — тот же источник правды, что и статус-чипы `ReleaseDetailsScreen`/`LibraryScreen`, без
 * дублирования новыми строковыми ключами.
 */
@Composable
fun CommunityListBar(
    counts: CommunityListCounts,
    modifier: Modifier = Modifier,
    palette: List<Color> = AnixThemeTokens.colors.chartSeries,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val entries =
        listOf(
            ListStatus.WATCHING to counts.watching,
            ListStatus.PLANNED to counts.plan,
            ListStatus.COMPLETED to counts.completed,
            ListStatus.ON_HOLD to counts.holdOn,
            ListStatus.DROPPED to counts.dropped,
        )
    val total = entries.sumOf { it.second }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        if (total <= 0) {
            Text(text = strings.chartNoData, style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(BAR_HEIGHT)
                        .clip(RoundedCornerShape(dimens.cornerS)),
            ) {
                for (index in entries.indices) {
                    val count = entries[index].second
                    if (count > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(count.toFloat())
                                    .fillMaxHeight()
                                    .background(palette[index % palette.size]),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                entries.forEachIndexed { index, (status, count) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(LEGEND_SWATCH_SIZE)
                                    .background(palette[index % palette.size], RoundedCornerShape(dimens.cornerS)),
                        )
                        Text(text = status.displayName(strings), style = MaterialTheme.typography.bodySmall)
                        Text(text = count.toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private val BAR_HEIGHT = 24.dp
private val LEGEND_SWATCH_SIZE = 12.dp
