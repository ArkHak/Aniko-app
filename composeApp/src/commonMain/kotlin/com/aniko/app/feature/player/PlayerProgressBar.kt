package com.aniko.app.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Прогресс-бар плеера с перемоткой drag'ом и тапом (часть P8.T3) — вынесен из `PlayerOverlay.kt`
 * отдельным файлом (detekt `TooManyFunctions`).
 *
 * [Slider] выбран вместо самописной полосы на `pointerInput` потому, что даёт сразу оба жеста
 * (перетаскивание и тап в произвольную точку) плюс корректную семантику доступности.
 *
 * Вызывается только когда длительность уже известна: до события `loadedmetadata` её не
 * существует (`duration = NaN`, см. KDoc `EmbedVideoState.durationMs`), и шкала «от нуля до
 * неизвестно чего» была бы выдумкой. Проверка живёт на стороне вызывающего.
 *
 * Локальные `scrubMs`/`pendingSeekMs` нужны из-за асинхронности моста: `seekTo` — команда без
 * ответа, реальная позиция приедет отдельным DOM-событием через сотни миллисекунд. Если сбросить
 * ползунок сразу после отпускания, он на пару кадров прыгнет назад, на старую позицию. Поэтому
 * держим запрошенную позицию, пока состояние не подтвердит её с точностью [SEEK_SETTLE_MS].
 */
@Composable
internal fun PlayerProgressBar(
    currentTimeMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentTimeMs, pendingSeekMs) {
        val pending = pendingSeekMs ?: return@LaunchedEffect
        // `Long.absoluteValue` не тянем ради одного сравнения — диапазон и так симметричен.
        val settled = (currentTimeMs - pending) in -SEEK_SETTLE_MS..SEEK_SETTLE_MS
        if (settled) pendingSeekMs = null
    }

    val shownMs = scrubMs ?: pendingSeekMs ?: currentTimeMs
    Column {
        Slider(
            value = shownMs.coerceIn(0L, durationMs).toFloat(),
            valueRange = 0f..durationMs.toFloat(),
            onValueChange = { scrubMs = it.toLong() },
            onValueChangeFinished = {
                val target = scrubMs
                scrubMs = null
                if (target != null) {
                    pendingSeekMs = target
                    onSeek(target)
                }
            },
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = OVERLAY_CONTENT_COLOR.copy(alpha = INACTIVE_TRACK_ALPHA),
                ),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceXs)) {
            Text(
                text = formatPlaybackTime(shownMs),
                style = MaterialTheme.typography.labelSmall,
                color = OVERLAY_CONTENT_COLOR,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatPlaybackTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = OVERLAY_CONTENT_COLOR,
            )
        }
    }
}

/**
 * `mm:ss`, а для длинного контента `h:mm:ss`. Своя реализация, потому что в commonMain нет
 * `String.format` (JVM-only) — та же причина, по которой форматируемые строки в
 * `com.aniko.ui.i18n.Strings` сделаны лямбдами, см. её KDoc.
 */
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

private const val INACTIVE_TRACK_ALPHA = 0.3f

private const val MILLIS_IN_SECOND = 1_000L
private const val SECONDS_IN_MINUTE = 60L
private const val MINUTES_IN_HOUR = 60L
private const val TIME_FIELD_WIDTH = 2
