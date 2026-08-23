@file:Suppress("MatchingDeclarationName")
// Файл назван по главному экспорту (fun TitleCard), а не по вспомогательному enum
// TitleCardLayout — так и задумано брифом Фазы 6 (P6.T1): один публичный файл-компонент.

package com.aniko.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.aniko.model.Release
import com.aniko.model.ReleaseStatus
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Раскладка [TitleCard]:
 * - [Grid] — постер сверху, заголовок под ним (поведение бывшего `ReleaseCard`).
 * - [List] — постер слева, заголовок/subtitle/бейджи справа в столбик (списки/история).
 */
enum class TitleCardLayout {
    Grid,
    List,
}

/**
 * Карточка релиза общего назначения (Фаза 6, P6.T1/T3): постер (см. [AnixPoster]) + заголовок +
 * оверлей бейджей (рейтинг/скоро/новая серия/статус в списке/избранное).
 *
 * Чисто презентационный компонент — не знает про ViewModel/Repository, принимает уже готовую
 * доменную модель. [isNewEpisode] и [subtitle] намеренно не читаются из [Release] — модель не
 * содержит этих данных, они вычисляются/форматируются вызывающей стороной (LOC-логика Фазы 7+).
 */
@Suppress("LongParameterList") // Публичная сигнатура зафиксирована брифом Фазы 6 (P6.T1): 8
// параметров — все опциональные, кроме release/onClick, композабл-функция карточки.
@Composable
fun TitleCard(
    release: Release,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layout: TitleCardLayout = TitleCardLayout.Grid,
    onLongClick: (() -> Unit)? = null,
    isNewEpisode: Boolean = false,
    subtitle: String? = null,
    posterWidth: Dp? = null,
) {
    when (layout) {
        TitleCardLayout.Grid ->
            GridTitleCard(release, onClick, modifier, onLongClick, isNewEpisode, subtitle, posterWidth)
        TitleCardLayout.List ->
            ListTitleCard(release, onClick, modifier, onLongClick, isNewEpisode, subtitle, posterWidth)
    }
}

@Suppress("LongParameterList") // Проброс параметров TitleCard в конкретную раскладку, см. выше.
@Composable
private fun GridTitleCard(
    release: Release,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
    isNewEpisode: Boolean,
    subtitle: String?,
    posterWidth: Dp?,
) {
    val dimens = AnixThemeTokens.dimens
    val resolvedPosterWidth = posterWidth ?: dimens.posterWidth

    // Без явной ширины на Column заголовок под постером не переносится по maxLines внутри
    // LazyRow (HorizontalPosterRail): элемент получает не ограниченные по ширине constraints,
    // и Text растягивается на всю "виртуально бесконечную" ширину ряда вместо переноса по
    // ширине постера — вылезает за его рамки на главном экране (найдено сверкой с макетом,
    // 2026-08-23). Column должна быть той же ширины, что и AnixPoster ниже.
    Column(modifier = modifier.width(resolvedPosterWidth)) {
        Box {
            AnixPoster(
                url = release.posterUrl,
                contentDescription = release.title,
                width = resolvedPosterWidth,
                modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            )

            TitleCardStatusOverlay(
                release = release,
                isNewEpisode = isNewEpisode,
                modifier = Modifier.align(Alignment.TopStart).padding(dimens.spaceXs),
            )

            PersonalStateOverlay(
                release = release,
                modifier = Modifier.align(Alignment.TopEnd).padding(dimens.spaceXs),
            )
        }

        Text(
            text = release.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = dimens.spaceXs),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("LongParameterList") // Проброс параметров TitleCard в конкретную раскладку, см. выше.
@Composable
private fun ListTitleCard(
    release: Release,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
    isNewEpisode: Boolean,
    subtitle: String?,
    posterWidth: Dp?,
) {
    val dimens = AnixThemeTokens.dimens

    Row(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        Box {
            AnixPoster(
                url = release.posterUrl,
                contentDescription = release.title,
                width = posterWidth ?: dimens.posterWidthS,
            )

            TitleCardStatusOverlay(
                release = release,
                isNewEpisode = isNewEpisode,
                modifier = Modifier.align(Alignment.TopStart).padding(dimens.spaceXs),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            Text(
                text = release.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PersonalStateOverlay(release = release, modifier = Modifier, horizontal = true)
        }
    }
}

/** Оверлей "объективного" состояния релиза: рейтинг / скоро / новая серия. */
@Composable
private fun TitleCardStatusOverlay(
    release: Release,
    isNewEpisode: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val isAnnounced = release.status == ReleaseStatus.ANNOUNCE
    val hasOverlay = release.grade != null || isAnnounced || isNewEpisode
    if (!hasOverlay) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        if (isAnnounced) {
            ComingSoonBadge()
        } else if (isNewEpisode) {
            NewEpisodeBadge()
        }
        release.grade?.let { grade -> RatingBadge(grade) }
    }
}

/** Оверлей персонального состояния: избранное / статус в списке пользователя. */
@Composable
private fun PersonalStateOverlay(
    release: Release,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    val status = release.myListStatus
    if (!release.isFavorite && status == null) return

    if (horizontal) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            if (release.isFavorite) FavoriteIndicatorBadge()
            status?.let { ListStatusChip(it, style = ListStatusChipStyle.Compact) }
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            if (release.isFavorite) FavoriteIndicatorBadge()
            status?.let { ListStatusChip(it, style = ListStatusChipStyle.Compact) }
        }
    }
}
