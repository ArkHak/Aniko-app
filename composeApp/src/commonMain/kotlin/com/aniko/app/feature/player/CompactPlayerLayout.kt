package com.aniko.app.feature.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.aniko.model.VoiceType
import com.aniko.player.EmbedVideoController
import com.aniko.player.EmbedVideoState
import com.aniko.ui.component.AnixIcon
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
    state: EmbedVideoState,
    controller: EmbedVideoController,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
    voiceTypes: List<VoiceType>,
    currentVoiceType: VoiceType?,
    onOpenAudioPicker: () -> Unit,
) {
    CompactVideoOverlay(
        videoHeight = videoHeight,
        isPlaying = state.isPlaying,
        controller = controller,
        onBack = onBack,
        onEnterFullscreen = onEnterFullscreen,
    )
    CompactBelowVideoContent(
        videoHeight = videoHeight,
        state = state,
        controller = controller,
        voiceTypes = voiceTypes,
        currentVoiceType = currentVoiceType,
        onOpenAudioPicker = onOpenAudioPicker,
    )
}

/** Топбар (назад/fullscreen) + центральная play-pause — часть [CompactPlayerChrome], те же границы
 *  (`videoHeight`), что и у самого видео. Вынесена отдельно (detekt `LongMethod`). */
@Composable
private fun BoxScope.CompactVideoOverlay(
    videoHeight: Dp,
    isPlaying: Boolean,
    controller: EmbedVideoController,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Box(modifier = Modifier.fillMaxWidth().height(videoHeight).align(Alignment.TopStart)) {
        // Перехватывающий слой (тот же приём, что и в PlayerOverlay.kt, см. её KDoc про "перехват
        // касаний"): без него нативный UI чужой embed-страницы под нами может забирать тапы себе
        // раньше, чем они дойдут до наших кнопок — на живой проверке кнопка "На весь экран" в
        // правом верхнем углу видео не реагировала ни разу, пока не появился этот слой. Тап по
        // видео (не по кнопке) переключает play/pause — тот же жест, что и в мокапе
        // (`onClick="togglePlay"` на всей видео-области `showPlayer`).
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickableNoIndication { controller.togglePlayPause() },
        )
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

        IconButton(
            onClick = { controller.togglePlayPause() },
            modifier = Modifier.align(Alignment.Center).size(dimens.minTouchTarget * PLAY_BUTTON_SCALE),
        ) {
            AnixIcon(
                name = if (isPlaying) "pause" else "play_arrow",
                filled = true,
                contentDescription = if (isPlaying) strings.playerPause else strings.playerPlay,
                tint = Color.White,
                modifier = Modifier.size(dimens.minTouchTarget),
            )
        }
    }
}

/** Название/прогресс/чипы под видео — не Column-обёртка вокруг видео (см. KDoc
 *  [CompactPlayerChrome] про единственный call site [com.aniko.player.EmbedPlayerView]), а
 *  отдельный узел, сдвинутый вниз на `videoHeight` через `padding(top = …)`. Вынесена отдельно
 *  (detekt `LongMethod`). */
@Suppress("LongParameterList") // Та же тройка состояние/контроллер/аудио-набор, что и у родителя.
@Composable
private fun BoxScope.CompactBelowVideoContent(
    videoHeight: Dp,
    state: EmbedVideoState,
    controller: EmbedVideoController,
    voiceTypes: List<VoiceType>,
    currentVoiceType: VoiceType?,
    onOpenAudioPicker: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(top = videoHeight)
                .padding(dimens.spaceM),
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
            PLAYBACK_RATES.forEach { rate ->
                PlayerPillChip(
                    label = strings.playerSpeedValue(rate.formatRate()),
                    selected = state.playbackRate.matches(rate),
                    onClick = { controller.setPlaybackRate(rate) },
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
    val shape = RoundedCornerShape(dimens.cornerM)
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
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CompactOverlayIconButton(
    iconName: String,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(dimens.minTouchTarget)
                .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        AnixIcon(name = iconName, contentDescription = null, filled = filled, tint = Color.White)
    }
}

private const val PLAY_BUTTON_SCALE = 1.3f
private const val PILL_CHIP_ALPHA = 0.07f
private const val PILL_CHIP_SELECTED_ALPHA = 0.16f
private val PILL_CHIP_FONT_SIZE = 12.sp
