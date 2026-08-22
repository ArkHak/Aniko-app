package com.aniko.app.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.VideoHost
import com.aniko.player.EmbedPlayerView
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
 * **Отсюда единственная развилка платформ на этом экране** — обычный `if` по `isSupported`,
 * без `expect/actual`: сам флаг уже разруливает платформу за нас (см. его KDoc).
 * - `isSupported == true` (Android/iOS) → [PlayerOverlay]: назад/PiP/тап-зона play-pause/
 *   прогресс-бар с seek (P8.T3), баннер «следующая серия через Nс» (P8.T4), скорость 1.0–2.0
 *   (P8.T5) и авто-отметка «просмотрено» на подходе к концу серии (P8.T8);
 * - `isSupported == false` (Desktop) → [PlayerDesktopControls]: только две кнопки, «следующая
 *   серия» и ручная отметка просмотра, потому что позиции воспроизведения там не существует
 *   (P8.T1). Ни прогресс-бара, ни таймера, ни панели скорости — см. KDoc [PlayerDesktopControls].
 *
 * @param onBack закрыть плеер. Приходит параметром, а не берётся из
 * [com.aniko.app.navigation.LocalTitleNavigator]: `TitleNavigator.back()` на wide-экранах сначала
 * разбирает стек detail-панелей, а плеер — всегда полноэкранный маршрут `NavController` поверх
 * этих панелей (см. KDoc `TitleNavigator.openPlayer`), и «назад» из него обязан снимать именно
 * маршрут. Открытие следующей серии, наоборот, идёт через навигатор — там `openPlayer` и так
 * бьёт ровно в `NavController`.
 */
@Suppress("LongParameterList") // 4 параметра маршрута задаются `AnixDestination.Player` и
// схлопнуть их в data-класс нельзя без изменения контракта навигации; остальные три —
// стандартная тройка экрана (onBack + modifier + viewModel).
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

    Surface(modifier = modifier.fillMaxSize().testTag(AnixTestTags.PLAYER_SCREEN_ROOT)) {
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        EmbedPlayerView(
                            url = source.url,
                            referer = source.referer,
                            controller = controller,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (controller.isSupported) {
                            PlayerOverlay(
                                state = videoState,
                                controller = controller,
                                hasNextEpisode = state.hasNextEpisode,
                                onBack = onBack,
                                onNextEpisode = openNextEpisode,
                                onEpisodeNearEnd = viewModel::markWatchedIfNeeded,
                            )
                        } else {
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

private fun PlayerError?.toMessage(strings: Strings): String =
    when (this) {
        PlayerError.NoConnection -> strings.commonErrorNoConnection
        PlayerError.Unauthorized -> strings.commonErrorUnauthorized
        is PlayerError.SourceUnavailable -> strings.playerSourceError(hostKey)
        PlayerError.Generic, null -> strings.playerLoadError
    }
