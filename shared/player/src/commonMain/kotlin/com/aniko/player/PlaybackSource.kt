package com.aniko.player

import com.aniko.model.VideoHost

/**
 * Что именно нужно проиграть.
 *
 * Anixart отдаёт разнородные источники: часть хостов даёт прямой поток (m3u8/mp4),
 * часть — только embed-страницу стороннего плеера. Плеер обязан различать эти
 * два случая на уровне типов, иначе логика расползётся по UI.
 */
sealed interface PlaybackSource {

    val host: VideoHost

    /** Прямой поток: HLS/MP4, играется нативным плеером платформы. */
    data class Direct(
        val url: String,
        override val host: VideoHost = VideoHost.UNKNOWN,
        /** Некоторым CDN нужен Referer/User-Agent. */
        val headers: Map<String, String> = emptyMap(),
        val startPositionMs: Long = 0L,
    ) : PlaybackSource

    /** Embed-страница стороннего плеера — рендерится в WebView / WKWebView. */
    data class Embed(
        val url: String,
        override val host: VideoHost = VideoHost.UNKNOWN,
        val referer: String? = null,
    ) : PlaybackSource
}
