package com.aniko.app.feature.player

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.player.EmbedVideoController

/**
 * Клавиатурные шорткаты плеера (P8.T7) — space/←→/↑↓, только Desktop.
 *
 * Раньше (P8.T1/P8.T7, "решение зафиксировано") пункт был закрыт как CUT: на Desktop видео играло
 * в системном браузере — не было ни видео-поверхности, ни адресата команд с клавиатуры. Теперь,
 * когда Desktop получил реальный JS-мост через JCEF (см. `EmbedVideoController.desktop.kt`),
 * пункт стал выполним — реализация здесь общая (сама обработка `KeyEvent` не платформенная, это
 * часть Compose Multiplatform), а [rememberIsKeyboardShortcutsPlatform] — единственный
 * expect/actual: он ограничивает шорткаты Desktop-ом (Android/iOS формально тоже могут получить
 * `KeyEvent` от внешней клавиатуры, но фокус-модель и UX там принципиально другие, и в объём этой
 * задачи не входят).
 *
 * Маппинг ↑/↓ на СКОРОСТЬ, а не громкость (бриф допускал оба варианта): у JS-моста
 * (`EmbedVideoBridge.kt`) в принципе нет команды `volume` — протокол моста переиспользуется как
 * есть (см. её KDoc, "не форкать"), а не расширяется под этот шорткат, поэтому ↑/↓ управляют
 * ЕДИНСТВЕННЫМ непрерывным числовым параметром, который мост реально поддерживает — скоростью
 * (те же 1.0–2.0 шаг 0.25, что и чипы P8.T5, [PLAYBACK_RATES]).
 *
 * `Modifier.focusable()` + разовый [FocusRequester.requestFocus] при входе — без явного фокуса
 * `onPreviewKeyEvent` в Compose Desktop не получает событий вовсе (клавиатурный фокус по
 * умолчанию не назначается никакому узлу дерева).
 */
@Composable
fun Modifier.playerKeyboardShortcuts(
    controller: EmbedVideoController,
    enabled: Boolean,
): Modifier {
    val isPlatformSupported = rememberIsKeyboardShortcutsPlatform()
    if (!enabled || !isPlatformSupported) return this

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val videoState by controller.state.collectAsStateWithLifecycle()

    return this
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event -> handlePlayerKeyEvent(event, controller, videoState.playbackRate) }
}

/** `true` — Compose получила это событие как «клавиша нажата» и обработала его. */
private fun handlePlayerKeyEvent(
    event: KeyEvent,
    controller: EmbedVideoController,
    currentRate: Float,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Spacebar -> {
            controller.togglePlayPause()
            true
        }

        Key.DirectionLeft -> {
            controller.seekBy(-PIP_SEEK_STEP_MS)
            true
        }

        Key.DirectionRight -> {
            controller.seekBy(PIP_SEEK_STEP_MS)
            true
        }

        Key.DirectionUp -> {
            nextPlaybackRate(currentRate, step = 1)?.let(controller::setPlaybackRate)
            true
        }

        Key.DirectionDown -> {
            nextPlaybackRate(currentRate, step = -1)?.let(controller::setPlaybackRate)
            true
        }

        else -> false
    }
}

/**
 * Следующее/предыдущее значение из [PLAYBACK_RATES] (те же 1.0–2.0 шаг 0.25, что и чипы P8.T5).
 * `null` на границах диапазона — шорткат молчит, а не заворачивает по кругу и не уходит за пределы
 * панели скорости (иначе `↑`/`↓` могли бы выставить скорость, для которой в UI нет чипа).
 */
internal fun nextPlaybackRate(
    current: Float,
    step: Int,
): Float? {
    val index = PLAYBACK_RATES.indexOfFirst { it == current }.takeIf { it >= 0 } ?: PLAYBACK_RATES.indexOf(1f)
    val nextIndex = index + step
    return PLAYBACK_RATES.getOrNull(nextIndex)
}

/** `true` — платформа, на которой шорткаты вообще имеет смысл вешать (см. KDoc [playerKeyboardShortcuts]). */
@Composable
expect fun rememberIsKeyboardShortcutsPlatform(): Boolean
