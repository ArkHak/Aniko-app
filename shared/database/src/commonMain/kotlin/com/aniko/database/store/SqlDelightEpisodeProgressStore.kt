package com.aniko.database.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.aniko.database.AnikoDatabase
import com.aniko.model.ReleaseId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * `episodeProgress` (P4.T1/T5, трек A) — LWW по `updated_at`, ключ серии — тройка
 * (release_id, source_id, position), см. KDoc `EpisodeProgress.sq`.
 */
class SqlDelightEpisodeProgressStore(
    private val database: AnikoDatabase,
    private val dispatcher: CoroutineDispatcher,
) : EpisodeProgressStore {
    override fun observeWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
    ): Flow<Boolean> =
        database.episodeProgressQueries
            .observeWatched(releaseId.toLong(), sourceId.toLong(), position.toLong())
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it ?: false }

    override fun observeWatchedPositions(
        releaseId: ReleaseId,
        sourceId: Int,
    ): Flow<Map<Int, Boolean>> =
        database.episodeProgressQueries
            .observeWatchedPositions(releaseId.toLong(), sourceId.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.associate { it.position.toInt() to it.is_watched } }

    override suspend fun setWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
        isWatched: Boolean,
        updatedAt: Instant,
    ) {
        withContext(dispatcher) {
            database.episodeProgressQueries.setWatched(
                release_id = releaseId.toLong(),
                source_id = sourceId.toLong(),
                position = position.toLong(),
                is_watched = isWatched,
                updated_at = updatedAt.toEpochMilliseconds(),
            )
        }
    }
}
