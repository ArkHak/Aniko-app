package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Небольшие пилл/кружок-оверлеи поверх постера (AnixPoster), используемые TitleCard
 * (Фаза 6, P6.T3). Вынесены в отдельный файл, чтобы переиспользоваться и другими компонентами
 * трека B (HorizontalPosterRail/ProgressRow/EpisodeGrid) без импорта всего TitleCard.kt.
 */

/** Компактный бейдж рейтинга: звезда + оценка с одним знаком после запятой. */
@Composable
fun RatingBadge(
    grade: Double,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    val text = formatGrade(grade)

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(dimens.cornerPill))
                .background(colors.posterScrim)
                .clearAndSetSemantics {
                    contentDescription = strings.badgeRatingContentDescription(text)
                }.padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs / DIVISOR_HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs / DIVISOR_HALF),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(dimens.badgeIconSize),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/** Текстовый пилл «новая серия». */
@Composable
fun NewEpisodeBadge(modifier: Modifier = Modifier) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(dimens.cornerPill))
                .background(colors.newEpisode)
                .clearAndSetSemantics {
                    contentDescription = strings.badgeNewEpisodeContentDescription
                }.padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs / DIVISOR_HALF),
    ) {
        Text(
            text = strings.badgeNewEpisode,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onNewEpisode,
        )
    }
}

/** Текстовый пилл «скоро» — для релизов в статусе [com.aniko.model.ReleaseStatus.ANNOUNCE]. */
@Composable
fun ComingSoonBadge(modifier: Modifier = Modifier) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(dimens.cornerPill))
                .background(colors.warning)
                .padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs / DIVISOR_HALF),
    ) {
        Text(
            text = strings.badgeComingSoon,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onWarning,
        )
    }
}

/**
 * Кружок-индикатор «в избранном» поверх постера. Перенесено из `ReleaseCard.kt` (была приватная
 * `FavoriteBadge`) и переименовано, чтобы не путаться с `Icons.Filled.Favorite`.
 */
@Composable
fun FavoriteIndicatorBadge(modifier: Modifier = Modifier) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current

    Box(
        modifier =
            modifier
                .size(dimens.badgeSize)
                .clip(CircleShape)
                .background(colors.posterScrim),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = strings.commonFavoriteBadge,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(dimens.badgeIconSize),
        )
    }
}

/** Форматирует оценку с ровно одним знаком после запятой без JVM-only `String.format`. */
private fun formatGrade(grade: Double): String {
    val scaled = (grade * DECIMAL_SCALE).roundToInt()
    val whole = scaled / DECIMAL_SCALE
    val fraction = abs(scaled % DECIMAL_SCALE)
    return "$whole.$fraction"
}

private const val DECIMAL_SCALE = 10
private const val DIVISOR_HALF = 2
