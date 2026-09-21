package com.aniko.app.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.delay

/**
 * Мигающий индикатор «−10с/+10с» после double-tap-жеста (жалоба/задача «удобный плеер»,
 * 2026-09-08, P2): общий для компактного ([CompactPlayerLayout]) и полноэкранного
 * ([PlayerOverlay]) режимов — одна визуальная конвенция «перемотка жестом» на обе раскладки,
 * без второй копии стиля.
 *
 * Сам жест детектится в конкретных раскладках (у каждой своя семантика одиночного тапа: в
 * fullscreen он прячет/показывает контролы, в compact — play/pause), этот файл отвечает только
 * за отклик: короткий полупрозрачный круг с иконкой `replay_10`/`forward_10` у края, куда
 * «перемотали»; fade-in 120мс, задержка 300мс, fade-out 180мс — заметно короче авто-скрытия
 * контролов (4с), чтобы не конкурировать с ним за внимание.
 *
 * Доступность: жест — дополнение к существующим кнопкам −10/+10 (у них есть
 * `contentDescription`), скринридеры получают тот же функционал кнопками — см. KDoc
 * [com.aniko.app.feature.player.PlayerOverlay] про центральную тап-зону.
 */
internal enum class PlayerSeekDirection { BACK, FORWARD }

/**
 * Состояние флеша на время жизни раскладки. `Pair<направление, серийный номер>` вместо
 * простого nullable-направления: два double-tap ПОДРЯД в одну сторону обязаны мигать заново
 * (серийный номер меняет ключ [LaunchedEffect] в [PlayerSeekFlashOverlay]), а `remember`-флаг
 * без ключа на повторное срабатывание не отреагировал бы — состояние уже `true`.
 */
internal class PlayerSeekFlashState {
    internal var flash: Pair<PlayerSeekDirection, Int>? by mutableStateOf(null)
        private set

    internal fun fire(direction: PlayerSeekDirection) {
        flash = direction to (flash?.second?.plus(1) ?: 0)
    }

    internal fun consume() {
        flash = null
    }
}

@Composable
internal fun rememberPlayerSeekFlash(): PlayerSeekFlashState = remember { PlayerSeekFlashState() }

/**
 * Сам индикатор. Вызывать внутри `BoxScope` поверх видео-области; позиция фиксированная по
 * направлению перемотки (BACK — левый край, FORWARD — правый), чтобы и у compact, и у fullscreen
 * был ровно ОДИН вызов и одна точка отрисовки (два вызова с одним [PlayerSeekFlashState] дали бы
 * две окружности на любой перемотке). Рисует только пока `state.flash != null`, по завершении fade-out гасит себя через
 * [PlayerSeekFlashState.consume].
 */
@Suppress("MagicNumber") // Времена fade-фаз анимации — содержательные константы ниже (см. их KDoc).
@Composable
internal fun BoxScope.PlayerSeekFlashOverlay(state: PlayerSeekFlashState) {
    val flash = state.flash ?: return
    val (direction, serial) = flash
    val dimens = AnixThemeTokens.dimens
    val isBack = direction == PlayerSeekDirection.BACK
    val alpha = remember(serial) { Animatable(0f) }
    LaunchedEffect(serial) {
        alpha.animateTo(1f, tween(SEEK_FLASH_FADE_IN_MS))
        delay(SEEK_FLASH_HOLD_MS.toLong())
        alpha.animateTo(0f, tween(SEEK_FLASH_FADE_OUT_MS))
        state.consume()
    }
    // Декоративный отклик жеста (без contentDescription); функционал — у кнопок ±10 (см. KDoc файла).
    // Круг и центровка иконки в нём — общий [PlayerIconCircle].
    PlayerIconCircle(
        iconName = if (isBack) "replay_10" else "forward_10",
        diameter = SEEK_FLASH_CIRCLE_DP,
        iconSize = SEEK_FLASH_ICON_DP,
        background = SEEK_FLASH_SCRIM,
        filled = false,
        modifier =
            Modifier
                .align(if (isBack) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(
                    start = if (isBack) dimens.spaceM else 0.dp,
                    end = if (isBack) 0.dp else dimens.spaceM,
                ).alpha(alpha.value),
    )
}

/** Радиус плашки — тот же визуальный язык, что у центральной play/pause (52dp) и топбар-кнопок. */
private val SEEK_FLASH_CIRCLE_DP = 64.dp
private val SEEK_FLASH_ICON_DP = 40.dp

@Suppress("MagicNumber") // hex-литерал цвета — то же обоснование, что и у `NEXT_EPISODE_BANNER_COLOR`
// в PlayerOverlay.kt: сам hex и есть содержательная константа.
private val SEEK_FLASH_SCRIM = Color(0x73000000)

private const val SEEK_FLASH_FADE_IN_MS = 120
private const val SEEK_FLASH_HOLD_MS = 300
private const val SEEK_FLASH_FADE_OUT_MS = 180

/**
 * Единственный источник шага перемотки ±10с в плеере (2026-09-08, P2): и double-tap-жест
 * ([PlayerOverlay]/[CompactPlayerLayout]), и центральные кнопки −10/+10 (PlayerCenterControls)
 * перематывают на один и тот же шаг. Раньше константа жила приватно в [PlayerOverlay], вторая
 * копия появилась в [CompactPlayerLayout] — два независимых «10 секунд» в одном пакете расходятся
 * при любой будущей правке шага.
 */
internal const val PLAYER_SEEK_STEP_MS = 10_000L
