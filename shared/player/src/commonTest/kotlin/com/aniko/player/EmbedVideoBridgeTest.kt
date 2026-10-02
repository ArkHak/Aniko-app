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

        // P16 (2026-09-10): хост объявляет качества — они приходят хвостовыми полями и должны
        // доезжать до UI списком в порядке хоста, а не хардкодом.
        val withQualities = parseEmbedVideoState("v1|1|1|1000|2000|1|360p,480p,720p|480p")
        assertEquals(listOf("360p", "480p", "720p"), withQualities?.availableQualities)
        assertEquals("480p", withQualities?.currentQuality)
    }

    @Test
    fun registrableDomain_stripsSubdomainsAndPortsAndPaths() {
        assertEquals("libria.fun", registrableDomainOf("https://anixart.libria.fun/public/iframe.php?id=1"))
        assertEquals("sibnet.ru", registrableDomainOf("http://video.sibnet.ru:8080/shell.php"))
        assertNull(registrableDomainOf("https://localhost"))
    }

    @Test
    fun parseChromeDebug_extractsEntriesAndStaysBelowStateFieldCount() {
        // Отладочное сообщение НЕ должно парситься как state: у него меньше EMBED_BRIDGE_FIELDS
        // полей — иначе отчёт затирал бы состояние плеера.
        val raw = "v1|dbgchrome|div.endscreen@40;div#next-episode.fp-next;span"
        assertNull(parseEmbedVideoState(raw))

        assertEquals(
            listOf("div.endscreen@40", "div#next-episode.fp-next", "span"),
            parseEmbedChromeDebug(raw),
        )
    }

    @Test
    fun parseChromeDebug_ignoresNonDebugMessages() {
        assertEquals(emptyList(), parseEmbedChromeDebug(""))
        assertEquals(emptyList(), parseEmbedChromeDebug("v1|1|1|0|-|1"))
        assertEquals(emptyList(), parseEmbedChromeDebug("v2|dbgchrome|div.x"))
        // Пустой отчёт (видимых элементов нет) — пустой список, а не список из одной пустой строки.
        assertEquals(emptyList(), parseEmbedChromeDebug("v1|dbgchrome|"))
    }

    @Test
    fun bridgeScript_kodikCssCoversWholeDomainFamily() {
        val script = embedBridgeScript()

        // Семейство доменов Kodik (KODIK_EMBED_HOSTS) — одна и та же CSS-таблица на все суффиксы.
        listOf("kodik.cc", "kodik.info", "kodik-hd.com", "kodik.biz", "aniqit.com", "kodikplayer.com", "anixmirai.com")
            .forEach { host -> assertTrue("'$host'" in script) }
        // Эндскрин «следующая серия» скрывается wildcard-селекторами (разметка Kodik плавает).
        assertTrue("'[class*=\"endscreen\"]'" in script)
        assertTrue("'[class*=\"next-ep\"]'" in script)
        // Скрытие только через CSS — <video> и его контейнеры в селекторы не попадают.
        assertTrue("html.aniko-video-found .fp-play" in script)
        assertFalse("'.fp-engine'" in script)
        // Debug-хук выключен по умолчанию и включается глобальным флагом страницы.
        assertTrue("__anikoDebugChrome" in script)
        assertTrue("'|$EMBED_BRIDGE_DEBUG_MARKER|'" in script)
    }

    @Test
    fun bridgeScript_snapshotsPlaybackBeforeQualitySwitch_andRestoresItAfterReload() {
        val script = embedBridgeScript()

        // Снимок ДО клика по пункту меню хоста + восстановление после перезагрузки источника:
        // позиция, пауза, скорость, громкость (иначе хост может «перезапустить с начала»).
        assertTrue("qualityRestore = captureQualityRestore()" in script)
        assertTrue("function applyQualityRestore(final)" in script)
        assertTrue("video.currentTime = r.t" in script)
        assertTrue("video.playbackRate = r.rate" in script)
        assertTrue("video.volume = r.vol" in script)
        assertTrue("video.pause()" in script)
        // Реагируем именно на перезагрузку источника, а не на любое событие.
        assertTrue("'emptied' || type === 'loadstart'" in script)
        // Нет такого пункта меню — снимок сбрасывается, чтобы не «протух» до следующей загрузки.
        assertTrue("if (!switchQuality(cmd.slice(8))) { qualityRestore = null; }" in script)
    }
}
