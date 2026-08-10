package com.aniko.data.repository

import com.aniko.data.api.EpisodeApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.sync.SyncQueueWorker
import com.aniko.database.store.EpisodeProgressStore
import com.aniko.database.store.SyncQueueStore
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
import com.aniko.model.AnixError
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.player.PlaybackSource
import com.aniko.player.isKodikEmbedUrl
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * Цепочка резолвинга плеера из `docs/api/ENDPOINTS.md`:
 * types → sources → episodes → target.
 *
 * P4.T7 (S3 — интеграция): [markWatched]/[markUnwatched] теперь пишут прогресс оптимистично в
 * [episodeProgressStore] и уходят на сервер через [SyncQueueStore]/[syncQueueWorker], а не бьют
 * в [episodeApi] напрямую — переживают офлайн (P4.T5), как и мутации `LibraryRepository`.
 */
class EpisodeRepository(
    private val episodeApi: EpisodeApi,
    private val episodeProgressStore: EpisodeProgressStore,
    private val syncQueueStore: SyncQueueStore,
    private val syncQueueWorker: SyncQueueWorker,
    private val clock: Clock,
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

    /** Отмечена ли конкретная серия просмотренной — прямой passthrough локальной истины, TTL не нужен. */
    fun observeWatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ): Flow<Boolean> = episodeProgressStore.observeWatched(releaseId, sourceId, position)

    /** Все просмотренные позиции релиза в рамках источника — для массовой отрисовки списка серий. */
    fun observeWatchedPositions(
        releaseId: Int,
        sourceId: Int,
    ): Flow<Set<Int>> = episodeProgressStore.observeWatchedPositions(releaseId, sourceId)

    suspend fun markWatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ) {
        setWatchedAndEnqueue(releaseId, sourceId, position, isWatched = true)
    }

    suspend fun markUnwatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ) {
        setWatchedAndEnqueue(releaseId, sourceId, position, isWatched = false)
    }

    private suspend fun setWatchedAndEnqueue(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        isWatched: Boolean,
    ) {
        val now = clock.now()
        episodeProgressStore.setWatched(releaseId, sourceId, position, isWatched, now)
        syncQueueStore.enqueue(
            SyncOperation(
                id = 0,
                kind = SyncOperationKind.EPISODE_SET_WATCHED,
                entityKey = "episode:$releaseId:$sourceId:$position",
                releaseId = releaseId,
                sourceId = sourceId,
                position = position,
                statusApiValue = null,
                boolArg = isWatched,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                lastError = null,
            ),
        )
        syncQueueWorker.drain()
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
