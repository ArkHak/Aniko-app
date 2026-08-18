package com.aniko.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Порог «конец серии» — общий триггер P8.T4 (баннер следующей серии) и P8.T8 (авто-отметка
 * просмотра). Тесты фиксируют именно те три случая, которые в живом воспроизведении ломают
 * наивную реализацию `duration - currentTime <= 20s`: нет метаданных, играет пре-ролл,
 * счётчик не дотянулся до `duration`.
 */
class EmbedVideoProgressTest {
    private val episode = EmbedVideoState(isVideoFound = true, durationMs = TWENTY_FOUR_MINUTES_MS)

    @Test
    fun remaining_isUnknownUntilVideoFound() {
        assertNull(EmbedVideoState().remainingMs())
        assertFalse(EmbedVideoState().isNearEnd())
    }

    @Test
    fun remaining_isUnknownUntilLoadedMetadata() {
        // `durationMs == null` — состояние до события `loadedmetadata` (там `duration` = NaN).
        val state = EmbedVideoState(isVideoFound = true, currentTimeMs = 1_000L, durationMs = null)
        assertNull(state.remainingMs())
        assertFalse(state.isNearEnd())
    }

    @Test
    fun shortClipIsNotTracked() {
        // Пре-ролл/заставка на 25 с: без границы MIN_TRACKED_DURATION_MS это дало бы
        // «серия подходит к концу» уже на нулевой секунде рекламы.
        val preRoll = EmbedVideoState(isVideoFound = true, currentTimeMs = 0L, durationMs = 25_000L)
        assertNull(preRoll.remainingMs())
        assertFalse(preRoll.isNearEnd())
        assertFalse(preRoll.isEpisodeFinished())
    }

    @Test
    fun nearEnd_flipsExactlyAtThreshold() {
        val before = episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS - END_OF_EPISODE_THRESHOLD_MS - 1)
        val at = episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS - END_OF_EPISODE_THRESHOLD_MS)
        assertFalse(before.isNearEnd())
        assertTrue(at.isNearEnd())
    }

    @Test
    fun middleOfEpisodeIsNeitherNearEndNorFinished() {
        val middle = episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS / 2)
        assertEquals(TWENTY_FOUR_MINUTES_MS / 2, middle.remainingMs())
        assertFalse(middle.isNearEnd())
        assertFalse(middle.isEpisodeFinished())
    }

    @Test
    fun finished_toleratesCounterNotReachingDuration() {
        // Браузер штатно останавливает `currentTime` на последнем декодированном кадре —
        // ровного равенства с `duration` ждать нельзя.
        val almost = episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS - 400L)
        assertTrue(almost.isEpisodeFinished())
        assertTrue(almost.isNearEnd())
    }

    @Test
    fun finished_isFalseWhileBannerWindowStillRunning() {
        val inBanner = episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS - 10_000L)
        assertTrue(inBanner.isNearEnd())
        assertFalse(inBanner.isEpisodeFinished())
    }

    @Test
    fun remaining_neverGoesNegative() {
        val overshoot = episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS + 5_000L)
        assertEquals(0L, overshoot.remainingMs())
        assertTrue(overshoot.isEpisodeFinished())
    }

    @Test
    fun countdown_roundsUpSoItNeverShowsZeroTooEarly() {
        assertEquals(15, episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS - 15_000L).secondsToEpisodeEnd())
        // 14 001 мс осталось — это всё ещё «15 с», а не «14 с».
        assertEquals(15, episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS - 14_001L).secondsToEpisodeEnd())
        assertEquals(0, episode.copy(currentTimeMs = TWENTY_FOUR_MINUTES_MS).secondsToEpisodeEnd())
        assertNull(EmbedVideoState().secondsToEpisodeEnd())
    }

    private companion object {
        /** Типовой хронометраж серии (`Release.duration` = 24 мин, см. `docs/REELWAVE_PLAN.md`). */
        const val TWENTY_FOUR_MINUTES_MS = 24L * 60L * 1_000L
    }
}
