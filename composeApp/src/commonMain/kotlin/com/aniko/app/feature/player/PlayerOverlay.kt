package com.aniko.app.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.player.EmbedVideoController
import com.aniko.player.EmbedVideoState
import com.aniko.player.isEpisodeFinished
import com.aniko.player.isNearEnd
import com.aniko.player.secondsToEpisodeEnd
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.delay

/**
 * Оверлей плеера поверх кадра embed-страницы: P8.T3 (назад, PiP, тап-зона play/pause,
 * прогресс-бар с seek), P8.T4 (баннер «следующая серия через Nс») и P8.T5 (скорость 1.0–2.0).
 *
 * **Живёт в `composeApp`, а не в `:shared:player`, намеренно.** Оверлею нужны и дизайн-токены
 * ([AnixThemeTokens]), и локализация ([LocalStrings]) — то есть `:shared:ui`, а `:shared:player`
 * на него не зависит с Фазы 2 (см. KDoc `EmbedPlayer.desktop.kt`: заглушка там английская
 * буквально по этой причине). `EmbedPlayerView` остаётся «голым» видео-контейнером,
 * а весь визуальный слой — здесь.
 *
 * **Вызывать только при `EmbedVideoController.isSupported == true`.** На Desktop контроллер
 * ничего не знает и ничем не управляет (видео играет в системном браузере, P8.T1), а нарисованный
 * там прогресс-бар был бы враньём — см. [PlayerDesktopControls], чем Desktop заменяет этот экран.
 *
 * Источник истины — только [state]. `play()`/`pause()` — односторонние команды в JS без
 * подтверждения, поэтому ни одна кнопка здесь не переключает локальный флаг «играет»: она шлёт
 * команду и ждёт, пока реальное DOM-событие видео вернётся через мост.
 *
 * **Про перехват касаний.** Пока мост нашёл `<video>` (`state.isVideoFound`), прозрачный слой
 * поверх кадра забирает все тапы себе — иначе тап одновременно и переключал бы наши контролы,
 * и жал бы на собственные кнопки чужого плеера под ними. Это осознанный размен: свои контролы
 * вместо чужих. Как только мост видео НЕ нашёл (хост с нестандартной вёрсткой, ещё не
 * загрузившаяся страница), слой снимается целиком и страница снова управляется своими средствами
 * — оверлей не имеет права запереть пользователя в кадре, которым не умеет управлять. В этом
 * состоянии из оверлея остаётся только кнопка «назад», причём постоянно видимая: на iOS другого
 * способа уйти с экрана нет.
 *
 * @param onEpisodeNearEnd вызывается, когда серия подходит к концу — сюда подвешена авто-отметка
 * «просмотрено» (P8.T8). Порог — общий с баннером ([isNearEnd] из `:shared:player`), сознательно
 * один и тот же на обе фичи.
 * @param onNextEpisode переход на следующую серию: и по кнопке «Смотреть сейчас», и по истечении
 * обратного отсчёта. Вызывается не чаще одного раза за жизнь этого экрана.
 */
@Suppress("LongParameterList") // Состояние + контроллер + флаг наличия следующей серии + 3 колбэка
// наружу (назад/следующая/конец серии) + modifier. Дробить оверлей на части с меньшим числом
// параметров пришлось бы через общий mutable-объект состояния — это хуже, чем счётчик.
@Composable
fun PlayerOverlay(
    state: EmbedVideoState,
    controller: EmbedVideoController,
    hasNextEpisode: Boolean,
    onBack: () -> Unit,
    onNextEpisode: () -> Unit,
    onEpisodeNearEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AnixThemeTokens.colors
    var controlsVisible by remember { mutableStateOf(true) }
    // Счётчик «пользователь что-то нажал» — перезапускает таймер авто-скрытия, не меняя
    // видимость. Именно счётчик, а не timestamp: ключ `LaunchedEffect` должен меняться на
    // каждое взаимодействие, даже если два подряд пришли в одну миллисекунду.
    var interactionTick by remember { mutableIntStateOf(0) }

    // Мост реально держит видео. Пока нет — управлять нечем, и оверлей обязан деградировать
    // до одной кнопки «назад», не перехватывая касания (см. KDoc, абзац про перехват).
    val bridgeActive = state.isVideoFound
    val controlsShown = controlsVisible && bridgeActive

    // Авто-скрытие только во время воспроизведения: на паузе контролы обязаны оставаться —
    // иначе кнопка play исчезает ровно тогда, когда она единственная нужная на экране.
    LaunchedEffect(controlsShown, interactionTick, state.isPlaying) {
        if (controlsShown && state.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    val nearEnd = state.isNearEnd()
    LaunchedEffect(nearEnd) {
        if (nearEnd) onEpisodeNearEnd()
    }

    Box(modifier = modifier.fillMaxSize()) {
        val scrimAlpha by animateFloatAsState(if (controlsShown) 1f else 0f, label = "playerScrim")
        if (bridgeActive) {
            // Отдельный слой-перехватчик: тап по любому месту кадра показывает/прячет контролы.
            // `indication = null` — рябь на весь экран поверх видео выглядела бы как дефект.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colors.posterScrim.copy(alpha = colors.posterScrim.alpha * scrimAlpha))
                        .clickableNoIndication {
                            controlsVisible = !controlsVisible
                            interactionTick++
                        },
            )
        }

        // Колонка сама по себе не перехватывает касания (у неё нет pointer-модификаторов), поэтому
        // тапы мимо кнопок проваливаются в слой-перехватчик выше и продолжают прятать контролы.
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // `|| !bridgeActive` — когда управлять нечем, «назад» висит постоянно: это
            // единственный выход с экрана на iOS.
            AnimatedVisibility(visible = controlsShown || !bridgeActive, enter = fadeIn(), exit = fadeOut()) {
                PlayerTopBar(onBack = onBack, onInteraction = { interactionTick++ })
            }

            PlayerCenterArea(
                visible = controlsShown,
                state = state,
                controller = controller,
                onInteraction = { interactionTick++ },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            NextEpisodeBanner(
                state = state,
                hasNextEpisode = hasNextEpisode,
                onNextEpisode = onNextEpisode,
            )

            AnimatedVisibility(visible = controlsShown, enter = fadeIn(), exit = fadeOut()) {
                PlayerBottomPanel(
                    state = state,
                    controller = controller,
                    onInteraction = { interactionTick++ },
                )
            }
        }
    }
}

/** Кнопка «назад» + PiP-заглушка (P8.T3). */
@Composable
private fun PlayerTopBar(
    onBack: () -> Unit,
    onInteraction: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = strings.backContentDescription,
            onClick = {
                onInteraction()
                onBack()
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        OverlayIconButton(
            icon = Icons.Outlined.PictureInPictureAlt,
            contentDescription = strings.playerPictureInPicture,
            // P8.T3 просит только разместить кнопку; сам режим «картинка в картинке» — это
            // P10.T8 (Фаза 10), где он и делается платформенно (Android PiP / iOS AVPictureIn-
            // PictureController). Держать здесь наполовину рабочую реализацию хуже, чем явную
            // заглушку: место в макете занято, обещание не дано.
            // TODO(P10.T8): подключить реальный PiP, когда появится платформенный API.
            onClick = onInteraction,
        )
    }
}

/**
 * Свободная середина кадра с центральной тап-зоной.
 *
 * Вынесена отдельной функцией не ради читаемости, а из-за разрешения перегрузок: внутри
 * `ColumnScope` компилятор выбирает `ColumnScope.AnimatedVisibility`, которая не вызывается с
 * неявным receiver'ом из вложенного `Box`. За границей функции `ColumnScope` больше не в области
 * видимости, и берётся обычная перегрузка.
 */
@Composable
private fun PlayerCenterArea(
    visible: Boolean,
    state: EmbedVideoState,
    controller: EmbedVideoController,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            PlayerCenterControls(
                isPlaying = state.isPlaying,
                controller = controller,
                onInteraction = onInteraction,
            )
        }
    }
}

/**
 * Центральная тап-зона: −10 с / play-pause / +10 с (P8.T3).
 *
 * Рисуется только когда мост держит `<video>` (см. `bridgeActive` в [PlayerOverlay]) — поэтому
 * отдельного «неактивного» состояния у кнопок нет: если команду некому исполнить, кнопок просто
 * не существует, а не они «нажимаются и ничего не делают».
 */
@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    controller: EmbedVideoController,
    onInteraction: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIconButton(
            icon = Icons.Filled.Replay10,
            contentDescription = strings.playerSeekBackward,
            onClick = {
                onInteraction()
                controller.seekBy(-SEEK_STEP_MS)
            },
        )
        OverlayIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) strings.playerPause else strings.playerPlay,
            iconSize = PLAY_BUTTON_ICON_SIZE,
            onClick = {
                onInteraction()
                controller.togglePlayPause()
            },
        )
        OverlayIconButton(
            icon = Icons.Filled.Forward10,
            contentDescription = strings.playerSeekForward,
            onClick = {
                onInteraction()
                controller.seekBy(SEEK_STEP_MS)
            },
        )
    }
}

/**
 * Нижняя панель: прогресс-бар с seek (P8.T3) + скорость воспроизведения (P8.T5).
 *
 * Прогресс-бар рисуется **только** при `durationMs != null` — до события `loadedmetadata`
 * длительности не существует вообще (`duration = NaN`), и шкала «от нуля до неизвестно чего»
 * была бы выдумкой. Пока её нет — панель показывает только скорость.
 *
 * Аудиодорожки/субтитров/качества здесь нет и не будет: это внутренний UI чужого embed-плеера
 * (CUT в таблице аудита `docs/REELWAVE_PLAN.md`), единого DOM-контракта под ним не существует.
 */
@Composable
private fun PlayerBottomPanel(
    state: EmbedVideoState,
    controller: EmbedVideoController,
    onInteraction: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        val duration = state.durationMs
        if (duration != null) {
            PlayerProgressBar(
                currentTimeMs = state.currentTimeMs,
                durationMs = duration,
                onSeek = { positionMs ->
                    onInteraction()
                    controller.seekTo(positionMs)
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            modifier = Modifier.selectableGroup(),
        ) {
            Text(
                text = strings.playerSpeedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = OVERLAY_CONTENT_COLOR,
            )
            PLAYBACK_RATES.forEach { rate ->
                FilterChip(
                    selected = state.playbackRate.matches(rate),
                    onClick = {
                        onInteraction()
                        controller.setPlaybackRate(rate)
                    },
                    label = { Text(strings.playerSpeedValue(rate.formatRate())) },
                )
            }
        }
    }
}

/**
 * P8.T4 — баннер «следующая серия через Nс» с отменой.
 *
 * Обратный отсчёт берётся из позиции воспроизведения ([secondsToEpisodeEnd]), а не из
 * собственного таймера: иначе он разъедется с видео на первой же паузе, перемотке или смене
 * скорости (требование плана — «триггер брать из `EmbedVideoController.state`»).
 *
 * Показывается независимо от видимости контролов: пользователь мог смотреть титры без оверлея,
 * и молча перескочить на следующую серию без единого шанса нажать «Отмена» было бы хуже всего.
 */
@Composable
private fun NextEpisodeBanner(
    state: EmbedVideoState,
    hasNextEpisode: Boolean,
    onNextEpisode: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    // Отмена живёт до конца этого экрана: серия одна, второй раз предлагать то же самое незачем.
    var cancelled by remember { mutableStateOf(false) }
    // Переход возможен ровно один раз: после навигации экран остаётся в back stack, и без флага
    // следующий же тик состояния попытался бы открыть ту же серию повторно.
    var navigated by remember { mutableStateOf(false) }

    val visible = hasNextEpisode && !cancelled && !navigated && state.isNearEnd()
    val finished = state.isEpisodeFinished()

    LaunchedEffect(visible, finished) {
        if (visible && finished) {
            navigated = true
            onNextEpisode()
        }
    }

    if (!visible) return
    val seconds = state.secondsToEpisodeEnd() ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceM, vertical = dimens.spaceS),
        shape = RoundedCornerShape(dimens.cornerM),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = dimens.spaceXs,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.space12, vertical = dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            Text(
                text = strings.playerNextEpisodeIn(seconds),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { cancelled = true }) { Text(strings.playerCancel) }
            TextButton(onClick = {
                navigated = true
                onNextEpisode()
            }) { Text(strings.playerNextEpisodeNow) }
        }
    }
}

/** Кнопка-иконка оверлея: белая на кадре видео (см. [OVERLAY_CONTENT_COLOR]). */
@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp = DEFAULT_ICON_SIZE,
) {
    val dimens = AnixThemeTokens.dimens
    IconButton(
        onClick = onClick,
        // Тач-таргет не меньше рекомендованного минимума даже у мелких иконок — оверлей
        // нажимают вслепую, глядя на видео, а не на кнопку.
        modifier = Modifier.size(maxOf(dimens.minTouchTarget, iconSize + dimens.spaceM)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = OVERLAY_CONTENT_COLOR,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Клик без ripple — на слое поверх видео рябь во весь экран читалась бы как дефект отрисовки. */
@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}

/** `1.0` → `1`, `1.25` → `1.25` — без хвостового нуля в целых значениях. */
private fun Float.formatRate(): String {
    val text = toString()
    return text.removeSuffix(".0")
}

/**
 * Скорость приходит обратно от видео как есть (`ratechange`), поэтому сравнение чипа с текущим
 * значением — приблизительное: `2.0f` из DOM может вернуться как `1.9999999`.
 */
private fun Float.matches(rate: Float): Boolean {
    val diff = this - rate
    return diff > -RATE_EPSILON && diff < RATE_EPSILON
}

/**
 * Скорость 1.0–2.0 с шагом 0.25 (P8.T5). Больше 2.0 не даём: `playbackRate` выше двух у части
 * хостов уводит звук в неразборчивую кашу, а сам шаг зафиксирован планом.
 */
@Suppress("MagicNumber") // Сами значения скорости и есть содержательные константы — заводить
// под каждую именованную (`RATE_1_25` и т.п.) было бы шумом ради метрики, тот же случай, что
// уже разобран в `AnixPalette` (shared/ui, Color.kt) для hex-литералов цвета.
private val PLAYBACK_RATES = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)

private const val RATE_EPSILON = 0.01f

/** Шаг тап-зон перемотки — те самые «−10с/+10с» из KDoc `EmbedVideoController.seekBy`. */
private const val SEEK_STEP_MS = 10_000L

/** Стандартное для видеоплееров время до авто-скрытия контролов. */
private const val CONTROLS_AUTO_HIDE_MS = 4_000L

/**
 * Цвет контента оверлея — фиксированный белый, а не из темы: под ним всегда кадр видео с тёмным
 * скримом, и в светлой теме `onSurface` оказался бы тёмным на тёмном.
 */
internal val OVERLAY_CONTENT_COLOR = Color.White

private val DEFAULT_ICON_SIZE = 32.dp
private val PLAY_BUTTON_ICON_SIZE = 56.dp
