package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        AnixIcon(
            name = "star",
            contentDescription = null,
            filled = true,
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

/**
 * Бейдж рейтинга в правом верхнем углу постера в сетке (desktop-мокап Claude Design, строка 757):
 * мини-плашка без иконки, контрастная поверх постера.
 */
@Composable
fun TopEndRatingBadge(
    grade: Double,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val text = formatGrade(grade)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(TOP_END_RATING_RADIUS))
                .background(TOP_END_RATING_BACKGROUND)
                .clearAndSetSemantics {
                    contentDescription = strings.badgeRatingContentDescription(text)
                }.padding(
                    horizontal = TOP_END_RATING_HORIZONTAL_PADDING,
                    vertical = TOP_END_RATING_VERTICAL_PADDING,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = TOP_END_RATING_FONT_SIZE,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
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

/**
 * Текстовый пилл «SUB» — тип озвучки является дорожкой субтитров, а не дубляжом (P8.T6,
 * `docs/REELWAVE_PLAN.md`). Живая верификация подтвердила реальное поле `is_sub` в ответе
 * `GET episode/{releaseId}` (см. `EpisodeMapper`/`VoiceType.isSub`) — бейдж не выдуман под мокап.
 */
@Composable
fun SubBadge(modifier: Modifier = Modifier) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(dimens.cornerPill))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clearAndSetSemantics {
                    contentDescription = strings.badgeSubContentDescription
                }.padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs / DIVISOR_HALF),
    ) {
        Text(
            text = strings.badgeSub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
 * `FavoriteBadge`) и переименовано, чтобы не путаться с иконкой `AnixIcon(name = "favorite")`.
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
        AnixIcon(
            name = "favorite",
            contentDescription = strings.commonFavoriteBadge,
            filled = true,
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

// Токены [TopEndRatingBadge] — точные значения desktop-мокапа Claude Design (строка 757).
private val TOP_END_RATING_BACKGROUND = Color.Black.copy(alpha = 0.55f)
private val TOP_END_RATING_RADIUS = 7.dp
private val TOP_END_RATING_HORIZONTAL_PADDING = 6.dp
private val TOP_END_RATING_VERTICAL_PADDING = 2.dp
private val TOP_END_RATING_FONT_SIZE = 10.sp
