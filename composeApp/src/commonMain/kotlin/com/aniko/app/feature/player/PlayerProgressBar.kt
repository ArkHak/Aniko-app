package com.aniko.app.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Прогресс-бар плеера с перемоткой drag'ом и тапом (часть P8.T3).
 *
 * Своя отрисовка вместо M3 `Slider` (2026-10-01, жалоба «некрасиво и несовременно»): у M3 Expressive
 * ползунок — вертикальная палка с зазором в дорожке и точкой-стопом на конце, у медиаприложений
 * (YouTube/Netflix) — тонкая дорожка и круглый ползунок, который растёт под пальцем. Оба жеста
 * (тап в точку и перетаскивание) и семантика доступности (`progressBarRangeInfo` + `setProgress`)
 * сделаны здесь же.
 *
 * `durationMs == null` — длительность ещё неизвестна (до `loadedmetadata`, см. KDoc
 * `EmbedVideoState.durationMs`): полоса рисуется неактивной с «--:--», но МЕСТО занимает сразу —
 * иначе она появлялась бы через секунду после старта, и весь блок под видео прыгал.
 *
 * Локальные `scrubMs`/`pendingSeekMs` нужны из-за асинхронности моста: `seekTo` — команда без
 * ответа, реальная позиция приедет отдельным DOM-событием через сотни миллисекунд. Если сбросить
 * ползунок сразу после отпускания, он на пару кадров прыгнет назад, на старую позицию. Поэтому
 * держим запрошенную позицию, пока состояние не подтвердит её с точностью [SEEK_SETTLE_MS].
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // Отрисовка дорожки + два жеста + семантика —
// одна неделимая полоса; ветвления — это `enabled`/`active` для размеров и цветов, не логика.
@Composable
internal fun PlayerProgressBar(
    currentTimeMs: Long,
    durationMs: Long?,
    onSeek: (Long) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val enabled = durationMs != null
    val total = (durationMs ?: 0L).coerceAtLeast(1L)
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentTotal by rememberUpdatedState(total)

    LaunchedEffect(currentTimeMs, pendingSeekMs) {
        val pending = pendingSeekMs ?: return@LaunchedEffect
        // `Long.absoluteValue` не тянем ради одного сравнения — диапазон и так симметричен.
        val settled = (currentTimeMs - pending) in -SEEK_SETTLE_MS..SEEK_SETTLE_MS
        if (settled) pendingSeekMs = null
    }

    fun commit(targetMs: Long) {
        pendingSeekMs = targetMs
        currentOnSeek(targetMs)
    }

    val shownMs = (scrubMs ?: pendingSeekMs ?: currentTimeMs).coerceIn(0L, total)
    val fraction = if (enabled) shownMs.toFloat() / total else 0f
    val active = scrubMs != null
    val trackHeight by animateDpAsState(if (active) TRACK_HEIGHT_ACTIVE else TRACK_HEIGHT, label = "trackHeight")
    val thumbSize by animateDpAsState(if (active) THUMB_SIZE_ACTIVE else THUMB_SIZE, label = "thumbSize")
    val playedColor = MaterialTheme.colorScheme.primary
    val restColor = OVERLAY_CONTENT_COLOR.copy(alpha = if (enabled) INACTIVE_TRACK_ALPHA else DISABLED_TRACK_ALPHA)

    Column {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TOUCH_HEIGHT)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        if (enabled) {
                            setProgress { value ->
                                commit((value.coerceIn(0f, 1f) * currentTotal).toLong())
                                true
                            }
                        } else {
                            disabled()
                        }
                    }.then(
                        if (!enabled) {
                            Modifier
                        } else {
                            Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        commit(positionToMs(offset.x, size.width, currentTotal))
                                    }
                                }.pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            scrubMs = positionToMs(offset.x, size.width, currentTotal)
                                        },
                                        onDragEnd = {
                                            scrubMs?.let(::commit)
                                            scrubMs = null
                                        },
                                        onDragCancel = { scrubMs = null },
                                    ) { change, _ ->
                                        change.consume()
                                        scrubMs = positionToMs(change.position.x, size.width, currentTotal)
                                    }
                                }
                        },
                    ),
        ) {
            val thumbRadius = thumbSize.toPx() / 2
            val trackPx = trackHeight.toPx()
            val left = thumbRadius
            val width = (size.width - thumbRadius * 2).coerceAtLeast(0f)
            val centerY = size.height / 2
            val corner = CornerRadius(trackPx / 2, trackPx / 2)
            drawRoundRect(
                color = restColor,
                topLeft = Offset(left, centerY - trackPx / 2),
                size = Size(width, trackPx),
                cornerRadius = corner,
            )
            if (enabled) {
                val playedWidth = width * fraction
                drawRoundRect(
                    color = playedColor,
                    topLeft = Offset(left, centerY - trackPx / 2),
                    size = Size(playedWidth, trackPx),
                    cornerRadius = corner,
                )
                drawCircle(color = playedColor, radius = thumbRadius, center = Offset(left + playedWidth, centerY))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceXs)) {
            Text(
                text = if (enabled) formatPlaybackTime(shownMs) else UNKNOWN_TIME,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                color = OVERLAY_CONTENT_COLOR,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = durationMs?.let(::formatPlaybackTime) ?: UNKNOWN_TIME,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                color = OVERLAY_CONTENT_COLOR.copy(alpha = TOTAL_TIME_ALPHA),
            )
        }
    }
}

/** X касания по полосе шириной [widthPx] → позиция в мс из [totalMs]. */
private fun positionToMs(
    x: Float,
    widthPx: Int,
    totalMs: Long,
): Long = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f).times(totalMs).toLong()

/** Моноширинные цифры: время не «дрожит» по ширине каждую секунду. */
private const val TABULAR_NUMBERS = "tnum"
private const val TOTAL_TIME_ALPHA = 0.7f

/** `65_000L` → `"01:05"`, часовые серии — `"1:02:03"`. */
internal fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis / MILLIS_IN_SECOND).coerceAtLeast(0L)
    val seconds = totalSeconds % SECONDS_IN_MINUTE
    val minutes = (totalSeconds / SECONDS_IN_MINUTE) % MINUTES_IN_HOUR
    val hours = totalSeconds / (SECONDS_IN_MINUTE * MINUTES_IN_HOUR)
    val mm = minutes.toString().padStart(TIME_FIELD_WIDTH, '0')
    val ss = seconds.toString().padStart(TIME_FIELD_WIDTH, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

/** Насколько близко подтверждённая позиция должна подойти к запрошенной, чтобы снять ползунок. */
private const val SEEK_SETTLE_MS = 1_500L

private const val INACTIVE_TRACK_ALPHA = 0.25f
private const val DISABLED_TRACK_ALPHA = 0.15f
private val TOUCH_HEIGHT = 28.dp
private val TRACK_HEIGHT = 3.dp
private val TRACK_HEIGHT_ACTIVE = 5.dp
private val THUMB_SIZE = 12.dp
private val THUMB_SIZE_ACTIVE = 18.dp

private const val MILLIS_IN_SECOND = 1_000L
private const val SECONDS_IN_MINUTE = 60L
private const val MINUTES_IN_HOUR = 60L
private const val TIME_FIELD_WIDTH = 2

/** Подпись времени, пока длительность неизвестна. */
private const val UNKNOWN_TIME = "--:--"
