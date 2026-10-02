package com.aniko.app.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.model.VoiceType
import com.aniko.player.EmbedVideoController
import com.aniko.player.EmbedVideoState
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.delay

/**
 * "Chrome" компактного (не полноэкранного) режима плеера — раскладка по умолчанию (P13, сверка с
 * мокапом Claude Design, `showPlayer` в `Reelwave Prototype.dc.html`): видео закреплено сверху,
 * под ним в обычном потоке — название/номер серии, полоса прогресса и **всегда видимые** (не в
 * auto-hide оверлее, как в [PlayerOverlay]) чипы озвучки/скорости.
 *
 * **Сама [com.aniko.player.EmbedPlayerView] сюда НЕ входит.** Вызывающая сторона ([PlayerScreen])
 * держит ровно один вызов `EmbedPlayerView(...)` вне ветвления compact/fullscreen и просто меняет
 * ему модификатор размера по [videoHeight] — если завести второй вызов внутри этого файла (как
 * было в первой версии, через `embedContent`-слот), переключение режимов создавало бы НОВЫЙ узел
 * композиции на новом call site, Compose разобрала бы старый `AndroidView`/`UIView` WebView и
 * создала новый — воспроизведение обрывалось бы (перезагрузка страницы, потеря позиции) при каждом
 * тапе на "На весь экран". Этот файл рисует только оверлей ПОВЕРХ видео-области (топбар с
 * back/fullscreen-кнопками + центральная play/pause) и контент ПОД ней — оба позиционируются
 * относительно того же [videoHeight], что использует вызывающая сторона для самого видео.
 *
 * **Почему чипы теперь не пропадают**: раньше единственным режимом плеера был полноэкранный
 * (см. [PlayerOverlay]) с авто-скрывающимся оверлеем — чип «Audio» пропадал через
 * `CONTROLS_AUTO_HIDE_MS` после старта воспроизведения и не появлялся снова, пока список озвучек
 * ещё грузился сетью (жалоба живой проверки: «когда запускается видео — не видна кнопка смены
 * озвучки»). Теперь чипы живут в обычном, не скрывающемся потоке под видео.
 */
@Suppress("LongParameterList") // Состояние видео/контроллер (P8.T3) + аудио-набор (P13.T10) +
// видео/серийный набор (prev/next + чип «Серия N» со шторкой) + videoHeight/onBack/
// onEnterFullscreen — та же тройка колбэков, что и у PlayerOverlay, расписана там же в KDoc
// параметров, дублировать не стали.
@Composable
fun BoxScope.CompactPlayerChrome(
    videoHeight: Dp,
    topOffset: Dp,
    onBelowContentHeightMeasured: (Dp) -> Unit,
    state: EmbedVideoState,
    controller: EmbedVideoController,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
    hasPrevEpisode: Boolean,
    onPrevEpisode: () -> Unit,
    hasNextEpisode: Boolean,
    onNextEpisode: () -> Unit,
    currentEpisodeLabel: String,
    episodesAvailable: Boolean,
    upNext: UpNextCardState? = null,
    onOpenEpisodesPicker: () -> Unit,
    voiceTypes: List<VoiceType>,
    currentVoiceType: VoiceType?,
    onOpenAudioPicker: () -> Unit,
    qualityLabel: String? = null,
    onOpenQualityPicker: () -> Unit = {},
    speedLabel: String? = null,
    onOpenSpeedPicker: () -> Unit = {},
) {
    CompactVideoOverlay(
        videoHeight = videoHeight,
        topOffset = topOffset,
        isPlaying = state.isPlaying,
        isBuffering = state.isBuffering,
        // Во время рекламы хоста — как до нахождения видео: касания уходят рекламе (кнопка «Пропустить»).
        videoFound = state.isVideoFound && !state.isAdPlaying,
        controller = controller,
        onBack = onBack,
        onEnterFullscreen = onEnterFullscreen,
        hasPrevEpisode = hasPrevEpisode,
        onPrevEpisode = onPrevEpisode,
        hasNextEpisode = hasNextEpisode,
        onNextEpisode = onNextEpisode,
        upNext = upNext,
    )
    CompactBelowVideoContent(
        videoHeight = videoHeight,
        topOffset = topOffset,
        onHeightMeasured = onBelowContentHeightMeasured,
        state = state,
        controller = controller,
        currentEpisodeLabel = currentEpisodeLabel,
        episodesAvailable = episodesAvailable,
        onOpenEpisodesPicker = onOpenEpisodesPicker,
        voiceTypes = voiceTypes,
        currentVoiceType = currentVoiceType,
        onOpenAudioPicker = onOpenAudioPicker,
        qualityLabel = qualityLabel,
        onOpenQualityPicker = onOpenQualityPicker,
        speedLabel = speedLabel,
        onOpenSpeedPicker = onOpenSpeedPicker,
    )
}

/**
 * Топбар (назад/fullscreen) + центральный ряд prev/play/next — часть [CompactPlayerChrome], те же
 * границы (`videoHeight`), что и у самого видео.
 *
 * Автоскрытие (2026-10-01, как в YouTube): тап по кадру показывает/прячет кнопки, во время
 * воспроизведения они уходят через [COMPACT_CONTROLS_AUTO_HIDE_MS]; на паузе остаются. Пока мост не
 * нашёл `<video>`, кнопки (и прежде всего «назад») видны всегда — управлять кадром ещё нечем.
 * Карточка [upNext] видна независимо от кнопок.
 */
@Suppress("LongParameterList", "LongMethod") // Длина — состояние автоскрытия + линейная раскладка
// слоёв (жесты/топбар/центр/карточка). videoHeight/isPlaying/videoFound (P16 2026-09-10 гейт первого
// запуска) + контроллер + пара onBack/onEnterFullscreen + пара prev/next — те же границы, что у
// родителя.
@Composable
private fun BoxScope.CompactVideoOverlay(
    videoHeight: Dp,
    topOffset: Dp,
    isPlaying: Boolean,
    isBuffering: Boolean,
    videoFound: Boolean,
    controller: EmbedVideoController,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
    hasPrevEpisode: Boolean,
    onPrevEpisode: () -> Unit,
    hasNextEpisode: Boolean,
    onNextEpisode: () -> Unit,
    upNext: UpNextCardState?,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val flash = rememberPlayerSeekFlash()
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val shown = controlsVisible || !videoFound
    LaunchedEffect(shown, interactionTick, isPlaying) {
        if (shown && isPlaying && videoFound) {
            delay(COMPACT_CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    val scrimAlpha by animateFloatAsState(if (shown && videoFound) COMPACT_SCRIM_ALPHA else 0f, label = "compactScrim")
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(videoHeight)
                .align(Alignment.TopStart)
                .offset(y = topOffset),
    ) {
        // P16 (2026-09-10): до нахождения <video> тапы НЕ перехватываем — старт выполняет
        // большой play хоста (trust-gesture, синтетический клик плеер игнорирует), он виден до
        // первого старта и скрывается классом `aniko-video-found`. После — перехват наш:
        // тап play/pause, double-tap ±10с.
        if (videoFound) {
            CompactVideoGestureLayer(
                controller = controller,
                flash = flash,
                onTap = {
                    controlsVisible = !controlsVisible
                    interactionTick++
                },
                onSeek = {
                    controlsVisible = true
                    interactionTick++
                },
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }
        // Отступ сверху — только на ту часть статус-бара, под которую реально заходит видео: в
        // компактном режиме видео центрировано по экрану, и полный safe-inset опускал «назад»/«на весь
        // экран» почти к середине кадра, вровень с центральным рядом (живая проверка iOS 2026-10-02).
        val statusBarInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val topInset = (statusBarInset - topOffset).coerceAtLeast(0.dp)
        AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(top = topInset)
                        .padding(dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactOverlayIconButton(
                    iconName = "arrow_back",
                    contentDescription = strings.backContentDescription,
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.weight(1f))
                CompactOverlayIconButton(
                    iconName = "fullscreen",
                    filled = true,
                    contentDescription = strings.playerEnterFullscreen,
                    onClick = onEnterFullscreen,
                )
            }
        }

        if (videoFound) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                CompactCenterControls(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    controller = controller,
                    hasPrevEpisode = hasPrevEpisode,
                    onPrevEpisode = onPrevEpisode,
                    hasNextEpisode = hasNextEpisode,
                    onNextEpisode = onNextEpisode,
                    onInteraction = { interactionTick++ },
                )
            }

            if (upNext != null) {
                UpNextCard(
                    state = upNext,
                    compact = true,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(dimens.spaceS),
                )
            }

            // Вспышка ±10с у края double-tap (общий с fullscreen индикатор,
            // [PlayerSeekFlashOverlay] сам позиционируется по направлению перемотки).
            // Чисто визуальный слой, тапы не ест.
            PlayerSeekFlashOverlay(state = flash)
        }
    }
}

/**
 * Центральный ряд компактного оверлея: prev / play-pause / next — тот же набор, что в fullscreen
 * ([PlayerCenterControls]), круги того же компактного стиля, что и раньше одна play-pause.
 * Prev/next гейтятся hasPrev/hasNext: на крайних сериях кнопка видна, но неактивна (тот же приём
 * disabled-альфы, что у OverlayIconButton). Вынесен отдельно (detekt `LongMethod` у родителя).
 */
@Suppress("LongParameterList") // Та же пара «флаг + колбэк» на prev/next, что и у родителя;
// isPlaying/controller — для play-pause в середине ряда.
@Composable
private fun CompactCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    controller: EmbedVideoController,
    hasPrevEpisode: Boolean,
    onPrevEpisode: () -> Unit,
    hasNextEpisode: Boolean,
    onNextEpisode: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactEpisodeSkipButton(
            iconName = "skip_previous",
            contentDescription = strings.playerPrevEpisode,
            enabled = hasPrevEpisode,
            onClick = onPrevEpisode,
        )
        // Буферизация — на месте ▶/⏸ спиннер [PlayerStartIndicator]; кнопка держит место в ряду.
        Box(modifier = Modifier.alpha(if (isBuffering) 0f else 1f)) {
            CompactPlayPauseButton(
                isPlaying = isPlaying,
                onClick = {
                    onInteraction()
                    controller.togglePlayPause()
                },
                contentDescription = if (isPlaying) strings.playerPause else strings.playerPlay,
            )
        }
        CompactEpisodeSkipButton(
            iconName = "skip_next",
            contentDescription = strings.playerNextEpisode,
            enabled = hasNextEpisode,
            onClick = onNextEpisode,
        )
    }
}

/**
 * Кнопка prev/next серии компактного центрального ряда — тот же общий [PlayerCircleIconButton],
 * что [CompactOverlayIconButton] (круг 34×34 `rgba(0,0,0,0.5)`), но с гейтом [enabled]: M3-альфа
 * disabled 0.38 на иконке, у самой кнопки тач-таргет не меньше [AnixThemeTokens.dimens.minTouchTarget].
 */
@Composable
private fun CompactEpisodeSkipButton(
    iconName: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PlayerCircleIconButton(
        iconName = iconName,
        contentDescription = contentDescription,
        // PlayerCircleIconButton не имеет гейта enabled — глушим клик здесь, визуальное
        // disabled-состояние ниже.
        onClick = { if (enabled) onClick() },
        diameter = OVERLAY_BUTTON_SIZE,
        iconSize = OVERLAY_BUTTON_ICON_SIZE,
        background =
            if (enabled) {
                Color.Black.copy(alpha = OVERLAY_BUTTON_SCRIM_ALPHA)
            } else {
                Color.Black.copy(alpha = OVERLAY_BUTTON_SCRIM_ALPHA * OVERLAY_DISABLED_ALPHA)
            },
        tint =
            if (enabled) {
                OVERLAY_CONTENT_COLOR
            } else {
                OVERLAY_CONTENT_COLOR.copy(alpha = OVERLAY_DISABLED_ALPHA)
            },
    )
}

/**
 * Перехватывающий слой компактного видео-оверлея (тот же приём, что и в [PlayerOverlay.kt], см.
 * её KDoc про "перехват касаний"): без него нативный UI чужой embed-страницы под нами может
 * забирать тапы себе раньше, чем они дойдут до наших кнопок — на живой проверке кнопка
 * "На весь экран" в правом верхнем углу видео не реагировала ни разу, пока не появился этот слой.
 * Тап по видео (не по кнопке) показывает/прячет кнопки ([onTap], как в YouTube); double-tap в
 * левой/правой половине перематывает на −10с/+10с (те же ±10с, что и в [PlayerOverlay]).
 */
@Composable
private fun CompactVideoGestureLayer(
    controller: EmbedVideoController,
    flash: PlayerSeekFlashState,
    onTap: () -> Unit,
    onSeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
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
                        onSeek()
                        flash.fire(direction)
                    },
                )
            },
    )
}

/** Название/прогресс/чипы под видео — не Column-обёртка вокруг видео (см. KDoc
 *  [CompactPlayerChrome] про единственный call site [com.aniko.player.EmbedPlayerView]), а
 *  отдельный узел, сдвинутый вниз на `videoHeight` через `padding(top = …)`. Вынесена отдельно
 *  (detekt `LongMethod`).
 *
 * [topOffset] — вертикальное центрирование компактного блока (2026-09-11, см. KDoc `PlayerScreen`
 * про живой баг-репорт «видео должно быть посередине»): та же величина, что сдвигает само видео
 * и оверлей над ним, добавляется поверх `videoHeight` в `padding(top = …)`. [onHeightMeasured]
 * замыкает цикл измерения: реальная высота ЭТОГО блока (переменная — прогресс-бар появляется
 * только после `loadedmetadata`, число чипов озвучки варьируется) нужна вызывающей стороне,
 * чтобы посчитать [topOffset] для центрирования всего кластера (видео + этот блок) как единого
 * целого — измеряется через `onGloballyPositioned`, а не читается заранее (высота неизвестна до
 * первой реальной раскладки).
 */
@Suppress("LongParameterList") // Та же тройка состояние/контроллер/аудио-набор, что и у родителя,
// плюс пара «текущая серия/открытие шторки» — гейтится непустым списком серий на вызывающей
// стороне, шторка общая с fullscreen ([EpisodesSheetOverlay] в PlayerScreen).
@Composable
private fun BoxScope.CompactBelowVideoContent(
    videoHeight: Dp,
    topOffset: Dp,
    onHeightMeasured: (Dp) -> Unit,
    state: EmbedVideoState,
    controller: EmbedVideoController,
    currentEpisodeLabel: String,
    episodesAvailable: Boolean,
    onOpenEpisodesPicker: () -> Unit,
    voiceTypes: List<VoiceType>,
    currentVoiceType: VoiceType?,
    onOpenAudioPicker: () -> Unit,
    qualityLabel: String? = null,
    onOpenQualityPicker: () -> Unit = {},
    speedLabel: String? = null,
    onOpenSpeedPicker: () -> Unit = {},
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val density = LocalDensity.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(top = videoHeight + topOffset)
                .onGloballyPositioned { coordinates ->
                    onHeightMeasured(with(density) { coordinates.size.height.toDp() })
                }.padding(dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        PlayerProgressBar(
            currentTimeMs = state.currentTimeMs,
            durationMs = state.durationMs,
            onSeek = { positionMs -> controller.seekTo(positionMs) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            // Чип «Серия N» первым: это навигация по контенту, а остальные пилюли — настройки
            // воспроизведения. Не рисуется, пока список серий не загрузился (открывать шторку
            // нечего) — тот же принцип честного UI, что у чипа Audio с одной озвучкой.
            if (episodesAvailable) {
                PlayerPillChip(
                    label = strings.playerEpisodeChip(currentEpisodeLabel),
                    onClick = onOpenEpisodesPicker,
                )
            }
            if (voiceTypes.size > 1) {
                PlayerPillChip(
                    label =
                        currentVoiceType?.let { strings.playerAudioChipLabel(it.name) }
                            ?: strings.playerAudioLabel,
                    onClick = onOpenAudioPicker,
                )
                if (currentVoiceType?.isSub == true) {
                    PlayerPillChip(label = strings.releaseVoiceFilterSub, onClick = null)
                }
            }
            if (qualityLabel != null) {
                PlayerPillChip(
                    label = strings.playerQualityChip(qualityLabel),
                    onClick = onOpenQualityPicker,
                )
            }
            if (speedLabel != null) {
                PlayerPillChip(
                    label = speedLabel,
                    onClick = onOpenSpeedPicker,
                )
            }
        }
    }
}

/**
 * Переиспользуемый плоский пилл-чип (P13) — та же визуальная стилистика, что у чипов озвучки/
 * скорости/качества в мокапе (`Reelwave Prototype.dc.html`, `showPlayer`): полупрозрачный белый
 * фон `rgba(255,255,255,0.07)`, скругление 10px, белый текст — не тема-зависимый M3
 * `FilterChip`/`AssistChip` (те подтягивают акцентный цвет темы для selected-состояния, чего в
 * самом мокапе для этих чипов нет вовсе — там все варианты выглядят одинаково нейтрально,
 * различаясь только текстом внутри). [selected] чуть повышает alpha фона — минимальная подсказка
 * текущего значения без внедрения акцентного цвета, которого нет в референсе.
 */
@Composable
internal fun PlayerPillChip(
    label: String,
    onClick: (() -> Unit)?,
    selected: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    val backgroundAlpha = if (selected) PILL_CHIP_SELECTED_ALPHA else PILL_CHIP_ALPHA
    val shape = RoundedCornerShape(PILL_CHIP_CORNER)
    var base =
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = backgroundAlpha), shape)
    if (onClick != null) {
        base = base.clickableNoIndication(onClick)
    }
    Box(
        modifier = base.padding(horizontal = dimens.space12, vertical = dimens.spaceS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = PILL_CHIP_FONT_SIZE,
            fontWeight = PILL_CHIP_FONT_WEIGHT,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Круглая полупрозрачная чёрная подложка 34×34 под back/fullscreen (Track A, точное соответствие
 * макету Claude Design, `showPlayer`) — тач-таргет остаётся не меньше [AnixDimens.minTouchTarget]
 * (доступность), сама видимая подложка отдельным вложенным кругом — макет рисует именно
 * `rgba(0,0,0,0.5)`-круг фиксированного размера, а не растянутый на весь тач-таргет. Сборка круга и
 * центровка иконки — в общем [PlayerCircleIconButton].
 */
@Composable
private fun CompactOverlayIconButton(
    iconName: String,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    PlayerCircleIconButton(
        iconName = iconName,
        contentDescription = contentDescription,
        onClick = onClick,
        diameter = OVERLAY_BUTTON_SIZE,
        iconSize = OVERLAY_BUTTON_ICON_SIZE,
        background = Color.Black.copy(alpha = OVERLAY_BUTTON_SCRIM_ALPHA),
        filled = filled,
    )
}

/** Круглая подложка 52×52 `rgba(0,0,0,0.45)` под центральной play/pause-кнопкой (Track A) —
 *  тот же общий [PlayerCircleIconButton], что и [CompactOverlayIconButton], только свой размер/альфа
 *  по макету. */
@Composable
private fun CompactPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    PlayerCircleIconButton(
        iconName = if (isPlaying) "pause" else "play_arrow",
        contentDescription = contentDescription,
        onClick = onClick,
        diameter = PLAY_PAUSE_BUTTON_SIZE,
        iconSize = PLAY_PAUSE_ICON_SIZE,
        background = Color.Black.copy(alpha = PLAY_PAUSE_SCRIM_ALPHA),
        filled = true,
    )
}

/** Автоскрытие кнопок компактного оверлея во время воспроизведения (короче fullscreen — область
 *  маленькая, кнопки сильнее закрывают кадр). */
private const val COMPACT_CONTROLS_AUTO_HIDE_MS = 3_000L

/** Затемнение кадра под видимыми кнопками — читаемость белых иконок на светлых сценах. */
private const val COMPACT_SCRIM_ALPHA = 0.3f

/** Круг back/fullscreen-кнопки компактного оверлея (Track A, `showPlayer`). */
private val OVERLAY_BUTTON_SIZE = 34.dp
private val OVERLAY_BUTTON_ICON_SIZE = 24.dp
private const val OVERLAY_BUTTON_SCRIM_ALPHA = 0.5f

/** Круг центральной play/pause-кнопки (Track A, `showPlayer`). */
private val PLAY_PAUSE_BUTTON_SIZE = 52.dp
private const val PLAY_PAUSE_SCRIM_ALPHA = 0.45f
private val PLAY_PAUSE_ICON_SIZE = 28.dp

private const val PILL_CHIP_ALPHA = 0.07f
private const val PILL_CHIP_SELECTED_ALPHA = 0.16f

/** Радиус/типографика чипов Audio/Speed компактного оверлея — точное соответствие макету
 *  (`showPlayer`): 10px radius, 11.5px/600, не берётся из [AnixDimens] (шаг токенов 8/12/16
 *  не содержит 10, а этот чип — единственное место, которому нужно именно 10). */
private val PILL_CHIP_CORNER = 10.dp
private val PILL_CHIP_FONT_SIZE = 11.5.sp
private val PILL_CHIP_FONT_WEIGHT = FontWeight.SemiBold
