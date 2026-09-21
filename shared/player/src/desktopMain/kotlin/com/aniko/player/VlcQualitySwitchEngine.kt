package com.aniko.player

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import kotlin.math.abs

/**
 * Исполнитель [QualitySwitchEngine] поверх нативного VLCJ [MediaPlayer].
 *
 * Все методы вызываются автоматом [QualitySwitchCoordinator] с потока `MediaPlayer.submit` (vlcj
 * запрещает трогать libVLC из своего событийного потока — иначе «вызов будет неэффективен, возможно
 * странное поведение либо фатальный краш JVM», см. KDoc `MediaPlayer.submit`) и никогда не бросают:
 * сбой нативного вызова превращается в `false`/no-op, а не в исключение внутри автомата.
 *
 * @param referer `Referer` для CDN-запроса потока (`:http-referrer=`) — читается на каждый [load],
 * потому что резолвер отдаёт его вместе с потоками КАЖДОГО источника.
 * @param currentTimeMs последняя позиция, известная UI-состоянию контроллера, — запасной вариант, когда
 * libVLC ещё не знает время (`status().time()` отрицателен).
 * @param scheduleTimeout доставка таймаута загрузки автомату (контроллер пересылает её на тот же поток).
 */
internal class VlcQualitySwitchEngine(
    private val player: MediaPlayer,
    private val referer: () -> String?,
    private val currentTimeMs: () -> Long,
    private val scheduleTimeout: (token: Long, delayMs: Long) -> Unit,
) : QualitySwitchEngine {
    override fun snapshot(): PlaybackSnapshot {
        val status = player.status()
        val audio = player.audio()
        val position = status.time().takeIf { it >= 0L } ?: currentTimeMs()
        return PlaybackSnapshot(
            positionMs = position,
            isPlaying = status.state() in PLAYING_INTENT_STATES,
            rate = status.rate().takeIf { it > 0f },
            volume = audio.volume().takeIf { it >= 0 },
            isMuted = audio.isMute,
        )
    }

    /**
     * `@Suppress("SpreadOperator")` — `media().play(mrl, vararg options)` принимает опции только
     * варарг-ом, а их число зависит от условий (referer/старт-позиция/пауза); собираем список и
     * передаём одним вызовом, а не ветвим четыре комбинации.
     */
    @Suppress("SpreadOperator")
    override fun load(
        url: String,
        startPositionMs: Long,
        startPaused: Boolean,
    ): Boolean {
        val options = mediaOptions(referer(), startPositionMs, startPaused)
        return runCatching { player.media().play(url, *options.toTypedArray()) }.getOrDefault(false)
    }

    override fun positionMs(): Long? = runCatching { player.status().time() }.getOrNull()?.takeIf { it >= 0L }

    override fun seekTo(positionMs: Long) {
        runCatching { player.controls().setTime(positionMs) }
    }

    override fun setPaused(paused: Boolean) {
        runCatching { player.controls().setPause(paused) }
    }

    override fun setRate(rate: Float) {
        runCatching { if (abs(player.status().rate() - rate) > RATE_EPSILON) player.controls().setRate(rate) }
    }

    override fun setVolume(volume: Int) {
        // libVLC хранит громкость на уровне aout плеера и обычно переживает смену медиа; пишем только
        // при расхождении, чтобы не дёргать аудиовыход зря.
        runCatching { if (player.audio().volume() != volume) player.audio().setVolume(volume) }
    }

    override fun setMuted(muted: Boolean) {
        runCatching { if (player.audio().isMute != muted) player.audio().setMute(muted) }
    }

    override fun scheduleTimeout(
        token: Long,
        delayMs: Long,
    ) = scheduleTimeout.invoke(token, delayMs)

    internal companion object {
        /** Состояния libVLC, в которых пользователь «хочет смотреть» (в т.ч. пока поток ещё открывается). */
        private val PLAYING_INTENT_STATES = setOf(State.OPENING, State.BUFFERING, State.PLAYING)

        /**
         * Опции медиа: `:http-referrer=` (если нужен), `:start-time=` в секундах (если позиция не нулевая) и
         * `:start-paused` (если пользователь был на паузе). Вынесено и `internal` ради теста формата —
         * ошибка в имени опции молча превратилась бы в «старт с нуля».
         */
        fun mediaOptions(
            referer: String?,
            startPositionMs: Long,
            startPaused: Boolean,
        ): List<String> =
            buildList {
                if (referer != null) add(":http-referrer=$referer")
                if (startPositionMs > 0L) add(":start-time=${startPositionMs / MILLIS_PER_SECOND}")
                if (startPaused) add(":start-paused")
            }

        private const val MILLIS_PER_SECOND = 1000.0
        private const val RATE_EPSILON = 0.001f
    }
}
