@file:Suppress("MatchingDeclarationName") // `.desktop.kt` — общепринятый суффикс actual-файла в KMP.

package com.aniko.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * Desktop-реализация моста поверх нативного VLCJ `MediaPlayer` (Step 2/3 пересмотра P8.T1, см.
 * журнал `docs/REELWAVE_PLAN.md`) — заменяет прежний `CefMessageRouter`-мост к `<video>` внутри
 * JCEF-страницы. [DesktopStreamResolver] находит реальный URL потока чистым HTTP-парсингом
 * embed-страницы (Step 3 пересмотра, `feature/desktop-video-player`, см. её KDoc) — на Desktop
 * с этого шага браузерный движок для резолва вообще не нужен, этот класс им никогда и не управлял.
 *
 * **Почему это надёжнее старого JS-моста, а не даунгрейд.** Android/iOS/старый-Desktop читают
 * состояние чужого `<video>` через DOM-события и периодический опрос (`setInterval`, см.
 * [embedBridgeScript]) — единственный доступный им канал, раз видео живёт внутри чужой страницы.
 * Здесь видео — НАШ собственный `MediaPlayer` (libVLC воспроизводит уже резолвнутый прямой поток,
 * а не чужую embed-страницу), поэтому состояние приходит нативными событиями VLCJ
 * ([MediaPlayerEventAdapter] — `playing`/`paused`/`timeChanged`/`lengthChanged`/`rateChanged`),
 * без единого DOM-опроса. `EmbedVideoState` остаётся тем же commonMain-контрактом (не пересмотрен
 * этой веткой) — здесь просто другой источник его полей.
 *
 * [attach]/[detach] вызывает `EmbedPlayerView` (desktop) при создании/уничтожении VLCJ-компонента
 * видео-окна — тот же паттерн, что раньше был с `CefBrowser`/`CefClient` у JCEF-моста.
 *
 * `@Suppress("TooManyFunctions")` — как и на прежнем JCEF-мосте: размер класса задан контрактом
 * `expect class` (9 методов) + внутренние [attach]/[detach], оба обслуживают тот же самый мост.
 */
@Suppress("TooManyFunctions")
actual class EmbedVideoController actual constructor() {
    private val stateFlow = MutableStateFlow(EmbedVideoState())

    actual val state: StateFlow<EmbedVideoState> = stateFlow.asStateFlow()

    /** `true` безусловно — тот же смысл, что и у прежнего JCEF-моста (см. историю KDoc): механизм
     *  (VLCJ `MediaPlayer` + нативные события) не зависит от версии ПО пользователя. Работоспособность
     *  конкретного запуска (нашёл ли `DesktopStreamResolver` реальный поток за embed-URL) —
     *  runtime-вопрос `EmbedPlayerView`/`EmbedVideoState.isVideoFound`, не свойство контроллера. */
    actual val isSupported: Boolean = true

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    private val eventListener =
        object : MediaPlayerEventAdapter() {
            // Первый реальный сигнал, что медиа-пайплайн реально ожил (не просто "URL резолвнулся"
            // — резолв может отдать URL, который libVLC не сможет открыть) — тем же смыслом, что и
            // isVideoFound у JS-моста ("есть на что реально смотреть"), но источник другой.
            override fun buffering(
                mp: MediaPlayer,
                newCache: Float,
            ) = stateFlow.update { it.copy(isVideoFound = true) }

            override fun playing(mp: MediaPlayer) = stateFlow.update { it.copy(isVideoFound = true, isPlaying = true) }

            override fun paused(mp: MediaPlayer) = stateFlow.update { it.copy(isPlaying = false) }

            override fun stopped(mp: MediaPlayer) = stateFlow.update { it.copy(isPlaying = false) }

            override fun finished(mp: MediaPlayer) = stateFlow.update { it.copy(isPlaying = false) }

            // Единственный сигнал, что libVLC ПОЛУЧИЛ резолвнутый URL, но не смог его реально
            // проиграть (неподдерживаемый формат/403 без верного Referer/битая ссылка,
            // истёкшая на середине CDN-сессия) — отдельный провал от "резолв не нашёл URL вовсе"
            // (там `isVideoFound` просто никогда не станет `true`, см. `EmbedPlayer.desktop.kt`).
            // Откат `isVideoFound` в `false` — тот же fallback, что и у полностью неудачного
            // резолва: `PlayerOverlay` (commonMain) деградирует до одной кнопки "назад" вместо
            // того, чтобы держать на экране полноценные контролы над мёртвым плеером.
            override fun error(mp: MediaPlayer) = stateFlow.update { it.copy(isVideoFound = false, isPlaying = false) }

            override fun timeChanged(
                mp: MediaPlayer,
                newTime: Long,
            ) = stateFlow.update { it.copy(currentTimeMs = newTime.coerceAtLeast(0L)) }

            // 0/отрицательное — переходное состояние между сериями/до готовности медиа, не настоящая
            // нулевая длительность (тот же смысл, что и "duration = NaN" у JS-моста, см. её KDoc).
            override fun lengthChanged(
                mp: MediaPlayer,
                newLength: Long,
            ) = stateFlow.update { it.copy(durationMs = newLength.takeIf { length -> length > 0L }) }

            // vlcj 4.11.0's MediaPlayerEventListener не даёт отдельного rateChanged-события (в
            // отличие от JS-моста, где `ratechange` — реальное DOM-событие `<video>`) — скорость
            // обновляется прямо в [setPlaybackRate] по результату `ControlsApi.setRate`.
        }

    actual fun setExpectedSource(embedUrl: String) {
        stateFlow.value = EmbedVideoState()
    }

    /** Вызывается `EmbedPlayerView` (desktop) один раз при создании VLCJ video-окна — до первого
     *  `media().play(...)`, чтобы ни одно событие не потерялось. */
    internal fun attach(player: MediaPlayer) {
        detach()
        mediaPlayer = player
        player.events().addMediaPlayerEventListener(eventListener)
    }

    /** Снимает мост с текущего `MediaPlayer` (dispose видео-окна/смена источника) — идемпотентна. */
    internal fun detach() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.events().removeMediaPlayerEventListener(eventListener) }
        }
        stateFlow.value = EmbedVideoState()
    }

    actual fun release() = detach()

    actual fun play() {
        mediaPlayer?.controls()?.play()
    }

    actual fun pause() {
        mediaPlayer?.controls()?.setPause(true)
    }

    actual fun togglePlayPause() {
        if (stateFlow.value.isPlaying) pause() else play()
    }

    actual fun seekTo(positionMs: Long) {
        mediaPlayer?.controls()?.setTime(positionMs.coerceAtLeast(0L))
    }

    actual fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        val target = (player.status().time() + deltaMs).coerceAtLeast(0L)
        player.controls().setTime(target)
    }

    actual fun setPlaybackRate(rate: Float) {
        val applied = mediaPlayer?.controls()?.setRate(rate) ?: false
        // Нет отдельного nativeRateChanged-события (см. комментарий у [eventListener]) — состояние
        // обновляется по результату самого вызова, а не по последующему колбэку.
        if (applied) stateFlow.update { it.copy(playbackRate = rate) }
    }

    /** CUT на Desktop, как и было (см. бриф задачи "Quality/subtitle/audio-track picking stays
     *  CUT as before") — libVLC воспроизводит уже резолвнутый прямой поток одного качества, у него
     *  нет клиентского quality-dropdown хоста, которым управлял бы старый JS-мост
     *  ([embedBridgeScript].switchQuality). [PlayerScreen] не рисует чип качества, когда
     *  `availableQualities` пуст (см. её KDoc) — честный no-op, не притворяемся, что переключаем. */
    actual fun setQuality(quality: String) = Unit
}
