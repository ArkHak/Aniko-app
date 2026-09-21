package com.aniko.data.sync

import com.aniko.database.store.ListMembershipStore
import com.aniko.model.ListMembership
import com.aniko.model.ListStatus
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * In-memory [ListMembershipStore] для тестов трека C и реактивных вкладок «Мои списки».
 *
 * Реактивен (backed by [MutableStateFlow], а не `flowOf(снимок)`) по той же причине, что и
 * `FakeReleaseCacheStore`: подписчики ([observeAll] — источник правды экрана списков, [observeStatus]
 * — `SyncQueueWorker.resolveRemovalStatus`) обязаны увидеть запись, сделанную ПОСЛЕ подписки,
 * иначе тест «переложил релиз → он переехал между вкладками» проверял бы не то поведение.
 */
class FakeListMembershipStore : ListMembershipStore {
    private val rows = MutableStateFlow<Map<ReleaseId, ListMembership>>(emptyMap())

    /** Тестовый сеттер — имитирует то, что локальный стор уже "знает" статус до вызова [drain][SyncQueueWorker.drain]. */
    fun seedStatus(
        releaseId: ReleaseId,
        status: ListStatus?,
    ) {
        update(releaseId) { it.copy(status = status) }
    }

    override fun observeStatus(releaseId: ReleaseId): Flow<ListStatus?> = rows.map { it[releaseId]?.status }

    override fun observeAll(): Flow<Map<ReleaseId, ListMembership>> = rows.asStateFlow()

    override suspend fun setStatus(
        releaseId: ReleaseId,
        status: ListStatus?,
        updatedAt: Instant,
    ) {
        update(releaseId) { it.copy(status = status) }
    }

    override fun observeFavorite(releaseId: ReleaseId): Flow<Boolean> = rows.map { it[releaseId]?.isFavorite ?: false }

    override suspend fun setFavorite(
        releaseId: ReleaseId,
        isFavorite: Boolean,
        updatedAt: Instant,
    ) {
        update(releaseId) { it.copy(isFavorite = isFavorite) }
    }

    /** Как реальный SQL `INSERT OR IGNORE` — не перезаписывает уже существующую строку. */
    override suspend fun initFromServer(
        releaseId: ReleaseId,
        status: ListStatus?,
        isFavorite: Boolean,
        fetchedAt: Instant,
    ) {
        if (releaseId in rows.value) return
        rows.value = rows.value + (releaseId to ListMembership(status, isFavorite))
    }

    private fun update(
        releaseId: ReleaseId,
        block: (ListMembership) -> ListMembership,
    ) {
        rows.value = rows.value + (releaseId to block(rows.value[releaseId] ?: ListMembership()))
    }
}
