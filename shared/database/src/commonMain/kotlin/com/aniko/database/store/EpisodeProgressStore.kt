package com.aniko.database.store

import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Локальный прогресс просмотра серий (`EpisodeRepository.markWatched`/`markUnwatched`), P4.T1/P4.T5.
 *
 * Ключ серии — тройка (releaseId, sourceId, position), как в самом API
 * (`episode/target/{releaseId}/{sourceId}/{position}`).
 */
interface EpisodeProgressStore {
    /** Отмечена ли конкретная серия просмотренной. */
    fun observeWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
    ): Flow<Boolean>

    /** Все просмотренные позиции релиза в рамках источника — для массовой отрисовки списка серий. */
    fun observeWatchedPositions(
        releaseId: ReleaseId,
        sourceId: Int,
    ): Flow<Set<Int>>

    suspend fun setWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
        isWatched: Boolean,
        updatedAt: Instant,
    )
}
