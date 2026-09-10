package com.aniko.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlin.math.roundToInt

/** Что именно крутит текущий вертикальный жест. */
internal enum class PlayerLevelTarget { BRIGHTNESS, VOLUME }

/**
 * Состояние жеста уровней: модификатор-обработчик и то, что рисует индикатор.
 *
 * Отдельный тип нужен из-за того, что обработчик жеста обязан висеть на ТОМ ЖЕ узле, что и
 * тап-слой fullscreen-оверлея (`detectTapGestures`): два соседних pointer-узла не делят события —
 * верхний забирает их целиком, и либо тапы перестали бы показывать контролы, либо жест уровней
 * не работал бы. Поэтому оверлей складывает оба обработчика в один `Modifier`, а состояние
 * (какая сторона тянется и насколько) вынесено сюда.
 */
@Stable
internal class PlayerLevelGestureState(
    val target: PlayerLevelTarget?,
    val level: Float,
    val modifier: Modifier,
)

/**
 * Вертикальный жест по половинам кадра: слева — яркость, справа — громкость (P16.T9).
 *
 * Разделение по половинам, а не по «узким краям», как в описании задачи: у краевых полос
 * попасть пальцем сложнее, а конфликтов у вертикального жеста в оверлее нет вовсе (горизонталь
 * занята только свайпом прогресс-бара внутри своей панели, тапы — соседним обработчиком).
 *
 * `levels.isSupported == false` (iOS/Desktop) — обработчик не вешается совсем: без платформенной
 * реализации жест был бы безмолвным (палец едет, уровень стоит).
 */
@Composable
internal fun rememberPlayerLevelGesture(levels: PlayerSystemLevels): PlayerLevelGestureState {
    if (!levels.isSupported) {
        return remember { PlayerLevelGestureState(target = null, level = 0f, modifier = Modifier) }
    }
    var target by remember { mutableStateOf<PlayerLevelTarget?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    val modifier =
        Modifier.pointerInput(levels) {
            // `active`/`current` — обычные переменные внутри корутины жеста, а не snapshot-состояние:
            // они нужны только обработчику (компоновке не видны) и не должны вызывать пересборку
            // на каждом кадре драга. Наружу отдаётся только то, что рисует индикатор.
            var active: PlayerLevelTarget? = null
            var current = 0f
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    active =
                        if (offset.x < size.width * PLAYER_LEVEL_SPLIT_FRACTION) {
                            PlayerLevelTarget.BRIGHTNESS
                        } else {
                            PlayerLevelTarget.VOLUME
                        }
                    current =
                        when (active) {
                            PlayerLevelTarget.BRIGHTNESS -> levels.brightness()
                            else -> levels.volume()
                        }
                    target = active
                    level = current
                },
                onDragEnd = {
                    active = null
                    target = null
                },
                onDragCancel = {
                    active = null
                    target = null
                },
                onVerticalDrag = { change, dragAmount ->
                    val which = active ?: return@detectVerticalDragGestures
                    // Потребляем движение: иначе тап-обработчик на том же узле увидит «тап» после
                    // драга и переключит видимость контролов ровно в конце жеста.
                    change.consume()
                    current = playerLevelAfterDrag(current, dragAmount, size.height.toFloat())
                    level = current
                    when (which) {
                        PlayerLevelTarget.BRIGHTNESS -> levels.setBrightness(current)
                        PlayerLevelTarget.VOLUME -> levels.setVolume(current)
                    }
                },
            )
        }
    return PlayerLevelGestureState(target = target, level = level, modifier = modifier)
}

/**
 * Индикатор уровня поверх видео: подпись, полоса и проценты (P16.T9).
 *
 * Полоса, а не иконка: в bundled icon-subset `:shared:ui` (31 глиф) нет ни `brightness_*`, ни
 * `volume_*`, а расширять subset ради двух знаков — отдельная операция над шрифтом; полоса+проценты
 * однозначны и не зависят от шрифта.
 */
@Composable
internal fun PlayerLevelIndicator(
    target: PlayerLevelTarget,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = AnixThemeTokens.colors
    val dimens = AnixThemeTokens.dimens
    val label =
        when (target) {
            PlayerLevelTarget.BRIGHTNESS -> strings.playerBrightnessLabel
            PlayerLevelTarget.VOLUME -> strings.playerVolumeLabel
        }
    val percent = (level * PERCENT_MAX).roundToInt()
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(dimens.corner16))
                .background(colors.posterScrim)
                .padding(horizontal = dimens.spaceM, vertical = dimens.space12)
                .clearAndSetSemantics { contentDescription = "$label $percent%" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Box(
            modifier =
                Modifier
                    .width(LEVEL_INDICATOR_BAR_WIDTH)
                    .height(LEVEL_INDICATOR_BAR_HEIGHT)
                    .clip(RoundedCornerShape(dimens.cornerPill))
                    .background(Color.White.copy(alpha = LEVEL_INDICATOR_TRACK_ALPHA)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(level)
                        .fillMaxHeight()
                        .background(Color.White),
            )
        }
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * Граница половин кадра: `0.5` — ровно середина (см. KDoc [rememberPlayerLevelGesture]).
 *
 * Именно множитель: `size.width / 0.5f` — это `2 * width`, то есть условие «левая половина» стало
 * бы всегда истинным и громкость не работала бы вовсе (поймано живой проверкой: оба свайпа
 * показывали «Яркость»).
 */
private const val PLAYER_LEVEL_SPLIT_FRACTION = 0.5f

private const val PERCENT_MAX = 100

private val LEVEL_INDICATOR_BAR_WIDTH = 96.dp

private val LEVEL_INDICATOR_BAR_HEIGHT = 4.dp

private const val LEVEL_INDICATOR_TRACK_ALPHA = 0.3f
