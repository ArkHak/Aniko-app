package com.aniko.database.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.aniko.database.AnikoDatabase
import com.aniko.model.Paged
import com.aniko.model.ReleaseId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * `cacheMeta` (свежесть + пагинация) + `releaseListEntry` (упорядоченный список id) на один
 * cache_key = одна страница листинга (P4.T1/T4, трек A). См. KDoc `CacheMeta.sq`/`ReleaseList.sq`.
 */
class SqlDelightReleaseListStore(
    private val database: AnikoDatabase,
    private val dispatcher: CoroutineDispatcher,
) : ReleaseListStore {
    override fun observePage(key: String): Flow<Paged<ReleaseId>?> {
        val meta =
            database.cacheMetaQueries
                .selectByKey(key)
                .asFlow()
                .mapToOneOrNull(dispatcher)
        val ids =
            database.releaseListQueries
                .selectByKey(key)
                .asFlow()
                .mapToList(dispatcher)
        return combine(meta, ids) { metaRow, idRows ->
            metaRow?.let {
                Paged(
                    items = idRows.map { id -> id.toInt() },
                    currentPage = it.current_page.toInt(),
                    totalPages = it.total_pages.toInt(),
                    totalCount = it.total_count?.toInt(),
                )
            }
        }
    }

    override suspend fun fetchedAt(key: String): Instant? =
        withContext(dispatcher) {
            database.cacheMetaQueries
                .selectByKey(key)
                .executeAsOneOrNull()
                ?.fetched_at
                ?.let(Instant::fromEpochMilliseconds)
        }

    override suspend fun replacePage(
        key: String,
        page: Paged<ReleaseId>,
        fetchedAt: Instant,
    ) {
        withContext(dispatcher) {
            database.transaction {
                database.releaseListQueries.deleteByKey(key)
                page.items.forEachIndexed { index, releaseId ->
                    database.releaseListQueries.insertEntry(
                        cache_key = key,
                        sort_order = index.toLong(),
                        release_id = releaseId.toLong(),
                    )
                }
                database.cacheMetaQueries.upsert(
                    cache_key = key,
                    fetched_at = fetchedAt.toEpochMilliseconds(),
                    current_page = page.currentPage.toLong(),
                    total_pages = page.totalPages.toLong(),
                    total_count = page.totalCount?.toLong(),
                )
            }
        }
    }

    override suspend fun invalidate(prefix: String) {
        withContext(dispatcher) {
            database.transaction {
                database.cacheMetaQueries.deleteByKeyPrefix(prefix)
                database.releaseListQueries.deleteByKeyPrefix(prefix)
            }
        }
    }
}
