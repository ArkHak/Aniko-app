package com.aniko.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Автомат смены качества без VLC: фейковый [QualitySwitchEngine] пишет журнал вызовов, а тест сам
 * «играет за libVLC» — шлёт `mediaChanged`/`playing`/`error` в том порядке, в котором их присылает
 * настоящий плеер (порядок проверен на реальном libVLC, см. отчёт задачи).
 */
class QualitySwitchCoordinatorTest {
    private val streams =
        linkedMapOf(
            "1080p" to "https://cdn/1080.m3u8",
            "720p" to "https://cdn/720.m3u8",
            "480p" to "https://cdn/480.m3u8",
            "360p" to "https://cdn/360.m3u8",
        )

    private class FakeEngine : QualitySwitchEngine {
        var snapshotToReturn = PlaybackSnapshot(positionMs = 125_000L, isPlaying = true)
        var acceptLoads = true
        var reportedPosition: Long? = 125_000L
        val calls = mutableListOf<String>()
        val timeouts = mutableListOf<Pair<Long, Long>>()

        override fun snapshot(): PlaybackSnapshot = snapshotToReturn

        override fun load(
            url: String,
            startPositionMs: Long,
            startPaused: Boolean,
        ): Boolean {
            calls += "load($url,$startPositionMs,paused=$startPaused)"
            return acceptLoads
        }

        override fun positionMs(): Long? = reportedPosition

        override fun seekTo(positionMs: Long) {
            calls += "seek($positionMs)"
        }

        override fun setPaused(paused: Boolean) {
            calls += "paused($paused)"
        }

        override fun setRate(rate: Float) {
            calls += "rate($rate)"
        }

        override fun setVolume(volume: Int) {
            calls += "volume($volume)"
        }

        override fun setMuted(muted: Boolean) {
            calls += "muted($muted)"
        }

        override fun scheduleTimeout(
            token: Long,
            delayMs: Long,
        ) {
            timeouts += token to delayMs
        }
    }

    private val engine = FakeEngine()
    private val states = mutableListOf<QualitySwitchState>()
    private val coordinator = QualitySwitchCoordinator(engine, states::add)

    private val latest get() = states.last()

    /** Стабильно играем [quality]: старт без предпочтения + подтверждение libVLC. */
    private fun startOn(quality: String = "720p") {
        coordinator.startPlayback(streams, defaultQuality = quality, preferredQuality = null)
        coordinator.onMediaChanged()
        coordinator.onPlaying()
        engine.calls.clear()
        states.clear()
    }

    /** Ответ libVLC на очередную загрузку: `mediaChanged` → `playing`. */
    private fun libVlcStartsPlaying() {
        coordinator.onMediaChanged()
        coordinator.onPlaying()
    }

    // ---------- старт ----------

    @Test
    fun start_withoutPreference_playsDefaultFromBeginning_andIsNotASwitch() {
        coordinator.startPlayback(streams, defaultQuality = "1080p", preferredQuality = null)

        assertEquals(listOf("load(https://cdn/1080.m3u8,0,paused=false)"), engine.calls)
        assertEquals("1080p", latest.currentQuality)
        assertNull(latest.switchingTo)
        // Ошибки плеера без идущей смены — не наши: контроллер объявит плеер мёртвым как раньше.
        assertFalse(coordinator.onError())
    }

    @Test
    fun start_withPreference_triesPreferred_withoutShowingASwitchIndicator() {
        coordinator.startPlayback(streams, defaultQuality = "1080p", preferredQuality = "480p")

        assertEquals(listOf("load(https://cdn/480.m3u8,0,paused=false)"), engine.calls)
        // Стартовый выбор качества по настройке пользователю не «смена»: индикатор/заморозка не нужны,
        // а чип сразу показывает выбранное в настройках качество, не умолчание резолвера.
        assertNull(latest.switchingTo)
        assertEquals("480p", latest.currentQuality)

        libVlcStartsPlaying()

        assertEquals("480p", latest.currentQuality)
        assertNull(latest.failure)
    }

    @Test
    fun start_withPreference_doesNotTouchUserVolumeRateOrMute() {
        coordinator.startPlayback(streams, defaultQuality = "1080p", preferredQuality = "480p")
        libVlcStartsPlaying()

        // У стартового снимка нет громкости/скорости/mute — пользовательские значения не сбрасываем.
        assertEquals(listOf("load(https://cdn/480.m3u8,0,paused=false)"), engine.calls)
    }

    @Test
    fun start_preferredEqualsDefault_isPlainStart() {
        coordinator.startPlayback(streams, defaultQuality = "720p", preferredQuality = "720p")

        assertEquals(listOf("load(https://cdn/720.m3u8,0,paused=false)"), engine.calls)
        assertFalse(coordinator.onError())
    }

    @Test
    fun start_preferredNotInStreams_isIgnored() {
        coordinator.startPlayback(streams, defaultQuality = "720p", preferredQuality = "240p")

        assertEquals(listOf("load(https://cdn/720.m3u8,0,paused=false)"), engine.calls)
    }

    @Test
    fun start_preferredFails_fallsBackToResolverDefault_andReportsIt() {
        coordinator.startPlayback(streams, defaultQuality = "1080p", preferredQuality = "360p")
        coordinator.onMediaChanged()
        assertTrue(coordinator.onError())

        assertEquals("load(https://cdn/1080.m3u8,0,paused=false)", engine.calls.last())
        // Пока идёт откат стартовой загрузки, чип показывает то, на что откатываемся.
        assertEquals("1080p", latest.currentQuality)

        libVlcStartsPlaying()

        assertEquals("1080p", latest.currentQuality)
        assertEquals(QualitySwitchFailure(id = 1, requested = "360p", restoredTo = "1080p"), latest.failure)
    }

    // ---------- обычная смена ----------

    @Test
    fun request_startsNewStreamAtSameSecond_andStaysPlaying() {
        startOn("720p")

        coordinator.request("480p")

        assertEquals(listOf("load(https://cdn/480.m3u8,125000,paused=false)"), engine.calls)
        assertEquals("480p", latest.switchingTo)
        // Пока идёт смена, «текущее» качество остаётся прежним — оно реально играет до подмены.
        assertEquals("720p", latest.currentQuality)
        assertEquals(listOf(1L to QualitySwitchCoordinator.DEFAULT_SWITCH_TIMEOUT_MS), engine.timeouts)
    }

    @Test
    fun request_whilePaused_startsNewStreamPaused() {
        startOn("720p")
        engine.snapshotToReturn = PlaybackSnapshot(positionMs = 61_500L, isPlaying = false)

        coordinator.request("360p")

        assertEquals(listOf("load(https://cdn/360.m3u8,61500,paused=true)"), engine.calls)
    }

    @Test
    fun success_restoresRateVolumeMuteAndPause_thenPublishesNewQuality() {
        startOn("720p")
        engine.snapshotToReturn =
            PlaybackSnapshot(positionMs = 125_000L, isPlaying = false, rate = 1.5f, volume = 80, isMuted = true)

        coordinator.request("480p")
        engine.calls.clear()
        libVlcStartsPlaying()

        assertEquals(listOf("rate(1.5)", "volume(80)", "muted(true)", "paused(true)"), engine.calls)
        assertEquals("480p", latest.currentQuality)
        assertNull(latest.switchingTo)
        assertNull(latest.failure)
    }

    @Test
    fun success_whilePlaying_doesNotForcePause_andSkipsDefaultRate() {
        startOn("720p")
        engine.snapshotToReturn = PlaybackSnapshot(positionMs = 125_000L, isPlaying = true, rate = 1f, volume = 100)

        coordinator.request("480p")
        engine.calls.clear()
        libVlcStartsPlaying()

        assertEquals(listOf("volume(100)"), engine.calls)
    }

    @Test
    fun success_streamIgnoredStartPosition_isSeekedBack() {
        startOn("720p")
        coordinator.request("480p")
        engine.reportedPosition = 0L
        engine.calls.clear()

        libVlcStartsPlaying()

        assertEquals(listOf("seek(125000)"), engine.calls)
    }

    @Test
    fun success_positionWithinTolerance_isNotSeeked() {
        startOn("720p")
        coordinator.request("480p")
        engine.reportedPosition = 125_000L + QualitySwitchCoordinator.SEEK_TOLERANCE_MS
        engine.calls.clear()

        libVlcStartsPlaying()

        assertTrue(engine.calls.none { it.startsWith("seek") })
    }

    @Test
    fun success_unknownPosition_isNotSeeked() {
        startOn("720p")
        coordinator.request("480p")
        engine.reportedPosition = null
        engine.calls.clear()

        libVlcStartsPlaying()

        assertTrue(engine.calls.none { it.startsWith("seek") })
    }

    @Test
    fun request_forQualityThatIsAlreadyPlaying_isNoop() {
        startOn("720p")

        coordinator.request("720p")

        assertTrue(engine.calls.isEmpty())
        assertTrue(states.isEmpty())
    }

    @Test
    fun request_forUnknownQuality_isNoop() {
        startOn("720p")

        coordinator.request("240p")

        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun request_beforeStreamsAreKnown_isNoop() {
        coordinator.request("720p")

        assertTrue(engine.calls.isEmpty())
    }

    // ---------- гонки ----------

    @Test
    fun playingOfPreviousMedia_beforeMediaChanged_isIgnored() {
        startOn("720p")
        coordinator.request("480p")

        // libVLC ещё не подтвердил смену медиа — `playing` принадлежит СТАРОМУ потоку.
        coordinator.onPlaying()

        assertEquals("480p", latest.switchingTo)
        assertEquals("720p", latest.currentQuality)

        libVlcStartsPlaying()

        assertEquals("480p", latest.currentQuality)
    }

    @Test
    fun errorOfPreviousMedia_beforeMediaChanged_isIgnoredNotRolledBack() {
        startOn("720p")
        coordinator.request("480p")
        engine.calls.clear()

        // Событие consumed (true), но откат НЕ запускается: провал старого потока не наш.
        assertTrue(coordinator.onError())

        assertTrue(engine.calls.isEmpty())
        assertNull(latest.failure)
    }

    @Test
    fun rapidRequests_lastSelectionWins_andPositionIsNeverLost() {
        startOn("1080p")
        coordinator.request("720p")
        // Настоящий баг наивной реализации: «позиция» нового медиа, которое ещё ничего не сыграло, — 0.
        engine.snapshotToReturn = PlaybackSnapshot(positionMs = 0L, isPlaying = false)
        coordinator.request("480p")
        coordinator.request("360p")

        assertEquals(
            listOf(
                "load(https://cdn/720.m3u8,125000,paused=false)",
                "load(https://cdn/480.m3u8,125000,paused=false)",
                "load(https://cdn/360.m3u8,125000,paused=false)",
            ),
            engine.calls,
        )
        assertEquals("360p", latest.switchingTo)
    }

    @Test
    fun rapidRequests_playingOfIntermediateLoads_isIgnored_onlyLastCompletes() {
        startOn("1080p")
        coordinator.request("720p")
        coordinator.request("480p")
        engine.calls.clear()

        // libVLC присылает подтверждения по порядку загрузок: сначала за 720p, потом за 480p.
        coordinator.onMediaChanged()
        coordinator.onPlaying()
        assertEquals("480p", latest.switchingTo)
        assertEquals("1080p", latest.currentQuality)

        coordinator.onMediaChanged()
        coordinator.onPlaying()

        assertEquals("480p", latest.currentQuality)
        assertNull(latest.switchingTo)
    }

    @Test
    fun duplicateTapOnPendingTarget_doesNotRestartLoad() {
        startOn("720p")
        coordinator.request("480p")
        engine.calls.clear()

        coordinator.request("480p")

        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun returningToOriginalQualityDuringSwitch_reloadsIt_andSucceedsWithoutFailure() {
        startOn("720p")
        coordinator.request("480p")

        coordinator.request("720p")

        assertEquals("load(https://cdn/720.m3u8,125000,paused=false)", engine.calls.last())
        coordinator.onMediaChanged()
        coordinator.onMediaChanged()
        coordinator.onPlaying()
        assertEquals("720p", latest.currentQuality)
        assertNull(latest.failure)
    }

    @Test
    fun staleTimeoutOfReplacedLoad_isIgnored() {
        startOn("1080p")
        coordinator.request("720p")
        coordinator.request("480p")
        engine.calls.clear()

        // Токен 1 — загрузка 720p, её уже заменили: таймаут не должен ронять свежую загрузку.
        coordinator.onTimeout(token = 1L)

        assertTrue(engine.calls.isEmpty())
        assertNull(latest.failure)
        assertEquals("480p", latest.switchingTo)
    }

    // ---------- откат ----------

    @Test
    fun error_rollsBackToPreviousQuality_atSamePosition_andReportsFailure() {
        startOn("720p")
        coordinator.request("1080p")
        coordinator.onMediaChanged()
        engine.calls.clear()

        assertTrue(coordinator.onError())

        assertEquals(listOf("load(https://cdn/720.m3u8,125000,paused=false)"), engine.calls)
        assertEquals("720p", latest.switchingTo)

        libVlcStartsPlaying()

        assertEquals("720p", latest.currentQuality)
        assertNull(latest.switchingTo)
        assertEquals(QualitySwitchFailure(id = 1, requested = "1080p", restoredTo = "720p"), latest.failure)
    }

    @Test
    fun timeout_rollsBackJustLikeAnError() {
        startOn("720p")
        coordinator.request("480p")
        val token = engine.timeouts.last().first
        engine.calls.clear()

        coordinator.onTimeout(token)

        assertEquals(listOf("load(https://cdn/720.m3u8,125000,paused=false)"), engine.calls)
        libVlcStartsPlaying()
        assertEquals("720p", latest.currentQuality)
        assertEquals("480p", latest.failure?.requested)
    }

    @Test
    fun timeoutWithoutMediaChanged_stillLetsRollbackComplete() {
        startOn("720p")
        coordinator.request("480p")
        val token = engine.timeouts.last().first

        // libVLC так и не подтвердил смену медиа: после таймаута откат обязан принять свой `playing`.
        coordinator.onTimeout(token)
        libVlcStartsPlaying()

        assertEquals("720p", latest.currentQuality)
    }

    @Test
    fun rollbackFailure_marksPlayerDeadWithoutRestore() {
        startOn("720p")
        coordinator.request("480p")
        coordinator.onMediaChanged()
        coordinator.onError()
        coordinator.onMediaChanged()

        coordinator.onError()

        assertNull(latest.currentQuality)
        assertNull(latest.switchingTo)
        assertEquals(QualitySwitchFailure(id = 1, requested = "480p", restoredTo = null), latest.failure)
        assertFalse(coordinator.onError())
    }

    @Test
    fun engineRejectsLoad_rollsBack_thenDeadIfRollbackRejectedToo() {
        startOn("720p")
        engine.acceptLoads = false

        coordinator.request("480p")

        assertNull(latest.currentQuality)
        assertEquals(QualitySwitchFailure(id = 1, requested = "480p", restoredTo = null), latest.failure)
    }

    @Test
    fun engineRejectsOnlyNewLoad_rollbackSucceeds() {
        // Отклоняет только 480p: после отказа откат на 720p принимается.
        val rejectingEngine =
            object : QualitySwitchEngine by engine {
                override fun load(
                    url: String,
                    startPositionMs: Long,
                    startPaused: Boolean,
                ): Boolean {
                    engine.load(url, startPositionMs, startPaused)
                    return url.contains("720")
                }
            }
        val local = mutableListOf<QualitySwitchState>()
        val custom = QualitySwitchCoordinator(rejectingEngine, local::add)
        custom.startPlayback(streams, defaultQuality = "720p", preferredQuality = null)
        custom.onMediaChanged()
        custom.onPlaying()

        custom.request("480p")
        custom.onMediaChanged()
        custom.onPlaying()

        assertEquals("720p", local.last().currentQuality)
        assertEquals(QualitySwitchFailure(id = 1, requested = "480p", restoredTo = "720p"), local.last().failure)
    }

    @Test
    fun failureIds_growWithEveryFailure() {
        startOn("720p")
        coordinator.request("480p")
        coordinator.onMediaChanged()
        coordinator.onError()
        libVlcStartsPlaying()
        assertEquals(1, latest.failure?.id)

        coordinator.request("360p")
        // Новый запрос сбрасывает прошлое уведомление…
        assertNull(latest.failure)
        coordinator.onMediaChanged()
        coordinator.onError()
        libVlcStartsPlaying()

        // …а следующий сбой получает новый id: UI покажет уведомление ещё раз.
        assertEquals(2, latest.failure?.id)
    }

    @Test
    fun successfulSwitch_clearsPreviousFailure() {
        startOn("720p")
        coordinator.request("480p")
        coordinator.onMediaChanged()
        coordinator.onError()
        libVlcStartsPlaying()
        assertEquals("480p", latest.failure?.requested)

        coordinator.request("360p")
        libVlcStartsPlaying()

        assertNull(latest.failure)
        assertEquals("360p", latest.currentQuality)
    }

    @Test
    fun retargetDuringRollback_userChoiceWins_andOldFailureIsDropped() {
        startOn("720p")
        coordinator.request("480p")
        coordinator.onMediaChanged()
        coordinator.onError() // пошёл откат на 720p
        engine.calls.clear()

        coordinator.request("360p")

        assertEquals(listOf("load(https://cdn/360.m3u8,125000,paused=false)"), engine.calls)
        coordinator.onMediaChanged()
        coordinator.onMediaChanged()
        coordinator.onPlaying()
        assertEquals("360p", latest.currentQuality)
        assertNull(latest.failure)
    }

    @Test
    fun reset_forgetsEverything() {
        startOn("720p")
        coordinator.request("480p")

        coordinator.reset()

        assertNull(latest.currentQuality)
        assertNull(latest.switchingTo)
        assertFalse(coordinator.onError())
        engine.calls.clear()
        coordinator.request("480p")
        assertTrue(engine.calls.isEmpty())
    }
}
