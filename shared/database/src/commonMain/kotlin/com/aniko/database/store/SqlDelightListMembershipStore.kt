package com.aniko.database.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.aniko.database.AnikoDatabase
import com.aniko.model.ListStatus
import com.aniko.model.ReleaseId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * `listMembership` (P4.T1/T5, трек A) — LWW по независимым `status_updated_at`/
 * `favorite_updated_at`, единый метод и для локальных оптимистичных правок, и для значений,
 * пришедших с сервера (см. KDoc `ListMembership.sq`).
 */
class SqlDelightListMembershipStore(
    private val database: AnikoDatabase,
    private val dispatcher: CoroutineDispatcher,
) : ListMembershipStore {
    override fun observeStatus(releaseId: ReleaseId): Flow<ListStatus?> =
        database.listMembershipQueries
            .observeStatus(releaseId.toLong())
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { ListStatus.fromApiValue(it?.status?.toInt()) }

    override suspend fun setStatus(
        releaseId: ReleaseId,
        status: ListStatus?,
        updatedAt: Instant,
    ) {
        withContext(dispatcher) {
            database.listMembershipQueries.setStatus(
                release_id = releaseId.toLong(),
                status = status?.apiValue?.toLong(),
                status_updated_at = updatedAt.toEpochMilliseconds(),
            )
        }
    }

    override fun observeFavorite(releaseId: ReleaseId): Flow<Boolean> =
        database.listMembershipQueries
            .observeFavorite(releaseId.toLong())
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it ?: false }

    override suspend fun setFavorite(
        releaseId: ReleaseId,
        isFavorite: Boolean,
        updatedAt: Instant,
    ) {
        withContext(dispatcher) {
            database.listMembershipQueries.setFavorite(
                release_id = releaseId.toLong(),
                is_favorite = isFavorite,
                favorite_updated_at = updatedAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun initFromServer(
        releaseId: ReleaseId,
        status: ListStatus?,
        isFavorite: Boolean,
        fetchedAt: Instant,
    ) {
        withContext(dispatcher) {
            database.listMembershipQueries.initFromServer(
                release_id = releaseId.toLong(),
                status = status?.apiValue?.toLong(),
                status_updated_at = fetchedAt.toEpochMilliseconds(),
                is_favorite = isFavorite,
                favorite_updated_at = fetchedAt.toEpochMilliseconds(),
            )
        }
    }
}
