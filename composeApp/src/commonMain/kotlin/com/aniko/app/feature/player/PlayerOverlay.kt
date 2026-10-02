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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.data.voicepin.LocalVoicePinStore
import com.aniko.model.Episode
import com.aniko.model.VoiceType
import com.aniko.player.EmbedVideoController
import com.aniko.player.EmbedVideoState
import com.aniko.player.playerOpensFullscreen
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.EpisodeGrid
import com.aniko.ui.component.VoiceTypeRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Оверлей плеера поверх кадра embed-страницы: P8.T3 (назад, PiP, тап-зона play/pause,
 * прогресс-бар с seek), P8.T4 (баннер «следующая серия через Nс») и P8.T5 (скорость 1.0–2.0).
 *
 * **Живёт в `composeApp`, а не в `:shared:player`, намеренно.** Оверлею нужны и дизайн-токены
 * ([AnixThemeTokens]), и локализация ([LocalStrings]) — то есть `:shared:ui`, а `:shared:player`
 * на него не зависит с Фазы 2. `EmbedPlayerView` остаётся «голым» видео-контейнером, а весь
 * визуальный слой — здесь.
 *
 * **Вызывать только при `EmbedVideoController.isSupported == true`.** Без работающего моста
 * (например, старый системный WebView на Android без нужных фич `androidx.webkit`, см. KDoc
 * `EmbedVideoController.isSupported`) контроллер ничего не знает и ничем не управляет, а
 * нарисованный прогресс-бар был бы враньём — см. [PlayerDesktopControls], чем в этом случае
 * заменяется этот экран. Провал резолва конкретного потока на Desktop (мёртвая
 * ссылка/неподдерживаемый хост) — другой случай, `isSupported` тут ни при чём: деградирует только
 * `bridgeActive` ниже.
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
 * @param onBack выход из экрана плеера. Используется в двух случаях: (1) no-bridge fullscreen
 * (`!state.isVideoFound`) — там стрелка «назад» не может свернуть в compact, потому что
 * `CompactVideoGestureLayer` запирает embed-страницу, а мост не управляет видео; (2) платформы,
 * открывающие плеер сразу в fullscreen ([com.aniko.player.playerOpensFullscreen] == true,
 * Desktop) — там compact-режим не самостоятельное состояние, и сворачивание в него было лишним
 * промежуточным экраном (фикс 2026-09-18, см. KDoc `PlayerTopBar`). В bridge-fullscreen на
 * Android/iOS стрелка сворачивает в compact через [onCollapseFullscreen], поэтому этот колбэк —
 * fallback-выход (единственный выход на iOS без edge-swipe, см. абзац выше).
 * @param onCollapseFullscreen сворачивает fullscreen обратно в компактный режим (P13) — просто
 * смена раскладки без навигации. При `bridgeActive` именно это делает стрелка «назад» на
 * платформах, где compact-режим — самостоятельное состояние ([com.aniko.player.playerOpensFullscreen]
 * == false).
 * @param onNextEpisode переход на следующую серию — кнопка «Следующая серия» в нижней панели
 * (Netflix-раскладка, 2026-10-01). Автопереход в конце серии и авто-отметка «просмотрено» живут в
 * [PlayerScreen] (общие для compact/fullscreen), сюда приходит только карточка [upNext].
 * @param onPrevEpisode переход на предыдущую серию — иконка skip_previous рядом с «Следующей
 * серией» (гейтится [hasPrevEpisode]).
 * @param episodeTitle заголовок топбара («Серия N»), `null` — не рисуется.
 * @param upNext карточка «Следующая серия» с обратным отсчётом; `null` — не показывается. Видна
 * независимо от контролов: титры смотрят без оверлея, и молча перескочить без шанса нажать
 * «Отмена» было бы хуже всего.
 * @param onOpenEpisodesPicker тап по кнопке списка серий в [PlayerTopBar] — сама шторка
 * ([EpisodesSheetOverlay]) рисуется в [PlayerScreen] общей для compact/fullscreen, как
 * [AudioPickerOverlay] (см. её KDoc): одно состояние видимости на оба режима вместо двух копий.
 * Кнопка гейтится непустым списком серий на вызывающей стороне: открывать шторку нечего.
 * @param voiceTypes список озвучек релиза для чипа «Audio» (P13.T10, `PlayerUiState.voiceTypes`).
 * Пустой список прячет чип целиком — переключаться некуда, показывать неактивную кнопку незачем
 * (тот же принцип честного UI, что и у PiP-заглушки, только тут решение — не рисовать вовсе).
 * @param currentVoiceType озвучка текущего источника (`PlayerUiState.currentVoiceType`) — подпись
 * чипа и подсветка выбранной строки в пикере. `null`, пока подбор ещё не завершился.
 * @param onOpenAudioPicker тап по чипу «Audio» (P13) — сам [AudioPickerOverlay] здесь больше не
 * рисуется (см. её KDoc), состояние видимости пикера и выбор строки (`selectVoiceType`) подняты в
 * [PlayerScreen], один пикер общий для compact- и fullscreen-режимов вместо двух независимых копий.
 */
@Suppress("LongParameterList", "LongMethod") // Состояние + контроллер + флаги наличия соседних
// серий + колбэки наружу (назад/prev/next/конец серии/шторка серий) + аудио-пикер (список +
// текущая озвучка + флаг загрузки + колбэк выбора, P13.T10) + modifier. Дробить оверлей на части
// с меньшим числом параметров пришлось бы через общий mutable-объект состояния — это хуже, чем
// счётчик. Тело функции длиннее лимита ровно из-за этого же перечисления layout-секций (топбар/
// центр/баннер/панель/пикер) — каждая уже вынесена в свой composable, короче эта функция уже не
// станет без потери читаемости.
@Composable
fun PlayerOverlay(
    state: EmbedVideoState,
    controller: EmbedVideoController,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    onBack: () -> Unit,
    onCollapseFullscreen: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    episodeTitle: String? = null,
    upNext: UpNextCardState? = null,
    voiceTypes: List<VoiceType> = emptyList(),
    currentVoiceType: VoiceType? = null,
    onOpenAudioPicker: () -> Unit = {},
    qualityLabel: String? = null,
    onOpenQualityPicker: () -> Unit = {},
    speedLabel: String? = null,
    onOpenSpeedPicker: () -> Unit = {},
    onOpenEpisodesPicker: () -> Unit = {},
    onEnterPictureInPicture: (() -> Unit)? = null,
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
    // Реклама хоста (Kodik VAST) — тот же режим, что «видео ещё не найдено»: только «назад» и никакого
    // перехвата касаний, иначе кнопку «Пропустить» рекламы не нажать.
    val bridgeActive = state.isVideoFound && !state.isAdPlaying
    // До нахождения <video> оверлей деградирует до «только назад»: старт делает большой play
    // хоста (trust-gesture), после первого старта класс `aniko-video-found` скрывает его и
    // рабочим UI становится наш (см. KDoc `EmbedVideoBridge` CHROME_HIDE_CSS).
    val controlsShown = controlsVisible && bridgeActive

    // Авто-скрытие только во время воспроизведения: на паузе контролы обязаны оставаться —
    // иначе кнопка play исчезает ровно тогда, когда она единственная нужная на экране.
    LaunchedEffect(controlsShown, interactionTick, state.isPlaying) {
        if (controlsShown && state.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // P16.T9 — вертикальные жесты яркости/громкости. Контроллер уровней берётся здесь же, а не
    // параметром: это фича ровно этого (полноэкранного) слоя, компактному режиму она не нужна.
    val systemLevels = rememberPlayerSystemLevels()
    val levelGesture = rememberPlayerLevelGesture(systemLevels)

    // Жест уровней висит на КОРНЕ оверлея, а не на тап-слое: тап-слой — сосед `Column`'а снизу по
    // z-порядку, и до него события не доходят вовсе (живая проверка: свайп уходил прямо в
    // embed-страницу и открывал собственный регулятор хоста). Корень же есть в пути доставки
    // всегда — он родитель и тап-слоя, и панелей; ребёнок-тап не потребляет движение, поэтому
    // драг доходит до корня и, перешагнув slop, гасит тап (см. KDoc [PlayerLevelGestureState]).
    Box(modifier = modifier.fillMaxSize().then(levelGesture.modifier)) {
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

        // Индикатор уровня — у той стороны, где идёт жест (яркость слева, громкость справа).
        levelGesture.target?.let { target ->
            PlayerLevelIndicator(
                target = target,
                level = levelGesture.level,
                modifier =
                    Modifier
                        .align(
                            if (target == PlayerLevelTarget.BRIGHTNESS) {
                                Alignment.CenterStart
                            } else {
                                Alignment.CenterEnd
                            },
                        ).padding(horizontal = AnixThemeTokens.dimens.spaceL),
            )
        }

        PlayerCenterArea(
            visible = controlsShown,
            state = state,
            controller = controller,
            onInteraction = { interactionTick++ },
            modifier = Modifier.fillMaxSize(),
        )

        // Колонка сама по себе не перехватывает касания (у неё нет pointer-модификаторов), поэтому
        // тапы мимо кнопок проваливаются в слой-перехватчик выше и продолжают прятать контролы.
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // `|| !bridgeActive` — когда управлять нечем, «назад» висит постоянно: это
            // единственный выход с экрана на iOS.
            AnimatedVisibility(visible = controlsShown || !bridgeActive, enter = fadeIn(), exit = fadeOut()) {
                PlayerTopBar(
                    title = episodeTitle,
                    onBack = onBack,
                    onCollapseFullscreen = onCollapseFullscreen,
                    bridgeActive = bridgeActive,
                    onOpenEpisodesPicker = {
                        interactionTick++
                        onOpenEpisodesPicker()
                    },
                    onEnterPictureInPicture = onEnterPictureInPicture,
                    onInteraction = { interactionTick++ },
                )
            }

            // Середина — пустая: центральные кнопки центрируются по КАДРУ (слой ниже), а не по зазору
            // между топбаром и нижней панелью — иначе они уезжали выше центра видео (живая проверка iOS).
            Spacer(modifier = Modifier.weight(1f))

            if (upNext != null) {
                UpNextCard(
                    state = upNext,
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .padding(
                                horizontal = AnixThemeTokens.dimens.spaceM,
                                vertical = AnixThemeTokens.dimens.spaceS,
                            ),
                )
            }

            AnimatedVisibility(visible = controlsShown, enter = fadeIn(), exit = fadeOut()) {
                PlayerBottomPanel(
                    state = state,
                    controller = controller,
                    onInteraction = { interactionTick++ },
                    hasPrevEpisode = hasPrevEpisode,
                    hasNextEpisode = hasNextEpisode,
                    onPrevEpisode = onPrevEpisode,
                    onNextEpisode = onNextEpisode,
                    voiceTypes = voiceTypes,
                    currentVoiceType = currentVoiceType,
                    onOpenAudioPicker = {
                        interactionTick++
                        onOpenAudioPicker()
                    },
                    qualityLabel = qualityLabel,
                    onOpenQualityPicker = {
                        interactionTick++
                        onOpenQualityPicker()
                    },
                    speedLabel = speedLabel,
                    onOpenSpeedPicker = {
                        interactionTick++
                        onOpenSpeedPicker()
                    },
                )
            }
        }
    }
}

/**
 * Стрелка «назад» + кнопка списка серий + PiP-заглушка (P8.T3).
 *
 * Семантика стрелки зависит от [bridgeActive] И платформы: при активном мосте она сворачивает
 * fullscreen в compact ([onCollapseFullscreen]) — но только там, где compact-режим реально
 * существует как самостоятельное состояние ([playerOpensFullscreen] == false, Android/iOS:
 * плеер открывается в compact, fullscreen — расширение поверх него). На платформах, где плеер
 * открывается сразу в fullscreen (Desktop), compact-панель пользователь никогда не видел и не
 * выбирал — сворачивание в неё первым «назад» выглядело как лишний промежуточный экран с
 * непонятными контролами (живой фидбек 2026-09-18), поэтому там стрелка сразу выходит из
 * экрана ([onBack]). При неактивном мосте — тоже [onBack], чтобы не запереть пользователя в
 * неуправляемом compact-режиме.
 */
@Suppress("LongParameterList") // Плоский набор колбэков без бизнес-логики: назад/collapse +
// bridgeActive (семантика стрелки) + открытие шторки серий + PiP + счётчик взаимодействия —
// тот же случай, что и у OverlayIconButton (см. её KDoc); группировка ради счётчика — косвенность.
@Composable
private fun PlayerTopBar(
    title: String?,
    onBack: () -> Unit,
    onCollapseFullscreen: () -> Unit,
    bridgeActive: Boolean,
    onOpenEpisodesPicker: () -> Unit,
    onEnterPictureInPicture: (() -> Unit)?,
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
                if (bridgeActive && !playerOpensFullscreen()) {
                    onCollapseFullscreen()
                } else {
                    onBack()
                }
            },
        )
        // Заголовок «Серия N» — как у взрослых плееров: всегда понятно, что сейчас играет.
        Text(
            text = title.orEmpty(),
            color = OVERLAY_CONTENT_COLOR,
            fontSize = TOP_BAR_TITLE_FONT_SIZE,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = dimens.spaceXs),
        )
        // Шторка со списком серий — общая с compact-режимом ([EpisodesSheetOverlay] в
        // [PlayerScreen]); сам оверлей только сообщает о тапе. Порядок кнопок: список серий
        // ближе к центру, PiP — на самом краю.
        OverlayIconButton(
            iconName = "playlist_play",
            contentDescription = strings.playerSelectEpisode,
            onClick = onOpenEpisodesPicker,
        )
        // Кнопка рисуется только там, где PiP реально работает (P16.T8): на iOS оверлей не может
        // ни войти в PiP, ни нарисовать в нём свои кнопки — вместо неработающей кнопки её нет
        // вовсе (тот же принцип честного UI, что у чипа Audio с одной озвучкой).
        if (onEnterPictureInPicture != null) {
            OverlayIconButton(
                iconName = "picture_in_picture_alt",
                contentDescription = strings.playerPictureInPicture,
                onClick = {
                    onInteraction()
                    onEnterPictureInPicture()
                },
            )
        }
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
                isBuffering = state.isBuffering,
                controller = controller,
                onInteraction = onInteraction,
            )
        }
    }
}

/**
 * Центральная тап-зона: −10 с / play-pause / +10 с (P8.T3, P16.T10). Переключение серий —
 * в нижней панели (Netflix-раскладка 2026-10-01), центр отдан только управлению текущим видео.
 *
 * Рисуется только когда мост держит `<video>` (см. `bridgeActive` в [PlayerOverlay]) — поэтому
 * отдельного «неактивного» состояния у кнопок нет: если команду некому исполнить, кнопок просто
 * не существует, а не они «нажимаются и ничего не делают».
 */
@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    controller: EmbedVideoController,
    onInteraction: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(CENTER_CONTROLS_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // P16 (2026-09-10): кнопка −30 убрана по решению пользователя — остаётся только ±10.
        OverlayIconButton(
            iconName = "replay_10",
            filled = true,
            contentDescription = strings.playerSeekBackward,
            onClick = {
                onInteraction()
                controller.seekBy(-PLAYER_SEEK_STEP_MS)
            },
        )
        // Во время буферизации на месте ▶/⏸ — спиннер [PlayerStartIndicator] (как у Netflix):
        // кнопка прозрачна, но держит место, чтобы ряд не схлопывался.
        OverlayIconButton(
            iconName = if (isPlaying) "pause" else "play_arrow",
            filled = true,
            contentDescription = if (isPlaying) strings.playerPause else strings.playerPlay,
            iconSize = PLAY_BUTTON_ICON_SIZE,
            modifier = Modifier.alpha(if (isBuffering) 0f else 1f),
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
 * Раскладка — `Column { PlayerProgressBar; Row { Row(weight, horizontalScroll) { пилюли }; ⏮; «Следующая ⏭» } }`
 * (Netflix-раскладка 2026-10-01): пилюли слева (скроллятся при переполнении), навигация по сериям
 * справа. Порядок пилюль идентичен compact-строке: audio → sub → quality → speed.
 *
 * Прогресс-бар рисуется **только** при `durationMs != null` — до события `loadedmetadata`
 * длительности не существует вообще (`duration = NaN`), и шкала «от нуля до неизвестно чего»
 * была бы выдумкой. Пока её нет — панель показывает только пилюли.
 *
 * Качество (P16-фикс 2026-09-09): ранний CUT (P13.T9) пересмотрен после живой сессии —
 * страница kodikplayer.com идентична для /720p|480p|1080p, качество выбирается КЛИЕНТСКИ в его
 * `quality-dropdown` (flowplayer), сегмент URL декоративен. Чип «Качество» появляется у хостов с
 * клиентским переключением (Kodik: [QualityPickerOverlay] → [EmbedVideoController.setQuality],
 * мост кликает пункт dropdown; см. `EmbedVideoBridge.switchQuality`). На хостах без уровней
 * (Sibnet/VideoJS без плагина) чип не рисуется — менять нечего, честный UI.
 *
 * Пилюли — [PlayerPillChip] (тот же компонент, что в compact-режиме), а не M3
 * `FilterChip`/`AssistChip`: мокап рисует их нейтральными, без акцентного selected-цвета.
 */

@Composable
private fun PlayerQualityChip(
    qualityLabel: String?,
    onInteraction: () -> Unit,
    onOpen: () -> Unit,
) {
    if (qualityLabel == null) return
    val strings = LocalStrings.current
    PlayerPillChip(
        label = strings.playerQualityChip(qualityLabel),
        onClick = {
            onInteraction()
            onOpen()
        },
    )
}

@Suppress("LongParameterList", "LongMethod") // Линейная раскладка панели: прогресс + пилюли
// (озвучка/качество/скорость) + навигация по сериям, каждая секция уже вынесена; параметры — их
// состояния и колбэки. Группировать в объект ради счётчика — косвенность без назначения.
@Composable
private fun PlayerBottomPanel(
    state: EmbedVideoState,
    controller: EmbedVideoController,
    onInteraction: () -> Unit,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        PlayerProgressBar(
            currentTimeMs = state.currentTimeMs,
            durationMs = state.durationMs,
            onSeek = { positionMs ->
                onInteraction()
                controller.seekTo(positionMs)
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
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
                PlayerQualityChip(
                    qualityLabel = qualityLabel,
                    onInteraction = onInteraction,
                    onOpen = onOpenQualityPicker,
                )
                if (speedLabel != null) {
                    PlayerPillChip(
                        label = speedLabel,
                        onClick = {
                            onInteraction()
                            onOpenSpeedPicker()
                        },
                    )
                }
            }
            EpisodeNavButtons(
                hasPrevEpisode = hasPrevEpisode,
                hasNextEpisode = hasNextEpisode,
                onPrevEpisode = {
                    onInteraction()
                    onPrevEpisode()
                },
                onNextEpisode = {
                    onInteraction()
                    onNextEpisode()
                },
            )
        }
    }
}

/**
 * Навигация по сериям справа в нижней панели, как у Netflix: «предыдущая» иконкой (нужна реже),
 * «Следующая серия» — подписанной кнопкой. Крайние серии просто не рисуют кнопку.
 */
@Composable
private fun EpisodeNavButtons(
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
) {
    val strings = LocalStrings.current
    if (hasPrevEpisode) {
        OverlayIconButton(
            iconName = "skip_previous",
            filled = true,
            contentDescription = strings.playerPrevEpisode,
            iconSize = EPISODE_NAV_ICON_SIZE,
            onClick = onPrevEpisode,
        )
    }
    if (hasNextEpisode) {
        NextEpisodeButton(onClick = onNextEpisode)
    }
}

/** Подписанная кнопка «Следующая серия ⏭» нижней панели fullscreen (Netflix-раскладка). */
@Composable
private fun NextEpisodeButton(onClick: () -> Unit) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(dimens.cornerPill)
    Row(
        modifier =
            Modifier
                .heightIn(min = dimens.minTouchTarget)
                .clip(shape)
                .background(Color.White.copy(alpha = NEXT_EPISODE_BUTTON_ALPHA), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Text(
            text = strings.playerNextEpisode,
            color = OVERLAY_CONTENT_COLOR,
            fontSize = NEXT_EPISODE_BUTTON_FONT_SIZE,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        AnixIcon(
            name = "skip_next",
            contentDescription = null,
            filled = true,
            tint = OVERLAY_CONTENT_COLOR,
            modifier = Modifier.size(EPISODE_NAV_ICON_SIZE),
        )
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
@Suppress("LongMethod") // iOS-like редизайн (2026-09-11) добавил grab handle (6 строк) поверх
// уже существующего тела — разбиение ради счётчика строк добавило бы косвенность, тот же
// аргумент, что уже применяется в проекте для линейных, но длинных composable.
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
    val pinStore = koinInject<LocalVoicePinStore>()
    val pinnedIds by pinStore.pinnedIds().collectAsStateWithLifecycle(initialValue = emptySet())
    val sorted =
        remember(voiceTypes, pinnedIds) {
            // Тот же порядок, что в Detail (ReleaseEpisodesSection): локальные пины → серверные
            // pinned → остальные (ревью Волны 3, P2).
            voiceTypes.sortedWith(
                compareByDescending<VoiceType> { it.id in pinnedIds }.thenByDescending { it.pinned },
            )
        }
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
                // iOS-like редизайн (2026-09-11, полный HIG-паттерн): grab handle — тот же
                // приём, что [OptionSheetOverlay] в `QualityPicker.kt` (см. её KDoc).
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = AUDIO_PICKER_GRAB_HANDLE_WIDTH, height = AUDIO_PICKER_GRAB_HANDLE_HEIGHT)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AUDIO_PICKER_GRAB_HANDLE_ALPHA),
                                RoundedCornerShape(dimens.cornerPill),
                            ),
                )
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
            // макету (`showDubPicker`), не голая иконка без фона. Круг и центровка иконки —
            // общий [PlayerIconCircle]; тон иконки — тема (панель пикера светлая/тёмная по теме, а не
            // белая поверх кадра), поэтому tint не дефолтный белый оверлея, а озвучку кнопки
            // (`closeContentDescription`) по-прежнему несёт сама иконка, как и до выноса круга.
            PlayerIconCircle(
                iconName = "close",
                diameter = AUDIO_PICKER_CLOSE_BUTTON_SIZE,
                iconSize = AUDIO_PICKER_CLOSE_ICON_SIZE,
                background = colors.overlay08,
                filled = true,
                tint = LocalContentColor.current,
                contentDescription = strings.closeContentDescription,
            )
        }
    }
}

private const val AUDIO_PICKER_MAX_HEIGHT_FRACTION = 0.6f
private val AUDIO_PICKER_TITLE_FONT_SIZE = 17.sp
private val AUDIO_PICKER_CLOSE_BUTTON_SIZE = 34.dp
private val AUDIO_PICKER_CLOSE_ICON_SIZE = 24.dp
private val AUDIO_PICKER_GRAB_HANDLE_WIDTH = 36.dp
private val AUDIO_PICKER_GRAB_HANDLE_HEIGHT = 5.dp
private const val AUDIO_PICKER_GRAB_HANDLE_ALPHA = 0.4f

/**
 * Шторка со списком серий поверх кадра плеера — выбор любой серии без выхода из плеера, по
 * паттерну [AudioPickerOverlay] (полноэкранный скрим + прижатая снизу панель с grab-handle и
 * заголовком; тап по скриму закрывает). Список — тот же переиспользуемый [EpisodeGrid], что и на
 * Title Detail (`ReleaseEpisodesSection.kt`), с подсветкой текущей серии ([currentPosition]) и
 * watched-отметками: [episodes] приходят уже смерженными с живой картой отметок — мердж делается
 * на вызывающей стороне ([PlayerScreen]), потому что источник отметок (`watchedPositions`) живёт
 * в её стейте.
 *
 * `internal`, не `private` — одна шторка общая для compact- и fullscreen-режимов: состояние
 * видимости поднято в [PlayerScreen] (тот же аргумент, что у [AudioPickerOverlay], см. её KDoc).
 *
 * @param episodes серии текущего источника с актуальными `isWatched`; пустой список сюда не
 * должен попадать — кнопку открытия гейтит непустой список (честный UI, как у чипа Audio).
 * @param currentPosition `position` реально играющей серии (`PlayerUiState.positionKey`) — рамка
 * текущей ячейки в сетке.
 * @param onSelect выбор серии: навигация на тот же маршрут с её `position` (делает [PlayerScreen]),
 * шторку закрывает вызывающая сторона после колбэка.
 */
@Composable
internal fun EpisodesSheetOverlay(
    episodes: List<Episode>,
    currentPosition: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    // Тот же лимит высоты, что у аудио-пикера: панель не выше 60% экрана, содержимое скроллится.
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
                modifier = Modifier.padding(dimens.spaceM).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = AUDIO_PICKER_GRAB_HANDLE_WIDTH, height = AUDIO_PICKER_GRAB_HANDLE_HEIGHT)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AUDIO_PICKER_GRAB_HANDLE_ALPHA),
                                RoundedCornerShape(dimens.cornerPill),
                            ),
                )
                AudioPickerHeader(title = strings.playerEpisodesTitle, onDismiss = onDismiss)
                // EpisodeGrid — не ленивый FlowRow, но одна сетка на реалистичное число серий
                // (десятки ячеек) меряется мгновенно, а bounded-высота панели + verticalScroll
                // дают скролл для длинных тайтлов — тот же ограничитель высоты, что в пикере
                // озвучки, только без LazyColumn.
                EpisodeGrid(
                    episodes = episodes,
                    currentPosition = currentPosition,
                    onEpisodeClick = { episode -> onSelect(episode.position) },
                )
            }
        }
    }
}

private val TOP_BAR_TITLE_FONT_SIZE = 16.sp
private val CENTER_CONTROLS_SPACING = 40.dp
private val EPISODE_NAV_ICON_SIZE = 24.dp
private const val NEXT_EPISODE_BUTTON_ALPHA = 0.16f
private val NEXT_EPISODE_BUTTON_FONT_SIZE = 13.sp

/** Кнопка-иконка оверлея: белая на кадре видео (см. [OVERLAY_CONTENT_COLOR]). [enabled] = false
 *  гасит иконку до M3-альфы disabled (0.38) — кнопка остаётся на месте, но читается неактивной.
 *  `LongParameterList`: плоский декоративный набор (иконка/подпись/клик/filled/размер/enabled),
 *  группировать нечего. */
@Suppress("LongParameterList")
@Composable
private fun OverlayIconButton(
    iconName: String,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    iconSize: Dp = DEFAULT_ICON_SIZE,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val buttonDescription = contentDescription
    IconButton(
        onClick = onClick,
        enabled = enabled,
        // Тач-таргет не меньше рекомендованного минимума даже у мелких иконок — оверлей
        // нажимают вслепую, глядя на видео, а не на кнопку. `clearAndSetSemantics` вместо
        // contentDescription на Icon (Фаза 11, T9, подтверждено на устройстве): IconButton не
        // сливает его в свой кликабельный узел — тот же паттерн, что и остальные M3-компоненты
        // этой фазы (см. AnixNavigationBar.kt/ChipRow.kt/LibraryScreen.kt).
        modifier =
            modifier
                .size(maxOf(dimens.minTouchTarget, iconSize + dimens.spaceM))
                .clearAndSetSemantics { this.contentDescription = buttonDescription },
    ) {
        AnixIcon(
            name = iconName,
            contentDescription = null,
            filled = filled,
            // Явный tint: у M3 IconButton disabled-альфа идёт через LocalContentColor, а наш
            // цвет задан жёстко белым (оверлей всегда поверх тёмного кадра) — без ручной альфы
            // неактивная кнопка визуально не отличалась бы от активной.
            tint = if (enabled) OVERLAY_CONTENT_COLOR else OVERLAY_CONTENT_COLOR.copy(alpha = OVERLAY_DISABLED_ALPHA),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** M3-альфа контента в disabled-состоянии ([androidx.compose.material3] применяет ту же константу).
 *  `internal` — та же альфа нужна компактному ряду prev/next ([CompactEpisodeSkipButton]). */
internal const val OVERLAY_DISABLED_ALPHA = 0.38f

/** Клик без ripple — на слое поверх видео рябь во весь экран читалась бы как дефект отрисовки. */
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
