package com.aniko.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Регрессия на матчинг видеохостов (Фаза 8).
 *
 * До фикса `fromKey` проверял «является ли машинный ключ enum'а подстрокой имени из API»
 * (`it.key in normalized`) — из 12 хостов резолвились только KODIK/SIBNET/RUTUBE, остальные 9
 * молча улетали в UNKNOWN. Имена в этом тесте — не выдуманные, а реально встреченные в живой
 * выборке ~470 пар (релиз, тип озвучки).
 */
class VideoHostTest {
    @Test
    fun fromKey_liveSourceNames_resolveToRealHosts() {
        assertEquals(VideoHost.KODIK, VideoHost.fromKey("Kodik"))
        assertEquals(VideoHost.SIBNET, VideoHost.fromKey("Sibnet"))
        assertEquals(VideoHost.RUTUBE, VideoHost.fromKey("RuTube"))
        // Ниже — те девять, что раньше давали UNKNOWN.
        assertEquals(VideoHost.VK_VIDEO, VideoHost.fromKey("VK Видео"))
        assertEquals(VideoHost.ANILIBRIA, VideoHost.fromKey("Libria"))
        assertEquals(VideoHost.ANILIBRIA, VideoHost.fromKey("Liberty"))
        assertEquals(VideoHost.STUDIO_MIR, VideoHost.fromKey("TSM"))
        assertEquals(VideoHost.SOVET_ROMANTICA, VideoHost.fromKey("Sovet (не работает)"))
    }

    @Test
    fun fromKey_unknownName_staysUnknown() {
        assertEquals(VideoHost.UNKNOWN, VideoHost.fromKey("Какой-то новый хостинг"))
        assertEquals(VideoHost.UNKNOWN, VideoHost.fromKey(null))
        assertEquals(VideoHost.UNKNOWN, VideoHost.fromKey(""))
    }

    @Test
    fun fromUrl_realPlaybackUrls_resolveByDomain() {
        assertEquals(VideoHost.KODIK, VideoHost.fromUrl("https://kodikplayer.com/seria/123/hash/720p"))
        assertEquals(VideoHost.SIBNET, VideoHost.fromUrl("https://video.sibnet.ru/shell.php?videoid=1"))
        assertEquals(VideoHost.ANILIBRIA, VideoHost.fromUrl("https://anixart.libria.fun/public/iframe.php?id=1"))
        assertEquals(VideoHost.RUTUBE, VideoHost.fromUrl("https://rutube.ru/play/embed/abc/"))
        assertEquals(VideoHost.SOVET_ROMANTICA, VideoHost.fromUrl("https://sovetromantica.com/embed/episode_1"))
        assertEquals(VideoHost.STUDIO_MIR, VideoHost.fromUrl("https://api.studiomir.club/embed/1"))
    }

    @Test
    fun fromUrl_matchesOnLabelBoundary_notSubstring() {
        // Подстроковый матчинг (как в старом fromKey) опознал бы это как SIBNET.
        assertEquals(VideoHost.UNKNOWN, VideoHost.fromUrl("https://notsibnet.ru/shell.php"))
        assertEquals(VideoHost.UNKNOWN, VideoHost.fromUrl(null))
    }

    @Test
    fun resolve_prefersDomainOverName() {
        // Имя источника на стороне Anixart переименовали, домен — нет: побеждает домен.
        assertEquals(
            VideoHost.ANILIBRIA,
            VideoHost.resolve(url = "https://anixart.libria.fun/public/iframe.php", name = "Совершенно новое имя"),
        )
        // Домена нет — падаем на имя.
        assertEquals(VideoHost.KODIK, VideoHost.resolve(url = null, name = "Kodik"))
    }
}
