package com.aniko.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Индикатор старта/буферизации поверх видео-области — общий для compact и fullscreen.
 *
 * Медиаприложения никогда не показывают «пустой чёрный кадр»: пока серия запускается (страница хоста
 * грузится, мост ещё не нашёл `<video>`) или поток ждёт данных ([isBuffering]), в центре крутится
 * индикатор. Свой спиннер хоста скрыт CSS моста — двух разных спиннеров не бывает.
 *
 * Слой касаний не перехватывает — жесты и кнопки под ним работают.
 */
@Composable
internal fun PlayerStartIndicator(
    isStarting: Boolean,
    isBuffering: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isStarting || isBuffering) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .size(START_SPINNER_SIZE)
                        .semantics { contentDescription = strings.playerLoading },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = START_TRACK_ALPHA),
                strokeWidth = START_SPINNER_STROKE,
            )
        }
    }
}

private val START_SPINNER_SIZE = 48.dp
private val START_SPINNER_STROKE = 4.dp
private const val START_TRACK_ALPHA = 0.15f

/**
 * Источник не загрузился ([com.aniko.player.PlaybackEngineProblem.SourceUnavailable]) или видео так
 * и не появилось за время автозапуска: своё сообщение на сплошном фоне (сырую страницу ошибки хоста
 * — «403 Forbidden» — пользователь не видит) с «Другой источник» (та же озвучка, другой хост), если
 * он есть, иначе — «Повторить» и, если есть из чего выбирать, «Сменить озвучку».
 */
@Composable
internal fun PlayerSourceFailedState(
    onRetry: () -> Unit,
    onAlternativeSource: (() -> Unit)?,
    onChangeVoice: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = FAILED_MAX_WIDTH).padding(horizontal = dimens.spaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            Text(
                text = strings.playerSourceFailedTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = strings.playerSourceFailedHint,
                color = Color.White.copy(alpha = FAILED_HINT_ALPHA),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            // Главное действие — «Другой источник», если он есть (чаще всего именно он и спасает:
            // 403 у одного хоста не значит 403 у другого); иначе — «Повторить».
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                if (onAlternativeSource != null) {
                    Button(onClick = onAlternativeSource) { Text(strings.playerOtherSource) }
                    OutlinedButton(onClick = onRetry) { Text(strings.commonRetry, color = Color.White) }
                } else {
                    Button(onClick = onRetry) { Text(strings.commonRetry) }
                    if (onChangeVoice != null) {
                        OutlinedButton(onClick = onChangeVoice) { Text(strings.playerChangeVoice, color = Color.White) }
                    }
                }
            }
        }
    }
}

private val FAILED_MAX_WIDTH = 420.dp
private const val FAILED_HINT_ALPHA = 0.7f
