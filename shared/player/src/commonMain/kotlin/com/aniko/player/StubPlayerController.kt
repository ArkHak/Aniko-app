package com.aniko.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Заглушка контроллера: корректно ведёт состояние, но ничего не воспроизводит.
 *
 * Нужна, чтобы UI-слой можно было писать и тестировать до появления настоящих
 * платформенных реализаций (Media3 / AVPlayer / VLC).
 */
internal class StubPlayerController(
    private val platformName: String,
) : PlayerController {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override fun load(source: PlaybackSource) {
        _state.update { PlaybackState(source = source, isBuffering = false) }
    }

    override fun play() {
        _state.update { it.copy(isPlaying = it.source != null) }
    }

    override fun pause() {
        _state.update { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun release() {
        _state.value = PlaybackState()
    }

    override fun toString(): String = "StubPlayerController($platformName)"
}
