package com.aniko.player

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Формат медиа-опций libVLC для смены качества. Сам плеер здесь НЕ поднимается (как и во всех
 * desktop-тестах проекта — `NativeDiscovery` на CI без libVLC зацикливается, PR #76): проверяется
 * только чистая функция, но именно от точности имён опций зависит, стартует ли новый поток с той же
 * секунды и на паузе, — опечатка молча превратилась бы в «старт с нуля».
 */
class VlcQualitySwitchEngineTest {
    @Test
    fun startFromBeginningWithoutReferer_hasNoOptions() {
        assertEquals(emptyList(), VlcQualitySwitchEngine.mediaOptions(referer = null, startPositionMs = 0L, startPaused = false))
    }

    @Test
    fun startPositionIsPassedInSecondsWithMillisecondPrecision() {
        val options = VlcQualitySwitchEngine.mediaOptions(referer = null, startPositionMs = 125_250L, startPaused = false)

        assertEquals(listOf(":start-time=125.25"), options)
    }

    @Test
    fun pausedSwitchStartsPaused() {
        val options = VlcQualitySwitchEngine.mediaOptions(referer = null, startPositionMs = 61_000L, startPaused = true)

        assertEquals(listOf(":start-time=61.0", ":start-paused"), options)
    }

    @Test
    fun refererGoesFirst() {
        val options =
            VlcQualitySwitchEngine.mediaOptions(referer = "https://kodikplayer.com/", startPositionMs = 1_500L, startPaused = false)

        assertEquals(listOf(":http-referrer=https://kodikplayer.com/", ":start-time=1.5"), options)
    }
}
