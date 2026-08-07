package com.aniko.data.repository

import com.aniko.data.api.EpisodeApi
import com.aniko.data.mapper.toDomain
import com.aniko.model.AnixError
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.player.PlaybackSource
import com.aniko.player.isKodikEmbedUrl

/**
 * Цепочка резолвинга плеера из `docs/api/ENDPOINTS.md`:
 * types → sources → episodes → target.
 */
class EpisodeRepository(
    private val episodeApi: EpisodeApi,
) {
    suspend fun voiceTypes(releaseId: Int): List<VoiceType> = episodeApi.types(releaseId).types.map { it.toDomain() }

    suspend fun sources(
        releaseId: Int,
        typeId: Int,
    ): List<EpisodeSource> = episodeApi.sources(releaseId, typeId).sources.map { it.toDomain() }

    suspend fun episodes(
        releaseId: Int,
        typeId: Int,
        sourceId: Int,
    ): List<Episode> = episodeApi.episodes(releaseId, typeId, sourceId).episodes.map { it.toDomain() }

    /**
     * Резолвит серию в проигрываемый источник для WebView.
     *
     * `host` приходит от вызывающей стороны (экран плеера получает его из навигации,
     * см. `AnixDestination.Player.hostKey`) — репозиторий больше не хранит его сам между
     * вызовами `sources()`/`resolvePlaybackSource()`, это раньше было гонкой состояния между
     * параллельными экранами (см. код-ревью Фазы 5).
     *
     * Архитектурное решение (не пересматривать в рамках Фазы 5): всё воспроизведение идёт через
     * embed (WebView), независимо от хоста и от [com.aniko.model.EpisodeTarget.iframe] —
     * нативный `PlayerController`/`PlaybackSource.Direct` из `:shared:player` остаются заделом
     * на будущее и здесь не используются.
     */
    suspend fun resolvePlaybackSource(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        host: VideoHost,
    ): PlaybackSource {
        val target = episodeApi.target(releaseId, sourceId, position).episode?.toDomain()
        val url =
            target?.url?.takeIf { it.isNotBlank() }
                ?: throw AnixError.PlaybackResolve(host)
        return if (isKodikEmbedUrl(url)) {
            // Query-параметры `?d=/&s=/&ip=` из ответа API рассчитаны на referer из исходного
            // запроса и с нашим WebView не совпадают — Kodik отдаёт `500 "Error code: ds"`
            // (проверено вживую). Если вместо этого загрузить страницу без query, но с
            // `Referer: https://anixmirai.com/`, Kodik сам генерирует корректные подписи
            // (d_sign/pd_sign/ref_sign) на основе заголовка и отдаёт настоящую страницу плеера —
            // 200, а не 500 (проверено вживую через curl). anixmirai.com — авторизованный домен
            // партнёра на стороне Kodik (тот же, что видно в оригинальном приложении).
            PlaybackSource.Embed(url = url.substringBefore('?'), host = host, referer = "https://anixmirai.com/")
        } else {
            PlaybackSource.Embed(url = url, host = host, referer = url)
        }
    }

    suspend fun markWatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ) {
        episodeApi.markWatched(releaseId, sourceId, position)
    }

    suspend fun markUnwatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ) {
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
