package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Карточка релиза: постер (см. [AnixPoster]) + заголовок под ним + компактный оверлей
 * в углу постера, отражающий персональное состояние релиза ("в избранном" /
 * статус в списке пользователя).
 *
 * Чисто презентационный компонент — не знает про ViewModel/Repository, принимает
 * уже готовую доменную модель. Переиспользуется в сетках/лентах релизов
 * (поиск, каталог, главная и т.п.), чтобы не дублировать `AnixPoster + Text`.
 */
@Composable
fun ReleaseCard(
    release: Release,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Column(modifier = modifier) {
        Box {
            AnixPoster(
                url = release.posterUrl,
                contentDescription = release.title,
                modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            )

            if (release.isFavorite || release.myListStatus != null) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(dimens.spaceXs),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                ) {
                    if (release.isFavorite) {
                        FavoriteBadge()
                    }
                    val status = release.myListStatus
                    if (status != null) {
                        ListStatusBadge(status)
                    }
                }
            }
        }

        Text(
            text = release.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(top = dimens.spaceXs),
        )
    }
}

@Composable
private fun FavoriteBadge() {
    Box(
        modifier =
            Modifier
                .size(OVERLAY_BADGE_SIZE)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "В избранном",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(OVERLAY_ICON_SIZE),
        )
    }
}

@Composable
private fun ListStatusBadge(status: ListStatus) {
    Box(
        modifier =
            Modifier
                .size(OVERLAY_BADGE_SIZE)
                .clip(CircleShape)
                .background(status.badgeColor()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.shortLabel(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/** Короткое обозначение статуса списка для компактного бейджа поверх постера. */
private fun ListStatus.shortLabel(): String =
    when (this) {
        ListStatus.WATCHING -> "С"
        ListStatus.PLANNED -> "П"
        ListStatus.COMPLETED -> "✓"
        ListStatus.ON_HOLD -> "О"
        ListStatus.DROPPED -> "Б"
    }

@Composable
private fun ListStatus.badgeColor(): Color =
    when (this) {
        ListStatus.WATCHING -> MaterialTheme.colorScheme.primary
        ListStatus.PLANNED -> MaterialTheme.colorScheme.secondary
        ListStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        ListStatus.ON_HOLD -> MaterialTheme.colorScheme.outline
        ListStatus.DROPPED -> MaterialTheme.colorScheme.errorContainer
    }

private val OVERLAY_BADGE_SIZE = 20.dp
private val OVERLAY_ICON_SIZE = 12.dp
