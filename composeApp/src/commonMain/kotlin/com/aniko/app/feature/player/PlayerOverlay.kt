@file:Suppress("TooManyFunctions") // Один экран-оверлей, разложенный на маленькие приватные
// composable по секциям макета (топбар/центр/нижняя панель/баннер/пикер озвучки, P13.T10) — это
// декомпозиция в пользу читаемости, а не разрастание ответственности одного файла.

package com.aniko.app.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.model.VoiceType
import com.aniko.player.EmbedVideoController
import com.aniko.player.EmbedVideoState
import com.aniko.player.isEpisodeFinished
import com.aniko.player.isNearEnd
import com.aniko.player.secondsToEpisodeEnd
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.VoiceTypeRow
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
 * состоянии из оверлея остаётся только стрелка «назад», причём постоянно видимая: на iOS без
 * системного edge-swipe единственный гарантированный выход — два тапа по стрелке
 * (fullscreen→compact→exit).
 *
 * Скрытие chrome хоста — отдельный косметический слой в JS-мосте (`EmbedVideoBridge`), а не
 * функциональная политика оверлея: если скрыть chrome не удалось, fallback — текущее поведение
 * «наш UI побеждает», никогда — потеря управления.
 *
 * @param onBack выход из экрана плеера. Используется только в no-bridge fullscreen
 * (`!state.isVideoFound`): там стрелка «назад» не может свернуть в compact, потому что
 * `CompactVideoGestureLayer` запирает embed-страницу, а мост не управляет видео. В обычном
 * bridge-fullscreen стрелка сворачивает в compact через [onCollapseFullscreen], поэтому этот
 * колбэк — fallback-выход (единственный выход на iOS без edge-swipe, см. абзац выше).
 * @param onCollapseFullscreen сворачивает fullscreen обратно в компактный режим (P13) — просто
 * смена раскладки без навигации. При `bridgeActive` именно это делает стрелка «назад».
 * @param onEpisodeNearEnd вызывается, когда серия подходит к концу — сюда подвешена авто-отметка
 * «просмотрено» (P8.T8). Порог — общий с баннером ([isNearEnd] из `:shared:player`), сознательно
 * один и тот же на обе фичи.
 * @param onNextEpisode переход на следующую серию: и по кнопке «Смотреть сейчас», и по истечении
 * обратного отсчёта. Вызывается не чаще одного раза за жизнь этого экрана.
 * @param voiceTypes список озвучек релиза для чипа «Audio» (P13.T10, `PlayerUiState.voiceTypes`).
 * Пустой список прячет чип целиком — переключаться некуда, показывать неактивную кнопку незачем
 * (тот же принцип честного UI, что и у PiP-заглушки, только тут решение — не рисовать вовсе).
 * @param currentVoiceType озвучка текущего источника (`PlayerUiState.currentVoiceType`) — подпись
 * чипа и подсветка выбранной строки в пикере. `null`, пока подбор ещё не завершился.
 * @param onOpenAudioPicker тап по чипу «Audio» (P13) — сам [AudioPickerOverlay] здесь больше не
 * рисуется (см. её KDoc), состояние видимости пикера и выбор строки (`selectVoiceType`) подняты в
 * [PlayerScreen], один пикер общий для compact- и fullscreen-режимов вместо двух независимых копий.
 */
@Suppress("LongParameterList", "LongMethod") // Состояние + контроллер + флаг наличия следующей
// серии + 3 колбэка наружу (назад/следующая/конец серии) + аудио-пикер (список + текущая озвучка +
// флаг загрузки + колбэк выбора, P13.T10) + modifier. Дробить оверлей на части с меньшим числом
// параметров пришлось бы через общий mutable-объект состояния — это хуже, чем счётчик. Тело функции
// длиннее лимита ровно из-за этого же перечисления layout-секций (топбар/центр/баннер/панель/пикер)
// — каждая уже вынесена в свой composable, короче эта функция уже не станет без потери читаемости.
@Composable
fun PlayerOverlay(
    state: EmbedVideoState,
    controller: EmbedVideoController,
    hasNextEpisode: Boolean,
    onBack: () -> Unit,
    onCollapseFullscreen: () -> Unit,
    onNextEpisode: () -> Unit,
    onEpisodeNearEnd: () -> Unit,
    voiceTypes: List<VoiceType> = emptyList(),
    currentVoiceType: VoiceType? = null,
    onOpenAudioPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = AnixThemeTokens.colors
    val flash = rememberPlayerSeekFlash()
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
            // Отдельный слой-перехватчик: тап по любому месту кадра показывает/прячет контролы,
            // двойной тап по левой/правой половине — перемотка на −10с/+10с.
            // `indication = null` — рябь на весь экран поверх видео выглядела бы как дефект.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colors.posterScrim.copy(alpha = colors.posterScrim.alpha * scrimAlpha))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    interactionTick++
                                },
                                onDoubleTap = { offset ->
                                    val direction =
                                        if (offset.x < size.width / 2f) {
                                            PlayerSeekDirection.BACK
                                        } else {
                                            PlayerSeekDirection.FORWARD
                                        }
                                    val deltaMs =
                                        if (direction == PlayerSeekDirection.BACK) {
                                            -PLAYER_SEEK_STEP_MS
                                        } else {
                                            PLAYER_SEEK_STEP_MS
                                        }
                                    controller.seekBy(deltaMs)
                                    controlsVisible = true
                                    interactionTick++
                                    flash.fire(direction)
                                },
                            )
                        },
            )
        }

        // Вспышка ±10с у края тапа — декоративный отклик жеста, не перехватывает касания.
        // Один вызов: [PlayerSeekFlashOverlay] сам позиционируется по направлению перемотки.
        PlayerSeekFlashOverlay(state = flash)

        // Колонка сама по себе не перехватывает касания (у неё нет pointer-модификаторов), поэтому
        // тапы мимо кнопок проваливаются в слой-перехватчик выше и продолжают прятать контролы.
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // `|| !bridgeActive` — когда управлять нечем, «назад» висит постоянно: это
            // единственный выход с экрана на iOS.
            AnimatedVisibility(visible = controlsShown || !bridgeActive, enter = fadeIn(), exit = fadeOut()) {
                PlayerTopBar(
                    onBack = onBack,
                    onCollapseFullscreen = onCollapseFullscreen,
                    bridgeActive = bridgeActive,
                    onInteraction = { interactionTick++ },
                )
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
                    voiceTypes = voiceTypes,
                    currentVoiceType = currentVoiceType,
                    onOpenAudioPicker = {
                        interactionTick++
                        onOpenAudioPicker()
                    },
                )
            }
        }
    }
}

/**
 * Стрелка «назад» + PiP-заглушка (P8.T3).
 *
 * Семантика стрелки зависит от [bridgeActive]: при активном мосте она сворачивает fullscreen
 * в compact ([onCollapseFullscreen]), при неактивном — выходит из экрана ([onBack]), чтобы не
 * запереть пользователя в неуправляемом compact-режиме.
 */
@Composable
private fun PlayerTopBar(
    onBack: () -> Unit,
    onCollapseFullscreen: () -> Unit,
    bridgeActive: Boolean,
    onInteraction: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIconButton(
            iconName = "arrow_back",
            contentDescription = strings.backContentDescription,
            onClick = {
                onInteraction()
                if (bridgeActive) {
                    onCollapseFullscreen()
                } else {
                    onBack()
                }
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        OverlayIconButton(
            iconName = "picture_in_picture_alt",
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
            iconName = "replay_10",
            filled = true,
            contentDescription = strings.playerSeekBackward,
            onClick = {
                onInteraction()
                controller.seekBy(-PLAYER_SEEK_STEP_MS)
            },
        )
        OverlayIconButton(
            iconName = if (isPlaying) "pause" else "play_arrow",
            filled = true,
            contentDescription = if (isPlaying) strings.playerPause else strings.playerPlay,
            iconSize = PLAY_BUTTON_ICON_SIZE,
            onClick = {
                onInteraction()
                controller.togglePlayPause()
            },
        )
        OverlayIconButton(
            iconName = "forward_10",
            filled = true,
            contentDescription = strings.playerSeekForward,
            onClick = {
                onInteraction()
                controller.seekBy(PLAYER_SEEK_STEP_MS)
            },
        )
    }
}

/**
 * Нижняя панель fullscreen: прогресс-бар с seek (P8.T3) + одна центрированная строка пилюль
 * (P13.T10 / player-triple-design, §3).
 *
 * Раскладка — `Column { PlayerProgressBar; Box(Center) { Row(horizontalScroll) { пилюли } } }`.
 * Порядок пилюль идентичен compact-строке: audio → sub → speeds, чтобы оба режима читались
 * одинаково. Строка центрирована, когда влезает, и скроллируется при переполнении.
 *
 * Прогресс-бар рисуется **только** при `durationMs != null` — до события `loadedmetadata`
 * длительности не существует вообще (`duration = NaN`), и шкала «от нуля до неизвестно чего»
 * была бы выдумкой. Пока её нет — панель показывает только пилюли.
 *
 * **Качества здесь нет и не будет** — CUT, см. `docs/REELWAVE_PLAN.md` (отчёт P13.T9): сегмент
 * качества в Kodik embed-URL (`/720p`) декоративный на нашей стороне — приложение никогда само не
 * выбирает качество, решает сервер Anixart/Kodik при подписи ссылки. Своя кнопка переключения
 * либо ничего не даст, либо сломает подпись URL и покажет пользователю ошибку вместо видео.
 * Хостовое меню качества в chrome плеера намеренно скрыто вместе с остальным chrome — см.
 * `EmbedVideoBridge` и §1.5 player-triple-design.
 *
 * Пилюли — [PlayerPillChip] (тот же компонент, что в compact-режиме), а не M3
 * `FilterChip`/`AssistChip`: мокап рисует их нейтральными, без акцентного selected-цвета.
 */
@Suppress("LongParameterList") // Состояние/контроллер видео + колбэк взаимодействия (существующая
// P8.T3/T5 тройка) + список озвучек/текущая озвучка/колбэк открытия пикера (P13.T10). Группировать
// P13.T10-параметры в объект ради одного вызова было бы отдельным типом без другого назначения.
@Composable
private fun PlayerBottomPanel(
    state: EmbedVideoState,
    controller: EmbedVideoController,
    onInteraction: () -> Unit,
    voiceTypes: List<VoiceType>,
    currentVoiceType: VoiceType?,
    onOpenAudioPicker: () -> Unit,
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

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                // Меньше двух озвучек — переключаться некуда, чип не рисуем вовсе (тот же принцип
                // честного UI, что и у PiP-заглушки/качества выше — см. KDoc [PlayerOverlay]).
                if (voiceTypes.size > 1) {
                    PlayerPillChip(
                        label =
                            currentVoiceType?.let { strings.playerAudioChipLabel(it.name) }
                                ?: strings.playerAudioLabel,
                        onClick = {
                            onInteraction()
                            onOpenAudioPicker()
                        },
                    )
                    if (currentVoiceType?.isSub == true) {
                        PlayerPillChip(label = strings.releaseVoiceFilterSub, onClick = null)
                    }
                }
                PLAYBACK_RATES.forEach { rate ->
                    PlayerPillChip(
                        label = strings.playerSpeedValue(rate.formatRate()),
                        selected = state.playbackRate.matches(rate),
                        onClick = {
                            onInteraction()
                            controller.setPlaybackRate(rate)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Пикер озвучки поверх кадра плеера (P13.T10) — открывается пилюлей «Audio» ([PlayerPillChip] в
 * обоих режимах), не отдельный экран или маршрут (как `showDubPicker` в мокапе): полноэкранный
 * скрим с прижатой к низу панелью и списком [VoiceTypeRow] — тем же переиспользуемым компонентом
 * `shared/ui`, что и `VoiceTypeSelector` на Title Detail (`ReleaseEpisodesSection.kt`, P8.T6).
 * Список озвучек — один и тот же API-объект ([VoiceType]) в обоих местах, заводить второй
 * визуальный компонент под него незачем.
 *
 * `internal`, не `private` (P13) — состояние видимости (`showAudioPicker`) поднято из
 * [PlayerOverlay] в [PlayerScreen], один и тот же пикер рисуется поверх ОБОИХ режимов
 * (compact/fullscreen) вместо двух независимых копий с отдельным состоянием каждая.
 *
 * Без фильтра «Все/Дубляж/Субтитры», который есть на Detail: там он оправдан длинным списком под
 * все сценарии использования экрана тайтла, здесь — лишний слой поверх видео ради списка, который
 * почти всегда короче десяти строк.
 *
 * Тап по скриму закрывает пикер (тот же жест, что открывает/прячет контролы под ним в fullscreen-
 * режиме, только пикер физически выше в Z-порядке общего `Box` в [PlayerScreen]).
 */
@Composable
internal fun AudioPickerOverlay(
    voiceTypes: List<VoiceType>,
    currentVoiceType: VoiceType?,
    isSwitching: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    val sorted = remember(voiceTypes) { voiceTypes.sortedByDescending(VoiceType::pinned) }
    // Живая проверка нашла: список озвучек у некоторых релизов доходит до 10+ студий, а старая
    // версия рисовала их обычным `Column.forEach` без ограничения высоты и без скролла — панель
    // росла выше экрана, верхние строки списка оказывались за его пределами и были физически
    // недостижимы (жалоба «не видна вся доступная озвучка»). `heightIn(max = ...)` ограничивает
    // саму панель, `LazyColumn` внутри даёт скролл для того, что не поместилось.
    val maxSheetHeight = LocalWindowInfo.current.containerDpSize.height * AUDIO_PICKER_MAX_HEIGHT_FRACTION

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.posterScrim)
                .clickableNoIndication(onDismiss),
    ) {
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    // Проглатывает тап, чтобы панель не закрывалась сквозь саму себя.
                    .clickableNoIndication {},
            shape = RoundedCornerShape(topStart = dimens.cornerL, topEnd = dimens.cornerL),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
            ) {
                AudioPickerHeader(title = strings.playerAudioLabel, onDismiss = onDismiss)
                if (isSwitching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                    items(sorted, key = VoiceType::id) { type ->
                        VoiceTypeRow(
                            voiceType = type,
                            selected = type.id == currentVoiceType?.id,
                            onClick = { onSelect(type.id) },
                            // Пикер плеера — единственное место, где выбранная строка озвучки
                            // подсвечивается акцентным тинтом альфа 0.14 (Track A, `showDubPicker`)
                            // — заметно светлее, чем 0.22 у фильтр-чипов `ChipRow`/`FilterChip` в
                            // остальном приложении (Detail и т.д.), сознательно не унифицировано:
                            // так задано макетом для ДВУХ разных визуальных паттернов "выбрано".
                            accentSelected = true,
                        )
                    }
                }
            }
        }
    }
}

/** Шапка пикера озвучки: заголовок 17px/800 + круглая кнопка закрытия 34×34 `overlay08` — точное
 *  соответствие макету (`showDubPicker`). Вынесена отдельно из [AudioPickerOverlay] (detekt
 *  `LongMethod`). */
@Composable
private fun AudioPickerHeader(
    title: String,
    onDismiss: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = AUDIO_PICKER_TITLE_FONT_SIZE,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(dimens.minTouchTarget),
        ) {
            // Круглая подложка 34×34 `overlay08` вокруг кнопки закрытия — точное соответствие
            // макету (`showDubPicker`), не голая иконка без фона.
            Box(
                modifier =
                    Modifier
                        .size(AUDIO_PICKER_CLOSE_BUTTON_SIZE)
                        .clip(CircleShape)
                        .background(colors.overlay08),
                contentAlignment = Alignment.Center,
            ) {
                AnixIcon(
                    name = "close",
                    contentDescription = strings.closeContentDescription,
                    filled = true,
                )
            }
        }
    }
}

private const val AUDIO_PICKER_MAX_HEIGHT_FRACTION = 0.6f
private val AUDIO_PICKER_TITLE_FONT_SIZE = 17.sp
private val AUDIO_PICKER_CLOSE_BUTTON_SIZE = 34.dp

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
    // Точное соответствие макету Claude Design (`showPlayer`): фиксированный тёмный фон
    // `rgba(15,16,22,0.9)`, а не тема-зависимый `colorScheme.surface` — баннер, как и остальной
    // оверлей плеера, всегда рисуется поверх тёмного кадра видео независимо от темы приложения
    // (тот же принцип, что и у [OVERLAY_CONTENT_COLOR]).
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceM, vertical = dimens.spaceS),
        shape = RoundedCornerShape(dimens.cornerM),
        color = NEXT_EPISODE_BANNER_COLOR,
        contentColor = OVERLAY_CONTENT_COLOR,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = NEXT_EPISODE_BANNER_PADDING_H,
                    vertical = NEXT_EPISODE_BANNER_PADDING_V,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            Text(
                text = strings.playerNextEpisodeIn(seconds),
                fontSize = NEXT_EPISODE_BANNER_FONT_SIZE,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { cancelled = true }) {
                Text(
                    text = strings.playerCancel,
                    fontSize = NEXT_EPISODE_BANNER_FONT_SIZE,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = {
                navigated = true
                onNextEpisode()
            }) {
                Text(
                    text = strings.playerNextEpisodeNow,
                    fontSize = NEXT_EPISODE_BANNER_FONT_SIZE,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Фон баннера «следующая серия» — точный hex макета (`rgba(15,16,22,0.9)`), см. комментарий у
 *  места использования. */
private const val NEXT_EPISODE_BANNER_BG_ALPHA = 0.9f

@Suppress("MagicNumber") // hex-литерал цвета — то же обоснование, что и у `AnixPalette`
// (shared/ui, Color.kt): сам hex и есть содержательная константа, заводить под него ещё одну
// именованную числовую метрику было бы шумом.
private val NEXT_EPISODE_BANNER_COLOR = Color(0xFF0F1016).copy(alpha = NEXT_EPISODE_BANNER_BG_ALPHA)
private val NEXT_EPISODE_BANNER_PADDING_H = 12.dp
private val NEXT_EPISODE_BANNER_PADDING_V = 10.dp
private val NEXT_EPISODE_BANNER_FONT_SIZE = 11.sp

/** Кнопка-иконка оверлея: белая на кадре видео (см. [OVERLAY_CONTENT_COLOR]). */
@Composable
private fun OverlayIconButton(
    iconName: String,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    iconSize: Dp = DEFAULT_ICON_SIZE,
) {
    val dimens = AnixThemeTokens.dimens
    val buttonDescription = contentDescription
    IconButton(
        onClick = onClick,
        // Тач-таргет не меньше рекомендованного минимума даже у мелких иконок — оверлей
        // нажимают вслепую, глядя на видео, а не на кнопку. `clearAndSetSemantics` вместо
        // contentDescription на Icon (Фаза 11, T9, подтверждено на устройстве): IconButton не
        // сливает его в свой кликабельный узел — тот же паттерн, что и остальные M3-компоненты
        // этой фазы (см. AnixNavigationBar.kt/ChipRow.kt/LibraryScreen.kt).
        modifier =
            Modifier
                .size(maxOf(dimens.minTouchTarget, iconSize + dimens.spaceM))
                .clearAndSetSemantics { this.contentDescription = buttonDescription },
    ) {
        AnixIcon(
            name = iconName,
            contentDescription = null,
            filled = filled,
            tint = OVERLAY_CONTENT_COLOR,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Клик без ripple — на слое поверх видео рябь во весь экран читалась бы как дефект отрисовки.
 *  `internal`, не `private` — переиспользуется [CompactPlayerLayout]/[PlayerPillChip] (тот же
 *  пакет, тот же принцип: чипы плеера не должны показывать ripple поверх видео). */
@Composable
internal fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}

/** `1.0` → `1`, `1.25` → `1.25` — без хвостового нуля в целых значениях. `internal`: переиспользуется
 *  [CompactPlayerLayout]. */
internal fun Float.formatRate(): String {
    val text = toString()
    return text.removeSuffix(".0")
}

/**
 * Скорость приходит обратно от видео как есть (`ratechange`), поэтому сравнение чипа с текущим
 * значением — приблизительное: `2.0f` из DOM может вернуться как `1.9999999`. `internal`:
 * переиспользуется [CompactPlayerLayout].
 */
internal fun Float.matches(rate: Float): Boolean {
    val diff = this - rate
    return diff > -RATE_EPSILON && diff < RATE_EPSILON
}

/**
 * Скорость 1.0–2.0 с шагом 0.25 (P8.T5). Больше 2.0 не даём: `playbackRate` выше двух у части
 * хостов уводит звук в неразборчивую кашу, а сам шаг зафиксирован планом. `internal`:
 * переиспользуется [CompactPlayerLayout].
 */
@Suppress("MagicNumber") // Сами значения скорости и есть содержательные константы — заводить
// под каждую именованную (`RATE_1_25` и т.п.) было бы шумом ради метрики, тот же случай, что
// уже разобран в `AnixPalette` (shared/ui, Color.kt) для hex-литералов цвета.
internal val PLAYBACK_RATES = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)

private const val RATE_EPSILON = 0.01f

/** Стандартное для видеоплееров время до авто-скрытия контролов. */
private const val CONTROLS_AUTO_HIDE_MS = 4_000L

/**
 * Цвет контента оверлея — фиксированный белый, а не из темы: под ним всегда кадр видео с тёмным
 * скримом, и в светлой теме `onSurface` оказался бы тёмным на тёмном.
 */
internal val OVERLAY_CONTENT_COLOR = Color.White

private val DEFAULT_ICON_SIZE = 32.dp
private val PLAY_BUTTON_ICON_SIZE = 56.dp
