package com.aniko.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.model.Episode
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Сетка номеров серий. Встраивается в `ReleaseDetailsScreen`, у которого корневой `Column`
 * уже прокручивается (`Modifier.verticalScroll`, см. `ReleaseDetailsScreen.kt`) — вложенный
 * ленивый список того же направления скролла (`LazyVerticalGrid`) упал бы с ошибкой
 * бесконечной высоты во время measure-прохода. `FlowRow` не ленивый (меряет всех детей сразу),
 * поэтому переносится в такой контейнер без проблем; для реалистичного числа серий одного
 * релиза (десятки, изредка сотни у долгих тайтлов) это не узкое место. `FlowRow` в
 * `compose.foundation` этой версии (Compose Multiplatform 1.11.1) — стабильный публичный API,
 * `ExperimentalLayoutApi` не требуется (проверено компиляцией).
 */
@Suppress("LongParameterList") // Публичная сигнатура зафиксирована брифом P6.T8: episodes/
// onEpisodeClick обязательны, currentPosition/isFiller/cellMinSize/onEpisodeLongClick —
// опциональные точки расширения с дефолтами (текущая серия/маркер филлера/размер ячейки/
// долгое нажатие, добавлено аддитивно в Фазе 7 для контекстного меню серии).
@Composable
fun EpisodeGrid(
    episodes: List<Episode>,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
    currentPosition: Int? = null,
    isFiller: (Episode) -> Boolean = { false },
    cellMinSize: Dp = AnixThemeTokens.dimens.episodeCellMinSize,
    onEpisodeLongClick: ((Episode) -> Unit)? = null,
) {
    val dimens = AnixThemeTokens.dimens

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        episodes.forEach { episode ->
            EpisodeCell(
                episode = episode,
                isCurrent = currentPosition == episode.position,
                isFiller = isFiller(episode),
                cellMinSize = cellMinSize,
                onClick = { onEpisodeClick(episode) },
                onLongClick = onEpisodeLongClick?.let { callback -> { callback(episode) } },
            )
        }
    }
}

// Приватная функция, зеркалит параметры публичного EpisodeGrid (см. @Suppress выше по файлу)
// плюс onLongClick, добавленный аддитивно в Фазе 7.
@Suppress("LongParameterList")
@Composable
private fun EpisodeCell(
    episode: Episode,
    isCurrent: Boolean,
    isFiller: Boolean,
    cellMinSize: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(dimens.cornerS)
    val alpha = if (isFiller) FILLER_ALPHA else 1f

    val containerColor =
        if (episode.isWatched) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    val contentColor =
        if (episode.isWatched) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    val contentDescription =
        if (episode.isWatched) {
            strings.episodeWatchedContentDescription
        } else {
            strings.episodeUnwatchedContentDescription
        }

    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = cellMinSize, minHeight = cellMinSize)
                .clip(shape)
                .then(
                    if (isCurrent) {
                        Modifier.border(BorderStroke(CURRENT_BORDER_WIDTH, MaterialTheme.colorScheme.primary), shape)
                    } else {
                        Modifier
                    },
                ).background(containerColor.copy(alpha = alpha), shape)
                .semantics { this.contentDescription = contentDescription }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = episode.position.toString(),
            color = contentColor.copy(alpha = alpha),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val CURRENT_BORDER_WIDTH = 2.dp
private const val FILLER_ALPHA = 0.55f
