package com.aniko.app.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.data.playerpreferences.PlayerPreferencesStore
import com.aniko.model.VideoHost
import com.aniko.player.EmbedPlayerView
import com.aniko.player.HideSystemBarsEffect
import com.aniko.player.LockLandscapeOrientationEffect
import com.aniko.player.PlaybackEngineProblem
import com.aniko.player.PlaybackSource
import com.aniko.player.isEpisodeFinished
import com.aniko.player.isNearEnd
import com.aniko.player.rememberEmbedVideoController
import com.aniko.player.secondsToEpisodeEnd
import com.aniko.ui.component.AnixEmptyState
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.displayNumber
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.ForcedDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
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
 * не в auto-hide оверлее. `isFullscreen` (дефолт платформенный — [playerOpensFullscreen]:
 * Desktop открывается сразу в fullscreen, Android/iOS — в компактном, P13-мокап) переключает между
 * [CompactPlayerChrome] (chrome компактного режима) и [PlayerOverlay] (старый полноэкранный режим
 * с авто-скрытием, P8.T3-T5) — **`EmbedPlayerView` вызывается РОВНО ОДИН РАЗ** вне этого
 * ветвления (см. KDoc [CompactPlayerChrome] про то, почему второй call site оборвал бы
 * воспроизведение при переключении). `LockLandscapeOrientationEffect()` вызывается, пока
 * `isFullscreen == true` (см. комментарий у вызова ниже) — принудительно поворачивает устройство
 * в альбомную ориентацию на Android, на iOS остаётся честным CUT (см. KDoc самой функции).
 *
 * **Многошаговый системный back (player-triple-design, §2.2).** При открытом пикере (озвучки
 * или шторки серий) back закрывает его; при `isFullscreen && videoState.isVideoFound` — сворачивает
 * плеер в компактный режим. Гейт `isVideoFound` обязателен: без моста compact-режим запирает
 * embed-страницу за жестовым слоем `CompactVideoGestureLayer`, поэтому back в no-bridge
 * fullscreen должен оставаться выходом из экрана, а не collapse.
 *
 * Исключение — стрелка «назад» самого оверлея на платформах с [playerOpensFullscreen] == true
 * (Desktop): там compact-режим не является самостоятельным состоянием, которое пользователь
 * когда-либо видел, и сворачивание в него первым «назад» ощущалось как лишний промежуточный
 * экран — см. KDoc `PlayerTopBar` в `PlayerOverlay.kt` (фикс 2026-09-18). Системный
 * `BackHandler` ниже Desktop не затрагивает (на нём он no-op, см. `BackHandler.desktop.kt`),
 * поэтому гейт на [playerOpensFullscreen] ему не нужен.
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
    // Здесь всегда ИСХОДНЫЕ параметры маршрута, даже после смены озвучки (`selectVoiceType` их не
    // меняет). Экран может покинуть композицию и войти в неё снова (см. KDoc
    // [PlayerUiState.isFullscreen]) — эффект тогда вызовет `load` с тем же ключом, и это no-op, пока
    // источник грузится/загружен: ViewModel помнит принятый ключ маршрута отдельно от реально
    // загруженного и не откатывает выбранную озвучку (см. `PlayerViewModel.load`).
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
    // Общая на compact/fullscreen шторка серий — по тому же аргументу, что showAudioPicker
    // (одна копия на оба режима вместо двух независимых, см. KDoc [EpisodesSheetOverlay]).
    var showEpisodesPicker by remember { mutableStateOf(false) }

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
        // текст, что дало бы то же самое невидимое сочетание для AnixLoadingState/AnixErrorState
        // (state.isLoading/state.error), только тёмный-на-чёрном вместо белого-на-белом.
        contentColor = Color.White,
    ) {
        when {
            state.isLoading -> AnixLoadingState(modifier = Modifier.fillMaxSize())

            state.error != null ->
                AnixErrorState(
                    message = state.error.toMessage(strings),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

            else -> {
                val source = state.source
                if (source is PlaybackSource.Embed) {
                    // Мост к `<video>` внутри чужой embed-страницы: даёт play/pause/seek/скорость
                    // на Android, iOS и Desktop (JCEF, `feature/desktop-video-player`).
                    // «Качество по умолчанию» из настроек: контроллер применяет его при старте источника
                    // (Desktop — выбором потока до первого кадра, Android/iOS — пунктом меню хоста).
                    // Ручной выбор ниже настройку не перезаписывает.
                    val preferredQualityHeight by
                        koinInject<PlayerPreferencesStore>().preferredQualityHeight.collectAsStateWithLifecycle()
                    val controller = rememberEmbedVideoController(source.url, preferredQualityHeight)
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
                    // Desktop ([isEmbedQualityControllerDriven]): моста-скрейпинга нет — чип
                    // отражает ТОЛЬКО список контроллера (резолвер отдал прозондированные
                    // качества, `setQuality` реально перезапускает поток), доменный фолбэк
                    // отключаем — иначе чип обещал бы качества, которые движок переключить не
                    // может (резолв ещё идёт / Sibnet / однокачественный AniLibria-фолбэк).
                    val qualityDrivenByController = isEmbedQualityControllerDriven()
                    val qualityOptions =
                        videoState.availableQualities.ifEmpty {
                            if (qualityDrivenByController) emptyList() else playerEmbedQualities(source.url)
                        }
                    var manualQuality by remember(source.url) { mutableStateOf<String?>(null) }
                    // Оптимистичный лейбл (manualQuality) живёт только для хост-скрейпинга:
                    // там пикер предлагает то, что показывает меню хоста. Когда качества
                    // присылает контроллер (Desktop), лейбл берём из его состояния — показывать
                    // качество, которое движок реально играет (или на которое идёт смена:
                    // `switchingQualityTo`, рядом виден индикатор), а не то, что UI оптимистично
                    // выбрал: переключение могло не состояться, и тогда контроллер откатился —
                    // чип обязан показать откат, а не залипнуть на неудавшемся выборе (раньше
                    // `manualQuality` перебивал контроллер и на Desktop — чип врал после сбоя).
                    // URL-фолбэк (`currentEmbedQuality`) — только для хост-скрейпинга: на Desktop
                    // он подставлял бы декоративный сегмент URL вместо реального качества потока.
                    val currentQuality =
                        if (qualityDrivenByController) {
                            videoState.switchingQualityTo ?: videoState.currentQuality
                        } else {
                            manualQuality
                                ?: videoState.currentQuality
                                ?: currentEmbedQuality(source.url)
                        }
                    var showQualityPicker by remember { mutableStateOf(false) }
                    // Скорость — одним табом (как качество, P16 2026-09-10).
                    val speedRate = videoState.playbackRate
                    var showSpeedPicker by remember { mutableStateOf(false) }

                    // Back в fullscreen работает в два шага: сначала закрыть пикер озвучки,
                    // потом — свернуть в compact. Пикер добавлен позже в композиции, поэтому
                    // его BackHandler побеждает при `showAudioPicker == true`. Шторка серий —
                    // по тому же приоритету: последняя в композиции побеждает.
                    BackHandler(
                        enabled = !showAudioPicker && !showEpisodesPicker && isFullscreen && videoState.isVideoFound,
                    ) {
                        viewModel.setFullscreen(false)
                    }
                    BackHandler(enabled = showAudioPicker) {
                        showAudioPicker = false
                    }
                    BackHandler(enabled = showEpisodesPicker) {
                        showEpisodesPicker = false
                    }

                    // Переход на другую серию — навигация на тот же маршрут с другой позицией
                    // (`+1` следующая, `-1` предыдущая, любая — выбор из шторки серий).
                    // `TitleNavigator.openPlayer` теперь ЗАМЕНЯЕТ текущий Player-маршрут
                    // (`popUpTo<Player> { inclusive = true }`, фикс 2026-09-08 «два плеера
                    // дублируются»): экран пересоздастся, `PlayerViewModel.load` увидит новый
                    // `LoadKey` и перезапустит цепочку. В back stack всегда ровно один Player —
                    // «назад» из любой серии возвращает на экран, откуда открыли плеер
                    // (детали/список), а не копится стек из серий.
                    //
                    // releaseId/sourceId/position/host параметров этой функции — ИСХОДНЫЕ параметры
                    // маршрута. После [PlayerViewModel.selectVoiceType] они расходятся с реально
                    // загруженным источником: другой `sourceId` (другая озвучка), `position` внутри
                    // неё не обязан совпадать с маршрутным (см. KDoc `EpisodeRepository.matchPosition`
                    // про то, что номерация серий не совпадает между источниками), а маршрутный `host`
                    // и вовсе не пересчитывается при смене озвучки. Берём вместо них РЕАЛЬНО
                    // загруженный источник: `state.positionKey` — тот же ключ, что и у сохранения
                    // позиции воспроизведения (releaseId/sourceId/position загруженного источника), и
                    // `source.host` — его хост (`PlaybackSource.host`, выставляется
                    // `EpisodeRepository.resolveEpisodeTarget` при каждой (пере)загрузке, домен URL
                    // приоритетнее переданного). Фолбэк на параметры маршрута недостижим на практике:
                    // кнопки серий видны только внутри уже загруженного `Embed`-источника,
                    // когда `positionKey` уже выставлен вместе с ним (см. `PlayerViewModel.startLoad`).
                    val loadedSourceKey = state.positionKey
                    val openEpisodeAt: (Int) -> Unit = { episodeOrdinal ->
                        // Режим (fullscreen/compact) переживает смену серии — см. PlayerFullscreenCarry.
                        PlayerFullscreenCarry.put(isFullscreen)
                        navigator.openPlayer(
                            loadedSourceKey?.releaseId ?: releaseId,
                            loadedSourceKey?.sourceId ?: sourceId,
                            episodeOrdinal,
                            source.host,
                        )
                    }
                    // Соседи — позиции СОСЕДНИХ элементов списка серий (`PlayerViewModel.neighbourPositions`),
                    // а не `position ± 1`: нумерация источников бывает с дырками. Кнопки гейтятся
                    // `hasNext/hasPrevEpisode`, так что `null` здесь недостижим — просто no-op.
                    val openNextEpisode = { state.nextEpisodePosition?.let(openEpisodeAt) ?: Unit }
                    val openPrevEpisode = { state.prevEpisodePosition?.let(openEpisodeAt) ?: Unit }
                    // Серии для шторки: API-снепшот (`state.episodes`) смержен с живой локальной
                    // картой отметок (`state.watchedPositions`) — ячейки сетки показывают актуальный
                    // просмотр сразу после оптимистичной записи, не дожидаясь перезагрузки списка.
                    val playerEpisodes =
                        state.episodes.map { episode ->
                            episode.copy(isWatched = state.watchedPositions[episode.position] ?: episode.isWatched)
                        }
                    val currentEpisodePosition = loadedSourceKey?.episodeOrdinal ?: position
                    // Отображаемый номер серии — из `Episode.displayNumber()` (у Sibnet
                    // API-position с 0, человеческий номер в `name`); фолбэк — сырой position,
                    // пока список серий ещё не загрузился.
                    val currentEpisodeLabel =
                        playerEpisodes.firstOrNull { it.position == currentEpisodePosition }?.displayNumber()
                            ?: currentEpisodePosition.toString()
                    val nextEpisodeLabel =
                        state.nextEpisodePosition?.let { next ->
                            playerEpisodes.firstOrNull { it.position == next }?.displayNumber() ?: next.toString()
                        }

                    if (controller.isSupported) {
                        // Конец серии — ОДНО место на оба режима (compact/fullscreen): авто-отметка
                        // «просмотрено» (P8.T8) и карточка «Следующая серия» с автопереходом. Раньше
                        // это жило внутри fullscreen-оверлея, и в компактном режиме серия не
                        // отмечалась, а следующая не включалась. Флаги — `remember(source.url)`:
                        // переключение режима не воскрешает отменённую карточку и не даёт двум
                        // режимам навигировать параллельно; новая серия — новый URL — сброс.
                        val nearEnd = videoState.isNearEnd()
                        LaunchedEffect(nearEnd) {
                            if (nearEnd) viewModel.markWatchedIfNeeded()
                        }
                        var upNextCancelled by remember(source.url) { mutableStateOf(false) }
                        var upNextNavigated by remember(source.url) { mutableStateOf(false) }
                        val upNextActive = state.hasNextEpisode && nearEnd && !upNextCancelled && !upNextNavigated
                        val episodeFinished = videoState.isEpisodeFinished()
                        LaunchedEffect(upNextActive, episodeFinished) {
                            if (upNextActive && episodeFinished) {
                                upNextNavigated = true
                                openNextEpisode()
                            }
                        }
                        val upNextSeconds = videoState.secondsToEpisodeEnd()
                        val upNext =
                            if (upNextActive && upNextSeconds != null && nextEpisodeLabel != null) {
                                UpNextCardState(
                                    episodeLabel = nextEpisodeLabel,
                                    secondsLeft = upNextSeconds,
                                    onPlayNow = {
                                        upNextNavigated = true
                                        openNextEpisode()
                                    },
                                    onCancel = { upNextCancelled = true },
                                )
                            } else {
                                null
                            }

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
                                // `isVideoFound` — сброс состояния при отцеплении моста (уход с экрана) тоже
                                // выглядит как «играло → пауза», но с нулевой позицией: его не сохраняем.
                                if (wasPlaying && !snapshot.isPlaying && snapshot.isVideoFound) {
                                    viewModel.onPlaybackPositionChanged(snapshot.currentTimeMs, snapshot.durationMs)
                                }
                                wasPlaying = snapshot.isPlaying
                            }
                        }
                        // Финальное сохранение по dispose (уход с экрана/смена серии) — то, что
                        // не успел троттлинг выше.
                        // Ключ — РЕАЛЬНО загруженный источник (`state.positionKey`), а не параметры
                        // маршрута: после смены озвучки (`selectVoiceType`) маршрут остаётся прежним, и
                        // запись под ним положила бы позицию новой озвучки в ключ исходной. `positionKey`
                        // выставляется вместе с `source`, так что внутри этой ветки он не `null`.
                        //
                        // Позиция — ПОСЛЕДНЯЯ увиденная с найденным видео, а не `controller.state` в
                        // момент dispose: WebView к этому моменту уже отцепил мост и обнулил состояние
                        // (`detach()`), и выход из плеера записывал 0 поверх настоящей позиции (живая
                        // проверка 2026-10-01: в сторе `…=0` у всех недосмотренных серий).
                        val loadedPositionKey = state.positionKey
                        val lastSeenPlayback = remember(loadedPositionKey, controller) { LastSeenPlayback() }
                        LaunchedEffect(lastSeenPlayback) {
                            controller.state.collect { snapshot ->
                                if (snapshot.isVideoFound && snapshot.durationMs != null) {
                                    lastSeenPlayback.currentMs = snapshot.currentTimeMs
                                    lastSeenPlayback.durationMs = snapshot.durationMs
                                }
                            }
                        }
                        DisposableEffect(loadedPositionKey, controller) {
                            onDispose {
                                // Видео так и не появилось — сохранять нечего (и затирать прежнюю точку тоже).
                                val seenMs = lastSeenPlayback.currentMs ?: return@onDispose
                                // Явный ключ: к моменту dispose (смена серии/озвучки) loadedKey в VM
                                // может указывать уже на новую серию (ревью Волны 3, P3).
                                loadedPositionKey?.let { loaded ->
                                    viewModel.onControllerDisposed(
                                        releaseId = loaded.releaseId,
                                        sourceId = loaded.sourceId,
                                        position = loaded.episodeOrdinal,
                                        currentMs = seenMs,
                                        durationMs = lastSeenPlayback.durationMs,
                                    )
                                }
                            }
                        }
                        // Автозапуск серии, как у взрослых плееров: открыл серию (или сработал
                        // автопереход) — она играет, без второго тапа по кнопке хоста. Команда
                        // повторяется, пока мост не увидит воспроизведение: страница хоста
                        // грузится несколько секунд, а до готовности фрейма `play` уходит в никуда.
                        // Только до ПЕРВОГО старта — дальше пауза пользователя не перебивается.
                        // Автозапуск сдался, а видео так и нет — экран покажет «источник недоступен».
                        var autostartGaveUp by remember(source.url) { mutableStateOf(false) }
                        LaunchedEffect(source.url, controller) {
                            val deadline = AUTOSTART_TIMEOUT_MS / AUTOSTART_RETRY_MS
                            var attempt = 0
                            while (isActive && attempt < deadline && !controller.state.value.isPlaying) {
                                // Реклама хоста: не трогаем плеер и не считаем её время в таймаут
                                // (длинная реклама иначе выглядела бы как «источник недоступен»).
                                if (!controller.state.value.isAdPlaying) {
                                    controller.play()
                                    attempt++
                                }
                                delay(AUTOSTART_RETRY_MS)
                            }
                            autostartGaveUp = !controller.state.value.isVideoFound
                        }
                        // P16.T7 — авто-продолжение: перемотка на сохранённую позицию. Ждём не только
                        // `<video>`, но и метаданных (`durationMs`): до `loadedmetadata` браузер молча
                        // игнорирует `currentTime = …` (живая проверка 2026-10-01: продолжение то
                        // срабатывало, то нет). Команда без подтверждения, поэтому проверяем, что позиция
                        // реально встала, и повторяем; только потом снимаем pending (до этого
                        // автосохранение заблокировано и не затрёт точку продолжения нулём).
                        // …и не во время рекламы хоста: после неё Kodik пересобирает источник и откатывает позицию.
                        val metadataReady =
                            videoState.isVideoFound && videoState.durationMs != null && !videoState.isAdPlaying
                        LaunchedEffect(state.pendingSeekToMs, metadataReady) {
                            val pendingSeekMs = state.pendingSeekToMs
                            if (pendingSeekMs != null && metadataReady) {
                                repeat(RESUME_SEEK_ATTEMPTS) {
                                    controller.seekTo(pendingSeekMs)
                                    delay(RESUME_SEEK_CHECK_MS)
                                    val landedMs = controller.state.value.currentTimeMs
                                    if (landedMs >= pendingSeekMs - RESUME_SEEK_TOLERANCE_MS) {
                                        viewModel.onResumeSeekConsumed()
                                        return@LaunchedEffect
                                    }
                                }
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
                            // Анимируется ТОЛЬКО переключение режима пользователем (2026-10-01):
                            // `animateDpAsState` анимировал любое изменение цели — и первый кадр
                            // экрана (окно ещё 0dp → «видео раскрывается» при каждом старте
                            // серии), и поворот. Вне переключения режима высота ставится сразу.
                            val videoHeightAnim = remember { Animatable(targetVideoHeight, Dp.VectorConverter) }
                            var lastFullscreen by remember { mutableStateOf(isFullscreen) }
                            LaunchedEffect(targetVideoHeight, isFullscreen) {
                                val modeToggled = lastFullscreen != isFullscreen
                                lastFullscreen = isFullscreen
                                if (modeToggled && videoHeightAnim.value > 0.dp) {
                                    videoHeightAnim.animateTo(targetVideoHeight, tween(VIDEO_RESIZE_ANIMATION_MS))
                                } else {
                                    videoHeightAnim.snapTo(targetVideoHeight)
                                }
                            }
                            val videoHeight = videoHeightAnim.value
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
                            PlayerOverlayHost(
                                modifier = Modifier.fillMaxSize(),
                                onKeyEvent = { event ->
                                    handlePlayerKeyEvent(event, controller, videoState.playbackRate)
                                },
                            ) {
                                // Слой поверх видео всегда тёмный (шторки/диалоги), как у медиаприложений.
                                ForcedDarkTheme {
                                    // Desktop без VLC: видео-окна нет, вместо чёрного прямоугольника — объяснение и
                                    // «Скачать VLC». Внутри хоста (а не под ним): прозрачное окно оверлея на Desktop
                                    // перехватывало бы клики по кнопке, лежащей в главном окне.
                                    if (videoState.engineProblem == PlaybackEngineProblem.VlcUnavailable) {
                                        VlcRequiredState(
                                            modifier =
                                                if (isFullscreen) {
                                                    Modifier.fillMaxSize()
                                                } else {
                                                    Modifier.fillMaxWidth().height(videoHeight).offset(y = topOffset)
                                                },
                                        )
                                    }
                                    // Ниже хрома (оверлей/compact рисуются после) — «назад» остаётся доступен.
                                    val onAlternativeSource: (() -> Unit)? =
                                        viewModel::switchToAlternativeSource.takeIf { state.alternativeSource != null }
                                    val sourceFailed =
                                        videoState.engineProblem == PlaybackEngineProblem.SourceUnavailable ||
                                            (autostartGaveUp && !videoState.isVideoFound)
                                    val videoAreaModifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(videoHeight)
                                            .offset(y = topOffset)
                                    if (sourceFailed && !pipActive) {
                                        PlayerSourceFailedState(
                                            // Повтор — пересоздание экрана той же серии: новая страница
                                            // хоста и новая цепочка автозапуска с нуля.
                                            onRetry = { openEpisodeAt(currentEpisodePosition) },
                                            onAlternativeSource = onAlternativeSource,
                                            onChangeVoice =
                                                { showAudioPicker = true }.takeIf { state.voiceTypes.size > 1 },
                                            modifier = videoAreaModifier,
                                        )
                                    }

                                    if (isFullscreen && !pipActive) {
                                        PlayerOverlay(
                                            state = videoState,
                                            controller = controller,
                                            hasPrevEpisode = state.hasPrevEpisode,
                                            hasNextEpisode = state.hasNextEpisode,
                                            onBack = onBack,
                                            onCollapseFullscreen = { viewModel.setFullscreen(false) },
                                            onPrevEpisode = openPrevEpisode,
                                            onNextEpisode = openNextEpisode,
                                            episodeTitle = strings.playerEpisodeChip(currentEpisodeLabel),
                                            upNext = upNext,
                                            voiceTypes = state.voiceTypes,
                                            currentVoiceType = state.currentVoiceType,
                                            onOpenAudioPicker = { showAudioPicker = true },
                                            qualityLabel = currentQuality.takeIf { qualityOptions.isNotEmpty() },
                                            onOpenQualityPicker = {
                                                if (qualityOptions.isNotEmpty()) showQualityPicker = true
                                            },
                                            speedLabel = strings.playerSpeedValue(speedRate.formatRate()),
                                            onOpenSpeedPicker = { showSpeedPicker = true },
                                            onOpenEpisodesPicker = {
                                                if (playerEpisodes.isNotEmpty()) showEpisodesPicker = true
                                            },
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
                                            hasPrevEpisode = state.hasPrevEpisode,
                                            onPrevEpisode = openPrevEpisode,
                                            hasNextEpisode = state.hasNextEpisode,
                                            onNextEpisode = openNextEpisode,
                                            currentEpisodeLabel = currentEpisodeLabel,
                                            episodesAvailable = playerEpisodes.isNotEmpty(),
                                            upNext = upNext,
                                            onOpenEpisodesPicker = { showEpisodesPicker = true },
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

                                    if (showEpisodesPicker) {
                                        EpisodesSheetOverlay(
                                            episodes = playerEpisodes,
                                            currentPosition = currentEpisodePosition,
                                            onSelect = { episodeOrdinal ->
                                                showEpisodesPicker = false
                                                openEpisodeAt(episodeOrdinal)
                                            },
                                            onDismiss = { showEpisodesPicker = false },
                                        )
                                    }

                                    if (showQualityPicker) {
                                        OptionSheetOverlay(
                                            title = strings.playerQualityTitle,
                                            options = qualityOptions,
                                            current = currentQuality,
                                            onSelect = { quality ->
                                                controller.setQuality(quality)
                                                if (!qualityDrivenByController) manualQuality = quality
                                                showQualityPicker = false
                                            },
                                            onDismiss = { showQualityPicker = false },
                                        )
                                    }

                                    if (showSpeedPicker) {
                                        val speedLabels =
                                            PLAYBACK_RATES.map { strings.playerSpeedValue(it.formatRate()) }
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

                                    // Старт/буферизация — свой спиннер вместо чёрного кадра и спиннера хоста;
                                    // если автозапуск сдался — наша ▶. Не в PiP и не без VLC (там своё).
                                    val hostBusy = videoState.engineProblem != null || videoState.isAdPlaying
                                    if (!sourceFailed && !pipActive && !hostBusy) {
                                        PlayerStartIndicator(
                                            isStarting = !videoState.isVideoFound,
                                            isBuffering = videoState.isBuffering,
                                            modifier = videoAreaModifier,
                                        )
                                    }

                                    // Индикатор «Переключаем качество…» / уведомление об откате (Desktop) —
                                    // поверх кадра видео, в том же слое, что и пикеры.
                                    PlayerQualitySwitchStatus(
                                        state = videoState,
                                        modifier = Modifier.fillMaxWidth().height(videoHeight).offset(y = topOffset),
                                    )

                                    // P16.T7 — плашка авто-продолжения «Продолжено с M:SS · С начала»: внизу
                                    // кадра, сама исчезает через RESUME_NOTICE_MS. Не отдельное окно
                                    // (как прежний AlertDialog) — не возвращает системные панели в fullscreen.
                                    val resumeNoticeMs = state.resumeNoticeMs
                                    val resumeNoticeInset =
                                        if (isFullscreen) RESUME_NOTICE_FULL_INSET else RESUME_NOTICE_COMPACT_INSET
                                    // Показ и таймер — только когда видео реально пошло: иначе плашка
                                    // успевала истечь, пока страница хоста ещё грузилась.
                                    // Защёлка: однажды начавшись, плашка держится свои RESUME_NOTICE_MS, даже
                                    // если `isPlaying` кратко мигнёт на буферизации после перемотки.
                                    var resumeNoticeStarted by remember(resumeNoticeMs) { mutableStateOf(false) }
                                    val playbackRunning = videoState.isPlaying && !videoState.isBuffering
                                    LaunchedEffect(playbackRunning) {
                                        if (playbackRunning) resumeNoticeStarted = true
                                    }
                                    if (resumeNoticeMs != null && !pipActive && resumeNoticeStarted) {
                                        LaunchedEffect(resumeNoticeMs) {
                                            delay(RESUME_NOTICE_MS)
                                            viewModel.onResumeNoticeDismiss()
                                        }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(videoHeight)
                                                    .offset(y = topOffset)
                                                    .padding(
                                                        bottom = resumeNoticeInset,
                                                    ),
                                            contentAlignment = Alignment.BottomCenter,
                                        ) {
                                            ResumeNotice(
                                                positionMs = resumeNoticeMs,
                                                strings = strings,
                                                onStartOver = {
                                                    controller.seekTo(0L)
                                                    viewModel.onResumeStartOver()
                                                },
                                            )
                                        }
                                    }
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
                                hasPrevEpisode = state.hasPrevEpisode,
                                hasNextEpisode = state.hasNextEpisode,
                                onBack = onBack,
                                onToggleWatched = viewModel::toggleWatched,
                                onPrevEpisode = openPrevEpisode,
                                onNextEpisode = openNextEpisode,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                } else {
                    // Недостижимо на практике: `resolvePlaybackSource` всегда возвращает `Embed`
                    // (см. `EpisodeRepository`), но исчерпывающая обработка честнее, чем `!!`.
                    AnixErrorState(message = strings.playerLoadError, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Страница загрузки VLC — единственное действие при [PlaybackEngineProblem.VlcUnavailable]. */
private const val VLC_DOWNLOAD_URL = "https://www.videolan.org/vlc/"

/**
 * Desktop: объяснение, почему видео не начнётся (нет VLC), и действие «Скачать VLC». Рисуется на
 * непрозрачном фоне темы поверх чёрного плеера внутри оверлея ([PlayerOverlayHost]); кнопка «назад»
 * компактного/полноэкранного оверлея остаётся поверх и доступна. Текст выбирает экран через i18n
 * ([PlaybackEngineProblem] его не несёт).
 */
@Composable
private fun VlcRequiredState(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current
    AnixEmptyState(
        message = strings.playerVlcRequired,
        actionLabel = strings.playerVlcDownload,
        onAction = { uriHandler.openUri(VLC_DOWNLOAD_URL) },
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    )
}

/** 16:9 — инженерно разумный эквивалент фиксированных 226px видео-области мокапа
 *  (`Reelwave Prototype.dc.html`, `showPlayer`), см. KDoc [PlayerScreen]. */
private const val COMPACT_VIDEO_ASPECT_RATIO = 16f / 9f

/** См. комментарий у `videoHeightAnim` в [PlayerScreen] — длительность плавного ресайза видео-области. */
private const val VIDEO_RESIZE_ANIMATION_MS = 300

private fun PlayerError?.toMessage(strings: Strings): String =
    when (this) {
        PlayerError.NoConnection -> strings.commonErrorNoConnection
        PlayerError.Unauthorized -> strings.commonErrorUnauthorized
        is PlayerError.SourceUnavailable -> strings.playerSourceError(hostKey)
        PlayerError.Generic, null -> strings.playerLoadError
    }

/** Автозапуск серии: шаг повтора команды `play` и сколько всего пытаться (страница хоста + реклама). */
private const val AUTOSTART_RETRY_MS = 1_500L
private const val AUTOSTART_TIMEOUT_MS = 20_000L

/** P16.T7 — троттлинг периодического сохранения позиции воспроизведения. */
private const val POSITION_SAVE_THROTTLE_MS = 2_000L

/**
 * P16.T7 — плашка авто-продолжения: «Продолжено с M:SS» + действие «С начала». Тёмная пилюля поверх
 * кадра (как тосты YouTube/Netflix), не диалог: серия уже играет с сохранённого места.
 */
@Composable
private fun ResumeNotice(
    positionMs: Long,
    strings: Strings,
    onStartOver: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RESUME_NOTICE_CORNER),
        color = Color(RESUME_NOTICE_BG).copy(alpha = RESUME_NOTICE_BG_ALPHA),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.playerResumedFrom(formatResumeTime(positionMs)),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onStartOver) {
                Text(
                    text = strings.playerResumeFromStart,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Последняя позиция, увиденная с найденным видео — для финального сохранения при dispose. */
private class LastSeenPlayback {
    var currentMs: Long? = null
    var durationMs: Long? = null
}

/** Перемотка на точку продолжения: попыток, пауза до проверки, допуск «позиция встала». */
private const val RESUME_SEEK_ATTEMPTS = 4
private const val RESUME_SEEK_CHECK_MS = 1_000L
private const val RESUME_SEEK_TOLERANCE_MS = 3_000L

/** Сколько висит плашка авто-продолжения. */
private const val RESUME_NOTICE_MS = 6_000L

/** В fullscreen плашка выше нижней панели (прогресс + пилюли), чтобы не перекрывать их. */
private val RESUME_NOTICE_FULL_INSET = 120.dp
private val RESUME_NOTICE_COMPACT_INSET = 12.dp
private val RESUME_NOTICE_CORNER = 20.dp
private const val RESUME_NOTICE_BG = 0xFF0F1016
private const val RESUME_NOTICE_BG_ALPHA = 0.92f

/** `125_000L` → `"2:05"` — M:SS без ведущего нуля у минут, секунды дополняются нулём слева. */
private fun formatResumeTime(positionMs: Long): String {
    val totalSeconds = positionMs / MILLIS_IN_SECOND
    val minutes = totalSeconds / SECONDS_IN_MINUTE
    val seconds = totalSeconds % SECONDS_IN_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val MILLIS_IN_SECOND = 1_000L
private const val SECONDS_IN_MINUTE = 60L
