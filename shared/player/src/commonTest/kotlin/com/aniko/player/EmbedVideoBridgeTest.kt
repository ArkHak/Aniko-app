package com.aniko.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты на две вещи, от которых зависит корректность моста и которые проверяемы без WebView:
 * фильтр origin'ов (иначе состояние смешается с рекламными фреймами — в живом спайке команду
 * перехватил `mc.yandex.ru`) и разбор сообщений от JS.
 */
class EmbedVideoBridgeTest {
    @Test
    fun originFilter_acceptsVideoHostAndItsSubdomains() {
        val filter = EmbedOriginFilter("https://video.sibnet.ru/shell.php?videoid=1")
        assertTrue(filter.accepts("https://video.sibnet.ru"))
        assertTrue(filter.accepts("https://st.sibnet.ru"))
        assertTrue(filter.accepts("https://sibnet.ru"))
    }

    @Test
    fun originFilter_rejectsForeignFrames() {
        val filter = EmbedOriginFilter("https://video.sibnet.ru/shell.php?videoid=1")
        // Ровно этот фрейм в живом спайке перехватил команду.
        assertFalse(filter.accepts("https://mc.yandex.ru"))
        assertFalse(filter.accepts("https://notsibnet.ru"))
        assertFalse(filter.accepts("null"))
        assertFalse(filter.accepts("about:blank"))
        assertFalse(filter.accepts(null))
    }

    @Test
    fun originFilter_kodikAcceptsWholeDomainFamily() {
        val filter = EmbedOriginFilter("https://kodikplayer.com/seria/123/hash/720p")
        assertTrue(filter.accepts("https://kodikplayer.com"))
        // Плеер Kodik может оказаться на соседнем домене группы.
        assertTrue(filter.accepts("https://aniqit.com"))
        assertFalse(filter.accepts("https://mc.yandex.ru"))
    }

    @Test
    fun parseState_beforeLoadedMetadata_durationIsNull() {
        val state = parseEmbedVideoState("v1|1|0|0|-|1")
        assertEquals(EmbedVideoState(isVideoFound = true, isPlaying = false, currentTimeMs = 0L, durationMs = null), state)
    }

    @Test
    fun parseState_playingWithMetadata() {
        val state = parseEmbedVideoState("v1|1|1|12345|1440000|2")
        assertEquals(
            EmbedVideoState(
                isVideoFound = true,
                isPlaying = true,
                currentTimeMs = 12345L,
                durationMs = 1_440_000L,
                playbackRate = 2f,
            ),
            state,
        )
    }

    @Test
    fun parseState_garbageIsIgnored() {
        assertNull(parseEmbedVideoState(""))
        assertNull(parseEmbedVideoState("v1|1|1"))
        // Чужой протокол — не наше сообщение, состояние трогать нельзя.
        assertNull(parseEmbedVideoState("v2|1|1|0|-|1"))
    }

    @Test
    fun registrableDomain_stripsSubdomainsAndPortsAndPaths() {
        assertEquals("libria.fun", registrableDomainOf("https://anixart.libria.fun/public/iframe.php?id=1"))
        assertEquals("sibnet.ru", registrableDomainOf("http://video.sibnet.ru:8080/shell.php"))
        assertNull(registrableDomainOf("https://localhost"))
    }
}
