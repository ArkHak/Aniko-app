package com.aniko.app.feature.player

import androidx.compose.runtime.Composable
import com.aniko.player.EmbedVideoController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Режим «картинка в картинке» с управлением (P16.T8).
 *
 * Почему управление, а не просто «свернуть в окошко»: у нас над видео нет нативного слоя, которым
 * система умеет управлять сама — видео живёт в чужой embed-странице внутри `WebView`, поэтому
 * PiP-окно получает наш собственный набор кнопок ([PlayerPipControl]), а нажатия приходят обратно
 * в приложение и превращаются в команды JS-моста ([EmbedVideoController]).
 *
 * Платформы:
 * - Android — `Activity.enterPictureInPictureMode` + `RemoteAction` (см. actual);
 * - iOS — `isSupported == false`: программный вход в PiP доступен только через
 *   `AVPictureInPictureController` над `AVPlayerLayer`, а наше видео — чужой `<video>` внутри
 *   `WKWebView`; системный PiP для такого видео iOS даёт сама (жест «домой»), но управлять им из
 *   Compose нельзя. Честная заглушка вместо неработающей кнопки (CUT, отчёт в `REELWAVE_PLAN.md`);
 * - Desktop — `isSupported == false`, полноэкранный оверлей там вообще не рисуется (P8.T1).
 */
interface PlayerPictureInPicture {
    /** `false` — кнопка PiP не показывается вовсе (см. KDoc класса). */
    val isSupported: Boolean

    /** `true`, пока Activity в PiP-режиме: оверлей обязан спрятаться, оставив только видео. */
    val isActive: StateFlow<Boolean>

    /**
     * Разрешает автоматический вход в PiP при сворачивании приложения (Android 12+
     * `setAutoEnterEnabled`; на более старых версиях — `onUserLeaveHint`, см. actual).
     * Включается только когда есть чем управлять: fullscreen + найденное `<video>` + идёт игра.
     * Иначе «домой» из плеера сворачивал бы приложение в окошко вместо нормального ухода.
     */
    fun setAutoEnterEnabled(enabled: Boolean)

    /** Обновляет иконку play/pause в PiP-действиях. */
    fun setPlaying(playing: Boolean)

    /** Ручной вход по кнопке оверлея. */
    fun enter()
}

/** Действия PiP-окна: то, что система умеет показать кнопками в окошке. */
enum class PlayerPipControl { TOGGLE_PLAY, REWIND, FORWARD }

/**
 * Контроллер PiP, привязанный к композиции: подписывается на смену PiP-режима Activity и
 * отписывается на `onDispose`.
 *
 * [controller] передаётся сюда, а не колбэком: PiP-действия приходят из системного окна, вне
 * композиции, и обязаны попасть прямо в JS-мост.
 */
@Composable
expect fun rememberPlayerPictureInPicture(controller: EmbedVideoController): PlayerPictureInPicture

/** Заглушка для платформ без PiP (iOS/Desktop) и для не-Activity контекста на Android. */
internal object UnsupportedPlayerPictureInPicture : PlayerPictureInPicture {
    override val isSupported: Boolean = false

    override val isActive: StateFlow<Boolean> = MutableStateFlow(false)

    override fun setAutoEnterEnabled(enabled: Boolean) = Unit

    override fun setPlaying(playing: Boolean) = Unit

    override fun enter() = Unit
}

/** Шаг перемотки в PiP-действиях, мс — тот же, что у тап-зон ±10с оверлея. */
internal const val PIP_SEEK_STEP_MS = 10_000L

/** Соотношение сторон PiP-окна: эпизоды — 16:9, окно обязано совпадать с видео. */
internal const val PIP_ASPECT_WIDTH = 16

internal const val PIP_ASPECT_HEIGHT = 9
