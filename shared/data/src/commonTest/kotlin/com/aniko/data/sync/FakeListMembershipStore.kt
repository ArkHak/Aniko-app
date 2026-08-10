package com.aniko.data.sync

import com.aniko.database.store.ListMembershipStore
import com.aniko.model.ListStatus
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * In-memory [ListMembershipStore] для тестов трека C — минимальный, только то, что реально
 * нужно [SyncQueueWorker]: [observeStatus] (для разрешения статуса `LIST_REMOVE`, см.
 * `SyncQueueWorker.resolveRemovalStatus`) плюс полный набор методов интерфейса, чтобы фейк
 * компилировался как реализация контракта.
 */
class FakeListMembershipStore : ListMembershipStore {
    private val statuses = mutableMapOf<ReleaseId, ListStatus?>()
    private val favorites = mutableMapOf<ReleaseId, Boolean>()

    /** Тестовый сеттер — имитирует то, что локальный стор уже "знает" статус до вызова [drain][SyncQueueWorker.drain]. */
    fun seedStatus(
        releaseId: ReleaseId,
        status: ListStatus?,
    ) {
        statuses[releaseId] = status
    }

    override fun observeStatus(releaseId: ReleaseId): Flow<ListStatus?> = flowOf(statuses[releaseId])

    override suspend fun setStatus(
        releaseId: ReleaseId,
        status: ListStatus?,
        updatedAt: Instant,
    ) {
        statuses[releaseId] = status
    }

    override fun observeFavorite(releaseId: ReleaseId): Flow<Boolean> = flowOf(favorites[releaseId] ?: false)

    override suspend fun setFavorite(
        releaseId: ReleaseId,
        isFavorite: Boolean,
        updatedAt: Instant,
    ) {
        favorites[releaseId] = isFavorite
    }

    /** Как реальный SQL `INSERT OR IGNORE` — не перезаписывает уже известное значение. */
    override suspend fun initFromServer(
        releaseId: ReleaseId,
        status: ListStatus?,
        isFavorite: Boolean,
        fetchedAt: Instant,
    ) {
        if (releaseId !in statuses) statuses[releaseId] = status
        if (releaseId !in favorites) favorites[releaseId] = isFavorite
    }
}
