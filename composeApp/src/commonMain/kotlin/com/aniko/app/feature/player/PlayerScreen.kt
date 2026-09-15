package com.aniko.app.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.VideoHost
import com.aniko.player.EmbedPlayerView
import com.aniko.player.HideSystemBarsEffect
import com.aniko.player.LockLandscapeOrientationEffect
import com.aniko.player.PlaybackSource
import com.aniko.player.rememberEmbedVideoController
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран плеера: на весь экран рендерит embed-страницу источника через [EmbedPlayerView].
 *
 * Архитектурное решение фазы 5 (не пересматривать): всё воспроизведение — через embed (WebView),
 * независимо от хоста. Нативный `PlayerController`/`VideoSurface` из `:shared:player` здесь не
 * используются — это задел на будущее.
 *
 * Фаза 8 добавила поверх embed'а JS-мост (`rememberEmbedVideoController`): он даёт
 * play/pause/seek/скорость и состояние `<video>` внутри чужой страницы. С ветки
 * `feature/desktop-video-player` — на всех трёх платформах (Android/iOS через `androidx.webkit`/
 * `WKWebView`, Desktop через встроенный JCEF, см. `EmbedPlayer.desktop.kt`).
 *
 * **P13 — компактный режим по умолчанию + переключение на fullscreen.** Сверка с мокапом Claude
 * Design (`showPlayer`) показала, что референс НЕ полноэкранный: видео закреплено сверху
 * фиксированной областью, под ним в обычном потоке — метаданные/прогресс/чипы озвучки-скорости,
 * не в auto-hide оверлее. `isFullscreen` (по умолчанию `false`) переключает между
 * [CompactPlayerChrome] (chrome компактного режима) и [PlayerOverlay] (старый полноэкранный режим
 * с авто-скрытием, P8.T3-T5) — **`EmbedPlayerView` вызывается РОВНО ОДИН РАЗ** вне этого
 * ветвления (см. KDoc [CompactPlayerChrome] про то, почему второй call site оборвал бы
 * воспроизведение при переключении). `LockLandscapeOrientationEffect()` вызывается, пока
 * `isFullscreen == true` (см. комментарий у вызова ниже) — принудительно поворачивает устройство
 * в альбомную ориентацию на Android, на iOS остаётся честным CUT (см. KDoc самой функции).
 *
 * **Двухшаговый системный back (player-triple-design, §2.2).** При открытом пикере озвучки
 * back закрывает пикер; при `isFullscreen && videoState.isVideoFound` — сворачивает плеер
 * в компактный режим. Гейт `isVideoFound` обязателен: без моста compact-режим запирает
 * embed-страницу за жестовым слоем `CompactVideoGestureLayer`, поэтому back в no-bridge
 * fullscreen должен оставаться выходом из экрана, а не collapse.
 *
 * **Единственная развилка на этом экране** — обычный `if` по `controller.isSupported`, без
 * `expect/actual`: сам флаг уже разруливает окружение за нас (см. его KDoc). На всех трёх
 * платформах он теперь `true` в штатном случае (на Desktop — безусловно, статически, см. KDoc
 * `EmbedVideoController.desktop.kt`) — ветка `isSupported == false` остаётся честным
 * деградационным путём для случаев вроде старого системного WebView на Android без нужных фич
 * `androidx.webkit` (тогда видео физически не появится, и полноценный оверлей был бы враньём).
 * Провал резолва конкретного потока (мёртвая ссылка/неподдерживаемый хост на Desktop) — другой,
 * менее суровый случай: `isSupported` остаётся `true`, деградирует только
 * `EmbedVideoState.isVideoFound` внутри уже показанного [PlayerOverlay] (см. её KDoc про
 * `bridgeActive`), эта развилка сюда не относится.
 * - `isSupported == true` → компактный режим по умолчанию + [PlayerOverlay] по кнопке "На весь
 *   экран": назад/PiP (Android)/тап-зона play-pause/прогресс-бар с seek (P8.T3), баннер
 *   «следующая серия через Nс» (P8.T4), скорость 1.0–2.0 (P8.T5), клавиатурные шорткаты
 *   (`playerKeyboardShortcuts`, Desktop-only, P8.T7) и авто-отметка «просмотрено» на подходе к
 *   концу серии (P8.T8);
 * - `isSupported == false` → [PlayerDesktopControls]: только две кнопки, «следующая серия» и
 *   ручная отметка просмотра — честный фолбэк без позиции воспроизведения, когда моста в
 *   принципе нет (а не Desktop-специфичная ветка, как было до пересмотра P8.T1).
 *
 * @param onBack закрыть плеер. Приходит параметром, а не берётся из
 * [com.aniko.app.navigation.LocalTitleNavigator]: `TitleNavigator.back()` на wide-экранах сначала
 * разбирает стек detail-панелей, а плеер — всегда полноэкранный маршрут `NavController` поверх
 * этих панелей (см. KDoc `TitleNavigator.openPlayer`), и «назад» из него обязан снимать именно
 * маршрут. Открытие следующей серии, наоборот, идёт через навигатор — там `openPlayer` и так
 * бьёт ровно в `NavController`.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // 4 параметра маршрута
// задаются `AnixDestination.Player`; сложность — несколько независимых эффектов (resume P16.T7,
// сохранение позиции, аудио-пикер, next-episode) в одном экране, вынос добавил бы косвенность.
// и схлопнуть их в data-класс нельзя без изменения контракта навигации; остальные три —
// стандартная тройка экрана (onBack + modifier + viewModel). Тело функции чуть перевалило за лимит
// после P13.T10/P13 (compact/fullscreen) — исчерпывающий `when` по состоянию загрузки плюс
// развилка Android/iOS-против-Desktop и режим compact/fullscreen не резались на части без
// протаскивания половины локальных `val` (`source`, `controller`, `openNextEpisode`) наружу.
@Composable
fun PlayerScreen(
    releaseId: Int,
    sourceId: Int,
    position: Int,
    hostKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val host = VideoHost.fromKey(hostKey)
    LaunchedEffect(releaseId, sourceId, position, hostKey) {
        viewModel.load(releaseId, sourceId, position, host)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val navigator = LocalTitleNavigator.current
    // `state.isFullscreen`, не `remember` здесь — см. KDoc [PlayerUiState.isFullscreen] про то,
    // почему это состояние обязано жить в ViewModel: реальный поворот экрана нередко пересекает
    // границу `AnixWindowSize`, из-за чего `AdaptiveScaffold` целиком пересобирает поддерево с
    // этим экраном и любой `remember` в нём стирается, хотя `Activity` не пересоздаётся.
    val isFullscreen = state.isFullscreen
    // Общий на compact/fullscreen пикер озвучки (P13) — см. KDoc [AudioPickerOverlay] про то,
    // почему состояние здесь, а не внутри [PlayerOverlay].
    var showAudioPicker by remember { mutableStateOf(false) }

    // P16.T8 — PiP. Флаг поднят сюда, а не живёт в ветке embed-источника, потому что от него
    // зависит и поворот экрана, и обе раскладки; сам контроллер PiP создаётся ниже, где уже есть
    // `controller` (PiP-кнопки — это команды моста, без моста их некуда слать).
    var pipActive by remember { mutableStateOf(false) }

    if (isFullscreen && !pipActive) {
        // Принудительный поворот в альбомную ориентацию, пока открыт fullscreen (P13). В PiP-окне
        // эффект выключен: ориентацию там задаёт система (окно 16:9), а запрос поворота Activity
        // из PiP-режима систему только дёргает.
        LockLandscapeOrientationEffect()
        // Иммерсивный режим (ревью замечание #3): системные часы/батарея скрыты, пока плеер
        // полноэкранный — тот же guard, что и у ориентации (PiP-окно маленькое, не должно
        // скрывать системные панели для всего экрана).
        HideSystemBarsEffect()
    }

    // color = Color.Black: найдено живым запуском на iOS-симуляторе (не видно по коду/detekt/
    // компиляции) — без явного цвета Surface брал MaterialTheme.colorScheme.surface (после
    // Track A — светлый в light-теме), а PlayerPillChip/CompactOverlayIconButton/
    // CompactPlayPauseButton рисуют белый текст/иконки на предположении "плеер всегда на чёрном
    // фоне", как в макете (`showPlayer` root — `background:#000`, вне зависимости от темы
    // приложения) — получался невидимый белый текст на белом/светлом фоне под видео.
    Surface(
        modifier = modifier.fillMaxSize().testTag(AnixTestTags.PLAYER_SCREEN_ROOT),
        color = Color.Black,
        // contentColorFor(Color.Black) не резолвится ни в один слот темы (это не М3-роль) и
        // молча оставляет прежний ambient LocalContentColor — на светлой теме это тёмный
        // текст, что дало бы то же самое невидимое сочетание для AnixLoadingBox/AnixErrorBox
        // (state.isLoading/state.error), только тёмный-на-чёрном вместо белого-на-белом.
        contentColor = Color.White,
    ) {
        when {
            state.isLoading -> AnixLoadingBox(modifier = Modifier.fillMaxSize())

            state.error != null ->
                AnixErrorBox(
                    message = state.error.toMessage(strings),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

            else -> {
                val source = state.source
                if (source is PlaybackSource.Embed) {
                    // Мост к `<video>` внутри чужой embed-страницы: даёт play/pause/seek/скорость
                    // на Android, iOS и Desktop (JCEF, `feature/desktop-video-player`).
                    val controller = rememberEmbedVideoController(source.url)
                    val videoState by controller.state.collectAsStateWithLifecycle()
                    val pictureInPicture = rememberPlayerPictureInPicture(controller)
                    LaunchedEffect(pictureInPicture) {
                        pictureInPicture.isActive.collect { pipActive = it }
                    }

                    // P16-фикс 2026-09-09 — качество видео: только у хостов с клиентским
                    // переключением (Kodik/flowplayer quality-dropdown). Список пуст — чип не
                    // рисуется вовсе (честный UI, как у Audio-пикера с одной озвучкой).
                    // Качества берём у хоста (мост отдаёт то, что объявляет его меню), пока хост
                    // молчит — фолбэк по домену (Kodik: 720p/480p), затем — из embed-URL.
                    val qualityOptions =
                        videoState.availableQualities.ifEmpty {
                            playerEmbedQualities(source.url)
                        }
                    var manualQuality by remember(source.url) { mutableStateOf<String?>(null) }
                    val currentQuality =
                        manualQuality ?: videoState.currentQuality ?: currentEmbedQuality(source.url)
                    var showQualityPicker by remember { mutableStateOf(false) }
                    // Скорость — одним табом (как качество, P16 2026-09-10).
                    val speedRate = videoState.playbackRate
                    var showSpeedPicker by remember { mutableStateOf(false) }

                    // Back в fullscreen работает в два шага: сначала закрыть пикер озвучки,
                    // потом — свернуть в compact. Пикер добавлен позже в композиции, поэтому
                    // его BackHandler побеждает при `showAudioPicker == true`.
                    BackHandler(enabled = !showAudioPicker && isFullscreen && videoState.isVideoFound) {
                        viewModel.setFullscreen(false)
                    }
                    BackHandler(enabled = showAudioPicker) {
                        showAudioPicker = false
                    }

                    // Переход на следующую серию — навигация на тот же маршрут с `position + 1`.
                    // `TitleNavigator.openPlayer` теперь ЗАМЕНЯЕТ текущий Player-маршрут
                    // (`popUpTo<Player> { inclusive = true }`, фикс 2026-09-08 «два плеера
                    // дублируются»): экран пересоздастся, `PlayerViewModel.load` увидит новый
                    // `LoadKey` и перезапустит цепочку. В back stack всегда ровно один Player —
                    // «назад» из любой серии возвращает на экран, откуда открыли плеер
                    // (детали/список), а не копится стек из серий.
                    val openNextEpisode = { navigator.openPlayer(releaseId, sourceId, position + 1, host) }

                    if (controller.isSupported) {
                        // P16.T7 — сохранение позиции воспроизведения в `LocalPlayerPositionStore`
                        // (через `PlayerViewModel`, который знает ключ текущей серии). Троттлинг
                        // ~2с реализован ручным циклом delay(), а не `Flow.sample` — тот же приём,
                        // что уже применяется по всему плееру ([PlayerOverlay]/[PlayerSeekFeedback]
                        // для авто-скрытия контролов), не тянет `@OptIn(FlowPreview::class)`.
                        LaunchedEffect(releaseId, sourceId, position, controller) {
                            while (isActive) {
                                delay(POSITION_SAVE_THROTTLE_MS)
                                val snapshot = controller.state.value
                                if (snapshot.isPlaying) {
                                    viewModel.onPlaybackPositionChanged(snapshot.currentTimeMs, snapshot.durationMs)
                                }
                            }
                        }
                        // Финальное сохранение по переходу playing→paused: троттлинг выше молчит,
                        // пока `isPlaying == false`, а именно на паузе теряется самая свежая позиция.
                        LaunchedEffect(releaseId, sourceId, position, controller) {
                            var wasPlaying = controller.state.value.isPlaying
                            controller.state.collect { snapshot ->
                                if (wasPlaying && !snapshot.isPlaying) {
                                    viewModel.onPlaybackPositionChanged(snapshot.currentTimeMs, snapshot.durationMs)
                                }
                                wasPlaying = snapshot.isPlaying
                            }
                        }
                        // Финальное сохранение по dispose (уход с экрана/смена серии) — то, что
                        // не успел троттлинг выше.
                        DisposableEffect(releaseId, sourceId, position, controller) {
                            onDispose {
                                val snapshot = controller.state.value
                                // Явный ключ: к моменту dispose (смена серии/озвучки) loadedKey в VM
                                // может указывать уже на новую серию (ревью Волны 3, P3).
                                viewModel.onControllerDisposed(
                                    releaseId = releaseId,
                                    sourceId = sourceId,
                                    position = position,
                                    currentMs = snapshot.currentTimeMs,
                                    durationMs = snapshot.durationMs,
                                )
                            }
                        }
                        // P16.T7 — «Продолжить» из resume-диалога: перемотка откладывается до
                        // момента, когда мост реально найдёт `<video>` (иначе `seekTo` уйдёт в
                        // никуда — команда не подтверждается, см. KDoc [EmbedVideoController]).
                        LaunchedEffect(state.pendingSeekToMs, videoState.isVideoFound) {
                            val pendingSeekMs = state.pendingSeekToMs
                            if (pendingSeekMs != null && videoState.isVideoFound) {
                                controller.seekTo(pendingSeekMs)
                                viewModel.onResumeSeekConsumed()
                            }
                        }
                        // P16.T8 — синхронизация PiP: авто-вход только когда есть чем управлять
                        // (fullscreen + найденное видео + идёт игра), иконка play/pause — по
                        // фактическому состоянию. `DisposableEffect` — снять авто-вход при уходе
                        // с экрана, иначе «домой» из другого экрана уводило бы в PiP пустой плеер.
                        LaunchedEffect(isFullscreen, videoState.isVideoFound, videoState.isPlaying) {
                            pictureInPicture.setPlaying(videoState.isPlaying)
                            pictureInPicture.setAutoEnterEnabled(
                                isFullscreen && videoState.isVideoFound && videoState.isPlaying,
                            )
                        }
                        DisposableEffect(pictureInPicture) {
                            onDispose { pictureInPicture.setAutoEnterEnabled(false) }
                        }

                        // `BoxWithConstraints` (SubcomposeLayout) здесь НЕ подходит — живая
                        // проверка показала, что её содержимое переставало реагировать на смену
                        // `isFullscreen` после того, как внутри уже был смонтирован `EmbedPlayerView`
                        // (AndroidView/WebView): состояние менялось (подтверждено логом в обработчике
                        // клика), но ветка `if (isFullscreen)` внутри `BoxWithConstraints` продолжала
                        // видеть старое значение кадр за кадром — похоже на известный класс проблем
                        // пересборки `SubcomposeLayout` рядом с interop-`AndroidView`. Обычный `Box`
                        // + ширина экрана из [LocalWindowInfo] вместо `maxWidth`/`maxHeight` эту
                        // проблему не воспроизводит.
                        val screenWidth = LocalWindowInfo.current.containerDpSize.width
                        val screenHeight = LocalWindowInfo.current.containerDpSize.height
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    // P8.T7 — space/←→/↑↓, только Desktop (см. KDoc
                                    // `playerKeyboardShortcuts`); на Android/iOS — no-op.
                                    .playerKeyboardShortcuts(controller, enabled = controller.isSupported),
                        ) {
                            val targetVideoHeight =
                                if (isFullscreen) screenHeight else screenWidth / COMPACT_VIDEO_ASPECT_RATIO
                            // Анимированный переход, а не мгновенный скачок высоты: резкий ресайз
                            // Android-`WebView` (Chromium) на переходе compact->fullscreen ронял
                            // системный `libmonochrome_64.so` нативным SIGSEGV на живой проверке
                            // (эмулятор Pixel_6_Pro_API_33/Android 13) — плавная анимация даёт
                            // рендереру кадры на промежуточных размерах вместо одного скачка.
                            val videoHeight by
                                animateDpAsState(
                                    targetValue = targetVideoHeight,
                                    animationSpec = tween(VIDEO_RESIZE_ANIMATION_MS),
                                    label = "playerVideoHeight",
                                )
                            // Вертикальное центрирование компактного блока (видео + название/
                            // прогресс/чипы под ним) в портретном режиме (2026-09-11, живой
                            // баг-репорт: «видео должно быть посередине» — раньше блок был прижат
                            // к верху экрана, а остаток портретного экрана простаивал пустым чёрным
                            // полем). `belowContentHeight` измеряется самим содержимым снизу видео
                            // (см. [CompactPlayerChrome]/`onBelowContentHeightMeasured` —
                            // прогресс-бар/чипы не имеют фиксированной высоты: прогресс-бар
                            // появляется только после `loadedmetadata`, чипы переносятся по числу
                            // озвучек). Fullscreen не центрируем — там видео и так занимает весь
                            // экран (`targetVideoHeight = screenHeight`), смещение всегда 0.
                            var belowContentHeight by remember { mutableStateOf(0.dp) }
                            val topOffset =
                                if (isFullscreen) {
                                    0.dp
                                } else {
                                    ((screenHeight - videoHeight - belowContentHeight) / 2).coerceAtLeast(0.dp)
                                }
                            EmbedPlayerView(
                                url = source.url,
                                referer = source.referer,
                                controller = controller,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(videoHeight)
                                        .align(Alignment.TopStart)
                                        .offset(y = topOffset),
                            )
                            // PlayerOverlayHost (P8.T1 Step 2/3, expect/actual `composeApp/.../
                            // feature/player/PlayerOverlayHost.kt`): passthrough на Android/iOS
                            // (рисует [content] здесь же, как и раньше), но на Desktop реально
                            // переносит его в ОДНО отдельное top-level окно — см. её KDoc за
                            // причиной (VLCJ-рендер видео теперь тоже отдельное окно, инлайновый
                            // Compose под ним был бы невидим и некликабелен). ВСЁ, что должно
                            // визуально лежать поверх кадра видео — оверлей/compact-chrome,
                            // пикеры озвучки/качества/скорости, resume-диалог — заведено ОДНИМ
                            // вызовом хоста, а не отдельным на каждый: несколько независимых
                            // `alwaysOnTop`-окон конкурировали бы друг с другом за то, какое
                            // из них реально самое верхнее.
                            PlayerOverlayHost(modifier = Modifier.fillMaxSize()) {
                                if (isFullscreen && !pipActive) {
                                    PlayerOverlay(
                                        state = videoState,
                                        controller = controller,
                                        hasNextEpisode = state.hasNextEpisode,
                                        onBack = onBack,
                                        onCollapseFullscreen = { viewModel.setFullscreen(false) },
                                        onNextEpisode = openNextEpisode,
                                        onEpisodeNearEnd = viewModel::markWatchedIfNeeded,
                                        voiceTypes = state.voiceTypes,
                                        currentVoiceType = state.currentVoiceType,
                                        onOpenAudioPicker = { showAudioPicker = true },
                                        qualityLabel = currentQuality.takeIf { qualityOptions.isNotEmpty() },
                                        onOpenQualityPicker = {
                                            if (qualityOptions.isNotEmpty()) showQualityPicker = true
                                        },
                                        speedLabel = strings.playerSpeedValue(speedRate.formatRate()),
                                        onOpenSpeedPicker = { showSpeedPicker = true },
                                        // Кнопка PiP — только когда есть чем управлять: без найденного
                                        // `<video>` окно «картинка в картинке» показывало бы пустую
                                        // страницу, поэтому в no-bridge fullscreen её нет вовсе.
                                        onEnterPictureInPicture =
                                            pictureInPicture
                                                .takeIf { it.isSupported && videoState.isVideoFound }
                                                ?.let { pip -> { pip.enter() } },
                                    )
                                } else if (!pipActive) {
                                    CompactPlayerChrome(
                                        videoHeight = videoHeight,
                                        topOffset = topOffset,
                                        onBelowContentHeightMeasured = { belowContentHeight = it },
                                        state = videoState,
                                        controller = controller,
                                        onBack = onBack,
                                        onEnterFullscreen = { viewModel.setFullscreen(true) },
                                        voiceTypes = state.voiceTypes,
                                        currentVoiceType = state.currentVoiceType,
                                        onOpenAudioPicker = { showAudioPicker = true },
                                        qualityLabel = currentQuality.takeIf { qualityOptions.isNotEmpty() },
                                        onOpenQualityPicker = {
                                            if (qualityOptions.isNotEmpty()) showQualityPicker = true
                                        },
                                        speedLabel = strings.playerSpeedValue(speedRate.formatRate()),
                                        onOpenSpeedPicker = { showSpeedPicker = true },
                                    )
                                }

                                if (showAudioPicker) {
                                    AudioPickerOverlay(
                                        voiceTypes = state.voiceTypes,
                                        currentVoiceType = state.currentVoiceType,
                                        isSwitching = state.isAudioSwitching,
                                        onSelect = { typeId ->
                                            viewModel.selectVoiceType(typeId)
                                            showAudioPicker = false
                                        },
                                        onDismiss = { showAudioPicker = false },
                                    )
                                }

                                if (showQualityPicker) {
                                    OptionSheetOverlay(
                                        title = strings.playerQualityTitle,
                                        options = qualityOptions,
                                        current = currentQuality,
                                        onSelect = { quality ->
                                            controller.setQuality(quality)
                                            manualQuality = quality
                                            showQualityPicker = false
                                        },
                                        onDismiss = { showQualityPicker = false },
                                    )
                                }

                                if (showSpeedPicker) {
                                    val speedLabels = PLAYBACK_RATES.map { strings.playerSpeedValue(it.formatRate()) }
                                    OptionSheetOverlay(
                                        title = strings.playerSpeedTitle,
                                        options = speedLabels,
                                        current = strings.playerSpeedValue(speedRate.formatRate()),
                                        onSelect = { label ->
                                            PLAYBACK_RATES
                                                .firstOrNull { strings.playerSpeedValue(it.formatRate()) == label }
                                                ?.let { controller.setPlaybackRate(it) }
                                            showSpeedPicker = false
                                        },
                                        onDismiss = { showSpeedPicker = false },
                                    )
                                }

                                // P16.T7 — resume-диалог «Продолжить с M:SS / С начала», по одному
                                // разу на переоткрытие серии ([PlayerUiState.resumePositionMs]
                                // сбрасывается обоими выборами).
                                val resumePositionMs = state.resumePositionMs
                                if (resumePositionMs != null) {
                                    ResumePlaybackDialog(
                                        positionMs = resumePositionMs,
                                        strings = strings,
                                        onContinue = viewModel::onResumeContinue,
                                        onStartOver = viewModel::onResumeStartOver,
                                        onDismiss = viewModel::onResumeDismiss,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            EmbedPlayerView(
                                url = source.url,
                                referer = source.referer,
                                controller = controller,
                                modifier = Modifier.fillMaxSize(),
                            )
                            PlayerDesktopControls(
                                isWatched = state.isWatched,
                                hasNextEpisode = state.hasNextEpisode,
                                onBack = onBack,
                                onToggleWatched = viewModel::toggleWatched,
                                onNextEpisode = openNextEpisode,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                } else {
                    // Недостижимо на практике: `resolvePlaybackSource` всегда возвращает `Embed`
                    // (см. `EpisodeRepository`), но исчерпывающая обработка честнее, чем `!!`.
                    AnixErrorBox(message = strings.playerLoadError, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** 16:9 — инженерно разумный эквивалент фиксированных 226px видео-области мокапа
 *  (`Reelwave Prototype.dc.html`, `showPlayer`), см. KDoc [PlayerScreen]. */
private const val COMPACT_VIDEO_ASPECT_RATIO = 16f / 9f

/** См. KDoc у `animateDpAsState` в [PlayerScreen] — длительность плавного ресайза видео-области. */
private const val VIDEO_RESIZE_ANIMATION_MS = 300

private fun PlayerError?.toMessage(strings: Strings): String =
    when (this) {
        PlayerError.NoConnection -> strings.commonErrorNoConnection
        PlayerError.Unauthorized -> strings.commonErrorUnauthorized
        is PlayerError.SourceUnavailable -> strings.playerSourceError(hostKey)
        PlayerError.Generic, null -> strings.playerLoadError
    }

/** P16.T7 — троттлинг периодического сохранения позиции воспроизведения. */
private const val POSITION_SAVE_THROTTLE_MS = 2_000L

/**
 * P16.T7 — resume-диалог: «Продолжить с M:SS» / «С начала». Показывается один раз на
 * переоткрытие серии (см. KDoc [PlayerUiState.resumePositionMs]) поверх видео, независимо от
 * compact/fullscreen — тот же слой, что и [AudioPickerOverlay].
 */
@Composable
private fun ResumePlaybackDialog(
    positionMs: Long,
    strings: Strings,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.playerResumeTitle) },
        text = { Text(strings.playerResumeContinueFrom(formatResumeTime(positionMs))) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(strings.playerResumeContinue) }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) { Text(strings.playerResumeFromStart) }
        },
    )
}

/** `125_000L` → `"2:05"` — M:SS без ведущего нуля у минут, секунды дополняются нулём слева. */
private fun formatResumeTime(positionMs: Long): String {
    val totalSeconds = positionMs / MILLIS_IN_SECOND
    val minutes = totalSeconds / SECONDS_IN_MINUTE
    val seconds = totalSeconds % SECONDS_IN_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val MILLIS_IN_SECOND = 1_000L
private const val SECONDS_IN_MINUTE = 60L
