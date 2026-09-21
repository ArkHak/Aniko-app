package com.aniko.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.aniko.player.EmbedVideoState
import com.aniko.player.QualitySwitchFailure
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.delay

/**
 * Индикатор смены качества и уведомление о сбое поверх кадра видео (Desktop; на Android/iOS
 * `switchingQualityTo`/`qualitySwitchFailure` всегда `null` — там качество меняет сама страница
 * хоста, и композабл ничего не рисует).
 *
 * - идёт смена ([EmbedVideoState.switchingQualityTo]) — компактная «пилюля» со спиннером и
 *   «Переключаем качество: 480p»: пользователь видит, что приложение работает, а не зависло, пока
 *   libVLC перезапускает поток с той же секунды;
 * - смена не удалась ([EmbedVideoState.qualitySwitchFailure]) — «Не удалось переключиться на 480p —
 *   снова 720p» на [FAILURE_NOTICE_MS] (ровно один раз на [QualitySwitchFailure.id]). Если откат тоже
 *   не удался (`restoredTo == null`, плеер без картинки) — сообщение остаётся, пока пользователь не
 *   выберет другое качество или не уйдёт с экрана.
 *
 * Пилюля стоит сверху по центру области видео: центр занят кнопками play/pause оверлея, низ —
 * прогресс-баром. Текст белый на `black @ 75%` — контраст ≥ AA на любом кадре. Помечена как
 * live region (озвучивается без фокуса), не интерактивна (тач-таргет не нужен).
 *
 * @param modifier размеры области видео (тот же `height`/`offset`, что у `EmbedPlayerView`).
 */
@Composable
internal fun PlayerQualitySwitchStatus(
    state: EmbedVideoState,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val failure = state.qualitySwitchFailure
    var dismissedFailureId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(failure?.id) {
        // Постоянное сообщение (откат не удался) не гасим: без него мёртвый плеер молчал бы без причины.
        if (failure != null && failure.restoredTo != null) {
            delay(FAILURE_NOTICE_MS)
            dismissedFailureId = failure.id
        }
    }
    val switchingTo = state.switchingQualityTo
    val visibleFailure = failure?.takeIf { it.id != dismissedFailureId }
    val message =
        when {
            switchingTo != null -> strings.playerQualitySwitching(switchingTo)
            visibleFailure != null -> visibleFailure.toMessage(strings)
            else -> null
        }
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        if (message != null) {
            StatusPill(text = message, showSpinner = switchingTo != null)
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    showSpinner: Boolean,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier =
            Modifier
                .padding(top = dimens.space12)
                .background(Color.Black.copy(alpha = PILL_BACKGROUND_ALPHA), RoundedCornerShape(dimens.cornerPill))
                .padding(horizontal = dimens.space12, vertical = dimens.spaceS)
                .testTag(AnixTestTags.PLAYER_QUALITY_SWITCH_STATUS)
                // Спиннер и текст — один озвучиваемый узел, иначе читалка объявит «индикатор прогресса» отдельно.
                .clearAndSetSemantics {
                    contentDescription = text
                    liveRegion = LiveRegionMode.Polite
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(SPINNER_SIZE),
                color = Color.White,
                strokeWidth = SPINNER_STROKE,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(start = if (showSpinner) dimens.spaceS else 0.dp),
        )
    }
}

private fun QualitySwitchFailure.toMessage(strings: Strings): String {
    val restored = restoredTo
    return if (restored != null) {
        strings.playerQualitySwitchRestored(requested, restored)
    } else {
        strings.playerQualitySwitchFailed(requested)
    }
}

/** Сколько висит уведомление об откате качества. */
private const val FAILURE_NOTICE_MS = 4_000L

private const val PILL_BACKGROUND_ALPHA = 0.75f
private val SPINNER_SIZE = 18.dp
private val SPINNER_STROKE = 2.dp
