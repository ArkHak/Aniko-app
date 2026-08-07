package com.aniko.app.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.model.VideoHost
import com.aniko.player.EmbedPlayerView
import com.aniko.player.PlaybackSource
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран плеера: на весь экран рендерит embed-страницу источника через [EmbedPlayerView].
 *
 * Архитектурное решение фазы 5 (не пересматривать): всё воспроизведение — через embed (WebView),
 * независимо от хоста. Нативный `PlayerController`/`VideoSurface` из `:shared:player` здесь не
 * используются — это задел на будущее.
 */
@Composable
fun PlayerScreen(
    releaseId: Int,
    sourceId: Int,
    position: Int,
    hostKey: String,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    LaunchedEffect(releaseId, sourceId, position, hostKey) {
        viewModel.load(releaseId, sourceId, position, VideoHost.fromKey(hostKey))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> AnixLoadingBox(modifier = Modifier.fillMaxSize())

            state.errorMessage != null ->
                AnixErrorBox(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

            else -> {
                val source = state.source
                if (source is PlaybackSource.Embed) {
                    EmbedPlayerView(url = source.url, referer = source.referer, modifier = Modifier.fillMaxSize())
                } else {
                    // Недостижимо на практике: `resolvePlaybackSource` всегда возвращает `Embed`
                    // (см. `EpisodeRepository`), но исчерпывающая обработка честнее, чем `!!`.
                    AnixErrorBox(message = "Не удалось загрузить видео", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
