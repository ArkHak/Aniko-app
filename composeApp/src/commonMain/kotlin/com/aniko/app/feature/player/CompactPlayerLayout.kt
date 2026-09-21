package com.aniko.app.feature.player

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
// videoHeight/onBack/onEnterFullscreen — та же тройка колбэков, что и у PlayerOverlay, расписана
// там же в KDoc параметров, дублировать не стали.
@Composable
fun BoxScope.CompactPlayerChrome(
    videoHeight: Dp,
    topOffset: Dp,
    onBelowContentHeightMeasured: (Dp) -> Unit,
    state: EmbedVideoState,
    controller: EmbedVideoController,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
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
        videoFound = state.isVideoFound,
        controller = controller,
        onBack = onBack,
        onEnterFullscreen = onEnterFullscreen,
    )
    CompactBelowVideoContent(
        videoHeight = videoHeight,
        topOffset = topOffset,
        onHeightMeasured = onBelowContentHeightMeasured,
        state = state,
        controller = controller,
        voiceTypes = voiceTypes,
        currentVoiceType = currentVoiceType,
        onOpenAudioPicker = onOpenAudioPicker,
        qualityLabel = qualityLabel,
        onOpenQualityPicker = onOpenQualityPicker,
        speedLabel = speedLabel,
        onOpenSpeedPicker = onOpenSpeedPicker,
    )
}

/** Топбар (назад/fullscreen) + центральная play-pause — часть [CompactPlayerChrome], те же границы
 *  (`videoHeight`), что и у самого видео. Вынесена отдельно (detekt `LongMethod`). */
@Suppress("LongParameterList") // videoHeight/isPlaying/videoFound (P16 2026-09-10 гейт первого
// запуска) + контроллер + пара onBack/onEnterFullscreen — те же границы, что у родителя.
@Composable
private fun BoxScope.CompactVideoOverlay(
    videoHeight: Dp,
    topOffset: Dp,
    isPlaying: Boolean,
    videoFound: Boolean,
    controller: EmbedVideoController,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val flash = rememberPlayerSeekFlash()
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
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
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

        if (videoFound) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                CompactPlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = { controller.togglePlayPause() },
                    contentDescription = if (isPlaying) strings.playerPause else strings.playerPlay,
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
 * Перехватывающий слой компактного видео-оверлея (тот же приём, что и в [PlayerOverlay.kt], см.
 * её KDoc про "перехват касаний"): без него нативный UI чужой embed-страницы под нами может
 * забирать тапы себе раньше, чем они дойдут до наших кнопок — на живой проверке кнопка
 * "На весь экран" в правом верхнем углу видео не реагировала ни разу, пока не появился этот слой.
 * Тап по видео (не по кнопке) переключает play/pause; double-tap в левой/правой половине
 * перематывает на −10с/+10с (те же ±10с, что и в [PlayerOverlay]).
 */
@Composable
private fun CompactVideoGestureLayer(
    controller: EmbedVideoController,
    flash: PlayerSeekFlashState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controller.togglePlayPause() },
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
@Suppress("LongParameterList") // Та же тройка состояние/контроллер/аудио-набор, что и у родителя.
@Composable
private fun BoxScope.CompactBelowVideoContent(
    videoHeight: Dp,
    topOffset: Dp,
    onHeightMeasured: (Dp) -> Unit,
    state: EmbedVideoState,
    controller: EmbedVideoController,
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
        val duration = state.durationMs
        if (duration != null) {
            PlayerProgressBar(
                currentTimeMs = state.currentTimeMs,
                durationMs = duration,
                onSeek = { positionMs -> controller.seekTo(positionMs) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
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
