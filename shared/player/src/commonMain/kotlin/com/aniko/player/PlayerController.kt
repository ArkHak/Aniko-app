package com.aniko.player

import androidx.compose.runtime.Composable
import com.aniko.model.AnixError
import kotlinx.coroutines.flow.StateFlow

/** Снимок состояния воспроизведения. */
data class PlaybackState(
    val source: PlaybackSource? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: AnixError? = null,
)

/**
 * Платформенно-независимый контракт видеоплеера.
 *
 * Реализации: Android — Media3/ExoPlayer, iOS — AVPlayer, Desktop — VLC/JavaFX.
 * На этапе скелета все три actual'а — no-op заглушки: контракт зафиксирован,
 * реализация приезжает в фазе «плеер».
 */
interface PlayerController {
    val state: StateFlow<PlaybackState>

    fun load(source: PlaybackSource)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun release()
}

/**
 * Создаёт и привязывает к жизненному циклу композиции платформенный контроллер.
 *
 * Именно `@Composable`, а не обычная фабрика: на Android реализации нужен `Context`,
 * на iOS — привязка к текущему `UIViewController`, и то и другое доступно только
 * из композиции.
 */
@Composable
expect fun rememberPlayerController(): PlayerController
