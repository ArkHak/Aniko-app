package com.anixkmp.data.repository

import com.anixkmp.data.api.EpisodeApi
import com.anixkmp.data.mapper.toDomain
import com.anixkmp.model.AnixError
import com.anixkmp.model.Episode
import com.anixkmp.model.EpisodeSource
import com.anixkmp.model.VideoHost
import com.anixkmp.model.VoiceType

/**
 * Цепочка резолвинга плеера из `docs/api/ENDPOINTS.md`:
 * types → sources → episodes → target.
 */
class EpisodeRepository(private val episodeApi: EpisodeApi) {

    suspend fun voiceTypes(releaseId: Int): List<VoiceType> =
        episodeApi.types(releaseId).types.map { it.toDomain() }

    suspend fun sources(releaseId: Int, typeId: Int): List<EpisodeSource> =
        episodeApi.sources(releaseId, typeId).sources.map { it.toDomain() }

    suspend fun episodes(releaseId: Int, typeId: Int, sourceId: Int): List<Episode> =
        episodeApi.episodes(releaseId, typeId, sourceId).episodes.map { it.toDomain() }

    /**
     * Резолвит серию в URL. Является ли URL прямым потоком или embed-страницей —
     * решает вызывающая сторона (`:shared:player`), опираясь на [host].
     *
     * `[TODO: verify live]` — формат ответа сервера ещё не подтверждён.
     */
    suspend fun resolveTargetUrl(releaseId: Int, source: EpisodeSource, position: Int): String {
        val url = episodeApi.target(releaseId, source.id, position).episode?.url
        return url?.takeIf { it.isNotBlank() }
            ?: throw AnixError.PlaybackResolve(source.host)
    }

    suspend fun markWatched(releaseId: Int, sourceId: Int, position: Int) {
        episodeApi.markWatched(releaseId, sourceId, position)
    }

    suspend fun markUnwatched(releaseId: Int, sourceId: Int, position: Int) {
        episodeApi.markUnwatched(releaseId, sourceId, position)
    }

    /** Хосты, которые заведомо требуют embed-режима, а не прямого воспроизведения. */
    fun requiresEmbed(host: VideoHost): Boolean = when (host) {
        VideoHost.KODIK,
        VideoHost.SIBNET,
        VideoHost.RUTUBE,
        VideoHost.VK_VIDEO,
        VideoHost.OK_RU,
        VideoHost.MAIL_RU,
        VideoHost.MYVI,
        VideoHost.ALLVIDEO,
        VideoHost.SOVET_ROMANTICA,
        VideoHost.STUDIO_MIR,
        VideoHost.TORLOOK,
        VideoHost.UNKNOWN,
        -> true

        VideoHost.ANILIBRIA -> false
    }
}
