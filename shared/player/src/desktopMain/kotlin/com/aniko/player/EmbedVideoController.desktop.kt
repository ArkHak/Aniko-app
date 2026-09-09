@file:Suppress("MatchingDeclarationName") // `.desktop.kt` — общепринятый суффикс actual-файла в KMP.

package com.aniko.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop: моста нет и не будет — [isSupported] всегда `false`, все команды no-op.
 *
 * Обоснование (P8.T1, решение зафиксировано, не пересматривать): на Desktop видео вообще не
 * рендерится приложением. Полноценного WebView в Compose Desktop без JCEF/KCEF нет, а JCEF/KCEF
 * отклонён отдельно: это heavyweight Swing-компонент, который в Compose Desktop всегда рисуется
 * ПОВЕРХ Compose-слоя (известный баг JetBrains CMP-6001), то есть оверлей плеера физически
 * невозможно нарисовать над кадром. Поэтому `EmbedPlayer.desktop.kt` открывает ссылку в
 * системном браузере — а там нет ни `<video>` под нашим контролем, ни кому слать команды.
 *
 * Следствия для UI: оверлей (P8.T3), панель скорости (P8.T5) и клавиатурные шорткаты (P8.T7,
 * закрыт как CUT) на Desktop не строятся. Проверять — через [isSupported], а не через
 * платформенные `if`-ы в общем коде.
 */
actual class EmbedVideoController actual constructor() {
    actual val isSupported: Boolean = false

    private val stateFlow = MutableStateFlow(EmbedVideoState())

    actual val state: StateFlow<EmbedVideoState> = stateFlow.asStateFlow()

    actual fun setExpectedSource(embedUrl: String) = Unit

    actual fun play() = Unit

    actual fun pause() = Unit

    actual fun togglePlayPause() = Unit

    actual fun seekTo(positionMs: Long) = Unit

    actual fun seekBy(deltaMs: Long) = Unit

    actual fun setPlaybackRate(rate: Float) = Unit

    actual fun setQuality(quality: String) = Unit

    actual fun release() = Unit
}
