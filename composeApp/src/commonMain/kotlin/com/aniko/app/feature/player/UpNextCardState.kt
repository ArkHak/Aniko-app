package com.aniko.app.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.player.END_OF_EPISODE_THRESHOLD_MS
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Данные карточки «Следующая серия» (автопереход в конце серии, как у Netflix/Crunchyroll).
 *
 * Видимость и сам автопереход решает [PlayerScreen] — ОДИН раз на оба режима (compact/fullscreen):
 * флаг «Отмена» живёт там же, иначе переключение режима на последних секундах воскрешало бы
 * отменённую карточку, а оба режима могли бы навигировать параллельно. Здесь — только отрисовка.
 *
 * @param episodeLabel человеческий номер следующей серии (`Episode.displayNumber()`).
 * @param secondsLeft секунды до конца текущей серии — из позиции видео, не из своего таймера:
 * отсчёт не разъезжается с видео на паузе/перемотке/смене скорости.
 */
data class UpNextCardState(
    val episodeLabel: String,
    val secondsLeft: Int,
    val onPlayNow: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * Карточка «Следующая серия · Серия N» с кольцом обратного отсчёта и действиями «Отмена» /
 * «Смотреть». Фон фиксированно тёмный (поверх кадра видео независимо от темы — тот же принцип,
 * что у [OVERLAY_CONTENT_COLOR]). [compact] — уменьшенный вариант для 16:9-области компактного
 * режима, где по высоте помещается меньше.
 */
@Suppress("LongMethod") // Линейная раскладка одной карточки (кольцо + подпись + две кнопки).
@Composable
internal fun UpNextCard(
    state: UpNextCardState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val totalSeconds = (END_OF_EPISODE_THRESHOLD_MS / MILLIS_PER_SECOND).toFloat()
    val progress by animateFloatAsState(
        targetValue = (state.secondsLeft / totalSeconds).coerceIn(0f, 1f),
        label = "upNextCountdown",
    )
    val ringSize = if (compact) UP_NEXT_RING_SIZE_COMPACT else UP_NEXT_RING_SIZE
    Surface(
        modifier = modifier.widthIn(max = UP_NEXT_MAX_WIDTH),
        shape = RoundedCornerShape(dimens.cornerM),
        color = UP_NEXT_BACKGROUND,
        contentColor = OVERLAY_CONTENT_COLOR,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = dimens.space12,
                    end = dimens.spaceXs,
                    top = dimens.spaceXs,
                    bottom = dimens.spaceXs,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(ringSize)
                        .semantics { contentDescription = strings.playerNextEpisodeIn(state.secondsLeft) },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(ringSize),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = OVERLAY_CONTENT_COLOR.copy(alpha = UP_NEXT_TRACK_ALPHA),
                    strokeWidth = UP_NEXT_RING_STROKE,
                )
                Text(text = state.secondsLeft.toString(), fontSize = UP_NEXT_RING_FONT, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = strings.playerNextEpisode,
                    fontSize = UP_NEXT_CAPTION_FONT,
                    color = OVERLAY_CONTENT_COLOR.copy(alpha = UP_NEXT_CAPTION_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = strings.playerEpisodeChip(state.episodeLabel),
                    fontSize = UP_NEXT_TITLE_FONT,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = state.onCancel,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(text = strings.playerCancel, color = OVERLAY_CONTENT_COLOR, fontSize = UP_NEXT_ACTION_FONT)
            }
            TextButton(
                onClick = state.onPlayNow,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                AnixIcon(
                    name = "play_arrow",
                    contentDescription = null,
                    filled = true,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(UP_NEXT_ACTION_ICON),
                )
                Text(
                    text = strings.playerNextEpisodeNow,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = UP_NEXT_ACTION_FONT,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val MILLIS_PER_SECOND = 1_000L

@Suppress("MagicNumber") // hex-литерал цвета — содержательная константа (см. `AnixPalette`).
private val UP_NEXT_BACKGROUND = Color(0xFF0F1016).copy(alpha = 0.92f)
private const val UP_NEXT_TRACK_ALPHA = 0.2f
private const val UP_NEXT_CAPTION_ALPHA = 0.7f
private val UP_NEXT_MAX_WIDTH = 420.dp
private val UP_NEXT_RING_SIZE = 40.dp
private val UP_NEXT_RING_SIZE_COMPACT = 32.dp
private val UP_NEXT_RING_STROKE = 3.dp
private val UP_NEXT_ACTION_ICON = 18.dp
private val UP_NEXT_RING_FONT = 12.sp
private val UP_NEXT_CAPTION_FONT = 11.sp
private val UP_NEXT_TITLE_FONT = 14.sp
private val UP_NEXT_ACTION_FONT = 13.sp
