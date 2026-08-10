package com.aniko.data.sync

import com.aniko.database.store.EpisodeProgressStore
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * In-memory [EpisodeProgressStore] для тестов трека C.
 *
 * [SyncQueueWorker] сейчас не вызывает ни один метод этого стора (см. `SyncQueueWorker`
 * class-level KDoc, пункт 1 про отсутствие touch-метода в S1) — фейк существует только чтобы
 * собрать конструктор воркера в тестах; хранит состояние по образцу остальных фейков этого
 * пакета на случай, если он понадобится будущим тестам.
 */
class FakeEpisodeProgressStore : EpisodeProgressStore {
    private val watched = mutableMapOf<Triple<ReleaseId, Int, Int>, Boolean>()

    override fun observeWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
    ): Flow<Boolean> = flowOf(watched[Triple(releaseId, sourceId, position)] ?: false)

    override fun observeWatchedPositions(
        releaseId: ReleaseId,
        sourceId: Int,
    ): Flow<Set<Int>> =
        flowOf(
            watched
                .filterKeys { it.first == releaseId && it.second == sourceId }
                .filterValues { it }
                .keys
                .map { it.third }
                .toSet(),
        )

    override suspend fun setWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
        isWatched: Boolean,
        updatedAt: Instant,
    ) {
        watched[Triple(releaseId, sourceId, position)] = isWatched
    }
}
