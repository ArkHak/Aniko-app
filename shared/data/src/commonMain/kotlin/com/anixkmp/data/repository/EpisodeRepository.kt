package com.anixkmp.data.repository

import com.anixkmp.data.api.EpisodeApi
import com.anixkmp.data.mapper.toDomain
import com.anixkmp.model.AnixError
import com.anixkmp.model.Episode
import com.anixkmp.model.EpisodeSource
import com.anixkmp.model.VideoHost
import com.anixkmp.model.VoiceType
import com.anixkmp.player.PlaybackSource

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
     * Резолвит серию в проигрываемый источник для WebView.
     *
     * `host` приходит от вызывающей стороны (экран плеера получает его из навигации,
     * см. `AnixDestination.Player.hostKey`) — репозиторий больше не хранит его сам между
     * вызовами `sources()`/`resolvePlaybackSource()`, это раньше было гонкой состояния между
     * параллельными экранами (см. код-ревью Фазы 5).
     *
     * Архитектурное решение (не пересматривать в рамках Фазы 5): всё воспроизведение идёт через
     * embed (WebView), независимо от хоста и от [com.anixkmp.model.EpisodeTarget.iframe] —
     * нативный `PlayerController`/`PlaybackSource.Direct` из `:shared:player` остаются заделом
     * на будущее и здесь не используются.
     */
    suspend fun resolvePlaybackSource(releaseId: Int, sourceId: Int, position: Int, host: VideoHost): PlaybackSource {
        val target = episodeApi.target(releaseId, sourceId, position).episode?.toDomain()
        val url = target?.url?.takeIf { it.isNotBlank() }
            ?: throw AnixError.PlaybackResolve(host)
        return PlaybackSource.Embed(url = url, host = host)
    }

    suspend fun markWatched(releaseId: Int, sourceId: Int, position: Int) {
        episodeApi.markWatched(releaseId, sourceId, position)
    }

    suspend fun markUnwatched(releaseId: Int, sourceId: Int, position: Int) {
        episodeApi.markUnwatched(releaseId, sourceId, position)
    }

    /*
     * Мёртвая заметка (не публичный API): изначально предполагалось ветвление
     * Direct/Embed по хосту — карта ниже перечисляла хосты, которые заведомо требуют
     * embed-страницы. Решение MVP — всё через embed, поэтому карта не нужна как код, но
     * оставлена как справка на случай будущего возврата к нативному воспроизведению:
     *
     * KODIK, SIBNET, RUTUBE, VK_VIDEO, OK_RU, MAIL_RU, MYVI, ALLVIDEO, SOVET_ROMANTICA,
     * STUDIO_MIR, TORLOOK, UNKNOWN -> требуют embed; ANILIBRIA -> предположительно прямой поток.
     */
}
