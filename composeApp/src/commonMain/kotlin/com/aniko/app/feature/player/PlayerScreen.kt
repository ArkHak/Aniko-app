package com.aniko.app.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.VideoHost
import com.aniko.player.EmbedPlayerView
import com.aniko.player.LockLandscapeOrientationEffect
import com.aniko.player.PlaybackSource
import com.aniko.player.rememberEmbedVideoController
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран плеера: на весь экран рендерит embed-страницу источника через [EmbedPlayerView].
 *
 * Архитектурное решение фазы 5 (не пересматривать): всё воспроизведение — через embed (WebView),
 * независимо от хоста. Нативный `PlayerController`/`VideoSurface` из `:shared:player` здесь не
 * используются — это задел на будущее.
 *
 * Фаза 8 добавила поверх embed'а JS-мост (`rememberEmbedVideoController`): он даёт
 * play/pause/seek/скорость и состояние `<video>` внутри чужой страницы на Android и iOS.
 * На Desktop `controller.isSupported == false` — там видео играет в системном браузере, и
 * никакого элемента под нашим контролем нет.
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
 * **Единственная развилка платформ на этом экране** — обычный `if` по `controller.isSupported`,
 * без `expect/actual`: сам флаг уже разруливает платформу за нас (см. его KDoc).
 * - `isSupported == true` (Android/iOS) → компактный режим по умолчанию + [PlayerOverlay] по
 *   кнопке "На весь экран": назад/PiP/тап-зона play-pause/прогресс-бар с seek (P8.T3), баннер
 *   «следующая серия через Nс» (P8.T4), скорость 1.0–2.0 (P8.T5) и авто-отметка «просмотрено» на
 *   подходе к концу серии (P8.T8);
 * - `isSupported == false` (Desktop) → [PlayerDesktopControls]: только две кнопки, «следующая
 *   серия» и ручная отметка просмотра, потому что позиции воспроизведения там не существует
 *   (P8.T1). Компактный режим/fullscreen-кнопка на Desktop не показываются вовсе — там и так
 *   видео играет в системном браузере, а не в этом окне (P8.T1).
 *
 * @param onBack закрыть плеер. Приходит параметром, а не берётся из
 * [com.aniko.app.navigation.LocalTitleNavigator]: `TitleNavigator.back()` на wide-экранах сначала
 * разбирает стек detail-панелей, а плеер — всегда полноэкранный маршрут `NavController` поверх
 * этих панелей (см. KDoc `TitleNavigator.openPlayer`), и «назад» из него обязан снимать именно
 * маршрут. Открытие следующей серии, наоборот, идёт через навигатор — там `openPlayer` и так
 * бьёт ровно в `NavController`.
 */
@Suppress("LongParameterList", "LongMethod") // 4 параметра маршрута задаются `AnixDestination.Player`
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

    if (isFullscreen) {
        // Принудительный поворот в альбомную ориентацию, пока открыт fullscreen (P13).
        LockLandscapeOrientationEffect()
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
                    // на Android и iOS, на Desktop `controller.isSupported == false` и все методы
                    // no-op (P8.T1).
                    val controller = rememberEmbedVideoController(source.url)
                    val videoState by controller.state.collectAsStateWithLifecycle()
                    // Переход на следующую серию — обычная навигация на тот же маршрут с
                    // `position + 1`: экран пересоздастся, `PlayerViewModel.load` увидит новый
                    // `LoadKey` и перезапустит цепочку. Отдельного «перезагрузить внутри экрана»
                    // не заводим — иначе back вернул бы не на предыдущую серию, а мимо неё.
                    val openNextEpisode = { navigator.openPlayer(releaseId, sourceId, position + 1, host) }

                    if (controller.isSupported) {
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
                        Box(modifier = Modifier.fillMaxSize()) {
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
                            EmbedPlayerView(
                                url = source.url,
                                referer = source.referer,
                                controller = controller,
                                modifier = Modifier.fillMaxWidth().height(videoHeight).align(Alignment.TopStart),
                            )
                            if (isFullscreen) {
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
                                )
                            } else {
                                CompactPlayerChrome(
                                    videoHeight = videoHeight,
                                    state = videoState,
                                    controller = controller,
                                    onBack = onBack,
                                    onEnterFullscreen = { viewModel.setFullscreen(true) },
                                    voiceTypes = state.voiceTypes,
                                    currentVoiceType = state.currentVoiceType,
                                    onOpenAudioPicker = { showAudioPicker = true },
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
