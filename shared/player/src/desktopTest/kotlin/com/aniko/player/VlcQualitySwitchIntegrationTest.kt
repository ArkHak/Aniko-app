package com.aniko.player

import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ОПЦИОНАЛЬНАЯ интеграция с НАСТОЯЩИМ libVLC. По умолчанию (и на CI) ПРОПУСКАЕТСЯ: без переменной
 * окружения `ANIKO_VLC_IT` тест не трогает ни один класс vlcj (на CI без libVLC `NativeDiscovery`
 * зацикливается — PR #76 и KDoc `EmbedPlayerView`), поэтому в обычный `desktopTest` он вреда не несёт.
 *
 * Как запустить вручную: поднять локальный HLS-сервер (`/720/index.m3u8`, `/360/index.m3u8` —
 * потоки ffmpeg `testsrc2`; `/403/…` — всегда 403; `/hang/…` — не отвечает; `/delay/…` — медленная
 * сеть) и
 * `ANIKO_VLC_IT=http://127.0.0.1:18080 ANIKO_VLC_IT_OUT=/tmp/report.txt ./gradlew :shared:player:desktopTest
 * --tests 'com.aniko.player.VlcQualitySwitchIntegrationTest'`. Реальный контроллер и реальный libVLC
 * (`--vout=dummy --aout=dummy`), но НЕ реальный CDN Kodik/AniLibria; замеры дописываются в файл
 * `ANIKO_VLC_IT_OUT`.
 *
 * **Тест не может повесить сборку.** Нативный вызов libVLC невозможно прервать из JVM, поэтому каждый
 * сценарий охраняет watchdog-поток: по истечении [HARD_DEADLINE_MS] он жёстко завершает тестовую JVM
 * (`Runtime.halt`) — Gradle тогда честно падает («Test Executor finished with non-zero exit value»), а не
 * висит часами. Поиск libVLC тоже ограничен по времени; не нашли — сценарий пропускается (`Assume`).
 *
 * Здесь НЕТ сценария «старая схема» (`setTime` прямо из события `playing`): он реально ВЕШАЕТ libVLC —
 * поток `media-player-events` навсегда застревает в `libvlc_media_player_set_time` (зафиксировано
 * thread-дампом), и именно поэтому прежняя реализация смены качества заменена автоматом, который
 * зовёт libVLC только с потока `MediaPlayer.submit`.
 */
class VlcQualitySwitchIntegrationTest {
    private val base: String? = System.getenv("ANIKO_VLC_IT")

    private class Rig(
        val base: String,
        val player: MediaPlayer,
        val controller: EmbedVideoController,
    ) {
        val state get() = controller.state.value
        val timeSamples = CopyOnWriteArrayList<Long>()

        val streams: Map<String, String> =
            linkedMapOf(
                "720p" to "$base/720/index.m3u8",
                "360p" to "$base/360/index.m3u8",
                "480p" to "$base/403/720/index.m3u8",
                "240p" to "$base/hang/360/index.m3u8",
            )

        fun start(defaultQuality: String = "720p") {
            controller.startResolvedStream(
                DesktopStreamResolver.Resolved(
                    streamUrl = streams.getValue(defaultQuality),
                    referer = "https://kodikplayer.com/",
                    qualityStreams = streams,
                ),
            )
        }

        fun videoHeight(): Int? =
            runCatching {
                player
                    .media()
                    .info()
                    ?.videoTracks()
                    ?.firstOrNull()
                    ?.height()
            }.getOrNull()

        /** MRL реально загруженного медиа — не зависит от того, успели ли распарситься дорожки (на паузе их может не быть). */
        fun mrl(): String =
            runCatching {
                player
                    .media()
                    .info()
                    ?.mrl()
            }.getOrNull().orEmpty()

        /** Высота кадра появляется, только когда декодер отдал первый кадр, — ждём, а не читаем «сразу». */
        fun awaitVideoHeight(expected: Int) = await("video height $expected") { videoHeight() == expected }

        fun await(
            what: String,
            timeoutMs: Long = AWAIT_TIMEOUT_MS,
            condition: () -> Boolean,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return
                Thread.sleep(POLL_MS)
            }
            throw AssertionError("timeout waiting for: $what; state=$state")
        }

        fun awaitPlayingAt(minMs: Long) = await("playing at >= $minMs") { state.isPlaying && state.currentTimeMs >= minMs }

        /** Ждёт, пока UI-состояние поймает время ≥ [minMs] после перемотки (libVLC сначала «эхом» шлёт цель). */
        fun seekAndSettle(positionMs: Long) {
            player.controls().setTime(positionMs)
            awaitPlayingAt(positionMs - SEEK_SETTLE_SLACK_MS)
            Thread.sleep(SETTLE_MS)
        }

        fun watchTime() {
            player.events().addMediaPlayerEventListener(
                object : MediaPlayerEventAdapter() {
                    override fun timeChanged(
                        mp: MediaPlayer,
                        newTime: Long,
                    ) {
                        timeSamples += newTime
                    }
                },
            )
        }
    }

    private fun report(line: String) {
        System.getenv("ANIKO_VLC_IT_OUT")?.let { File(it).appendText(line + "\n") }
    }

    /** `true`, если libVLC нашёлся за [DISCOVERY_TIMEOUT_MS]; поиск в отдельном daemon-потоке, чтобы его зацикливание не вешало тест. */
    private fun libVlcFound(): Boolean {
        val found = AtomicBoolean(false)
        val worker = Thread { found.set(runCatching { NativeDiscovery().discover() }.getOrDefault(false)) }
        worker.isDaemon = true
        worker.start()
        worker.join(DISCOVERY_TIMEOUT_MS)
        return found.get()
    }

    private fun withRig(
        preferred: Int? = null,
        switchTimeoutMs: Long? = null,
        block: Rig.() -> Unit,
    ) {
        assumeTrue("set ANIKO_VLC_IT=<local HLS base url> to run", base != null)
        assumeTrue("libVLC not found", libVlcFound())
        val watchdog =
            Thread {
                try {
                    Thread.sleep(HARD_DEADLINE_MS)
                    System.err.println("VlcQualitySwitchIntegrationTest: hard deadline exceeded, halting the test JVM")
                    Runtime.getRuntime().halt(WATCHDOG_EXIT_CODE)
                } catch (_: InterruptedException) {
                    // сценарий закончился вовремя
                }
            }
        watchdog.isDaemon = true
        watchdog.start()
        val factory = MediaPlayerFactory("--vout=dummy", "--aout=dummy", "--quiet")
        val player = factory.mediaPlayers().newMediaPlayer()
        val controller = EmbedVideoController()
        if (switchTimeoutMs != null) controller.qualitySwitchTimeoutMs = switchTimeoutMs
        controller.setPreferredQuality(preferred)
        controller.attach(player)
        try {
            Rig(checkNotNull(base), player, controller).block()
        } finally {
            controller.detach()
            runCatching { player.controls().stop() }
            runCatching { player.release() }
            runCatching { factory.release() }
            watchdog.interrupt()
        }
    }

    @Test
    fun switchKeepsPositionAndKeepsPlaying() =
        withRig {
            start()
            awaitPlayingAt(500L)
            seekAndSettle(60_000L)
            val before = state.currentTimeMs
            watchTime()
            val startedAt = System.currentTimeMillis()

            controller.setQuality("360p")
            await("switch to 360p") { state.switchingQualityTo == null && state.currentQuality == "360p" && state.isPlaying }
            await("first 360p frames") { timeSamples.size >= 3 }
            val switchMs = System.currentTimeMillis() - startedAt

            val min = timeSamples.minOrNull() ?: -1L
            report(
                "A switch 720p->360p: before=$before ms, min time sample after switch=$min ms, " +
                    "took=$switchMs ms, height=${videoHeight()}",
            )
            assertEquals(360, videoHeight())
            // Никакого «перезапуска с начала»: ни одного отсчёта времени раньше исходной позиции (допуск на сегмент 4 с).
            assertTrue(min >= before - POSITION_TOLERANCE_MS, "playback restarted: min=$min before=$before")
            assertNull(state.qualitySwitchFailure)
        }

    @Test
    fun pausedSwitchStaysPausedAtSamePosition() =
        withRig {
            start()
            awaitPlayingAt(500L)
            seekAndSettle(30_000L)
            controller.pause()
            await("paused") { !state.isPlaying }
            val pausedAt = player.status().time()

            controller.setQuality("360p")
            await("switch to 360p") { state.switchingQualityTo == null && state.currentQuality == "360p" }
            Thread.sleep(PAUSED_OBSERVATION_MS)

            val after = player.status().time()
            report("B paused switch: pausedAt=$pausedAt after=$after playerState=${player.status().state()} uiPlaying=${state.isPlaying}")
            assertEquals(false, state.isPlaying)
            assertTrue(!player.status().isPlaying, "must stay paused")
            assertTrue(abs(after - pausedAt) <= POSITION_TOLERANCE_MS, "position drifted: $pausedAt -> $after")
            // На паузе кадр может ещё не декодироваться (дорожек нет) — проверяем по MRL загруженного медиа.
            assertTrue("/360/" in mrl(), "new quality must be loaded, mrl=${mrl()}")
        }

    @Test
    fun rateAndVolumeSurviveSwitch() =
        withRig {
            start()
            awaitPlayingAt(500L)
            controller.setPlaybackRate(1.5f)
            player.audio().setVolume(55)
            val volumeBefore = player.audio().volume()
            seekAndSettle(20_000L)

            controller.setQuality("360p")
            await("switch to 360p") { state.switchingQualityTo == null && state.currentQuality == "360p" }
            await("rate applied") { player.status().rate() > 1.4f }

            report("C rate/volume: rate=${player.status().rate()} volumeBefore=$volumeBefore volumeAfter=${player.audio().volume()}")
            assertEquals(1.5f, player.status().rate())
            assertEquals(volumeBefore, player.audio().volume())
        }

    @Test
    fun rapidSwitchingLastSelectionWins() =
        withRig {
            start()
            awaitPlayingAt(500L)
            seekAndSettle(45_000L)
            val before = state.currentTimeMs
            watchTime()

            controller.setQuality("360p")
            controller.setQuality("720p")
            controller.setQuality("360p")
            await("final 360p") { state.switchingQualityTo == null && state.currentQuality == "360p" && state.isPlaying }
            await("frames") { timeSamples.size >= 3 }

            val min = timeSamples.minOrNull() ?: -1L
            report("D rapid switching: before=$before min sample=$min height=${videoHeight()} quality=${state.currentQuality}")
            assertEquals(360, videoHeight())
            assertTrue(min >= before - POSITION_TOLERANCE_MS, "position lost by rapid switching: min=$min before=$before")
            assertNull(state.qualitySwitchFailure)
        }

    @Test
    fun deadQuality_rollsBackToPreviousAtSamePosition() =
        withRig {
            start()
            awaitPlayingAt(500L)
            seekAndSettle(50_000L)
            val before = state.currentTimeMs

            controller.setQuality("480p") // /403/… — CDN отвечает 403
            await("rollback reported") { state.qualitySwitchFailure != null && state.switchingQualityTo == null }
            await("playing again") { state.isPlaying }

            val failure = assertNotNull(state.qualitySwitchFailure)
            awaitVideoHeight(720)
            await("time advances after rollback") { player.status().time() > before + MIN_PLAYED_MS }
            report(
                "E dead quality: failure=$failure quality=${state.currentQuality} " +
                    "height=${videoHeight()} time=${player.status().time()} before=$before",
            )
            assertEquals("480p", failure.requested)
            assertEquals("720p", failure.restoredTo)
            assertEquals("720p", state.currentQuality)
            assertTrue("/403/" !in mrl(), "the dead quality must not stay loaded, mrl=${mrl()}")
            assertTrue(state.isVideoFound, "player must not be declared dead after a successful rollback")
            assertTrue(player.status().time() >= before - POSITION_TOLERANCE_MS, "rollback lost position")
        }

    @Test
    fun hungQuality_timesOutAndRollsBack() =
        withRig(switchTimeoutMs = SHORT_SWITCH_TIMEOUT_MS) {
            start()
            awaitPlayingAt(500L)
            seekAndSettle(40_000L)
            val startedAt = System.currentTimeMillis()

            controller.setQuality("240p") // /hang/… — сервер не отвечает
            await("rollback reported", timeoutMs = LONG_AWAIT_MS) {
                state.qualitySwitchFailure != null && state.switchingQualityTo == null
            }
            val tookMs = System.currentTimeMillis() - startedAt

            val failure = assertNotNull(state.qualitySwitchFailure)
            report("F hung quality: failure=$failure took=$tookMs ms quality=${state.currentQuality} time=${player.status().time()}")
            assertEquals("720p", failure.restoredTo)
            assertTrue(tookMs >= SHORT_SWITCH_TIMEOUT_MS - POLL_MS, "rolled back before the timeout: $tookMs")
            await("playing again") { state.isPlaying }
        }

    @Test
    fun preferredQuality_isAppliedAtStart_withoutShowingASwitch() =
        withRig(preferred = 360) {
            val sawSwitch = AtomicBoolean(false)
            val watcher =
                Thread {
                    try {
                        while (true) {
                            if (state.switchingQualityTo != null) sawSwitch.set(true)
                            Thread.sleep(SWITCH_WATCH_MS)
                        }
                    } catch (_: InterruptedException) {
                        // тест закончил наблюдение
                    }
                }
            watcher.isDaemon = true
            watcher.start()
            start(defaultQuality = "720p")
            await("360p playing") { state.currentQuality == "360p" && state.isPlaying && state.currentTimeMs > MIN_PLAYED_MS }
            watcher.interrupt()

            report(
                "G preferred=360 at start: quality=${state.currentQuality} height=${videoHeight()} " +
                    "sawSwitchIndicator=${sawSwitch.get()}",
            )
            assertEquals(360, videoHeight())
            assertEquals(false, sawSwitch.get())
            assertNull(state.qualitySwitchFailure)
        }

    private companion object {
        /** Жёсткий предел на один сценарий; дальше watchdog гасит тестовую JVM. */
        const val HARD_DEADLINE_MS = 150_000L
        const val WATCHDOG_EXIT_CODE = 70
        const val DISCOVERY_TIMEOUT_MS = 20_000L
        const val AWAIT_TIMEOUT_MS = 30_000L
        const val LONG_AWAIT_MS = 60_000L
        const val POLL_MS = 50L
        const val SETTLE_MS = 1_500L
        const val SEEK_SETTLE_SLACK_MS = 3_000L
        const val PAUSED_OBSERVATION_MS = 2_500L
        const val SHORT_SWITCH_TIMEOUT_MS = 5_000L
        const val SWITCH_WATCH_MS = 10L
        const val MIN_PLAYED_MS = 300L

        /** Допуск на сегментную точность перемотки libVLC по HLS (замеры: ошибка 1.1–2.3 с при 4-с сегментах). */
        const val POSITION_TOLERANCE_MS = 4_500L
    }
}
