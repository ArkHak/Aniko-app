package com.aniko.data.cache

import com.aniko.database.store.ReleaseListStore
import com.aniko.model.Paged
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * In-memory [ReleaseListStore] для тестов `ReleaseRepository`/`LibraryRepository`/
 * `ScheduleRepository` (P4.T7, S3) — реактивный по той же причине, что и
 * [FakeReleaseCacheStore] (см. его KDoc).
 */
class FakeReleaseListStore : ReleaseListStore {
    private val pages = mutableMapOf<String, MutableStateFlow<Paged<ReleaseId>?>>()
    private val fetchedAtByKey = mutableMapOf<String, Instant>()

    private fun flowFor(key: String): MutableStateFlow<Paged<ReleaseId>?> = pages.getOrPut(key) { MutableStateFlow(null) }

    override fun observePage(key: String): Flow<Paged<ReleaseId>?> = flowFor(key).asStateFlow()

    override suspend fun fetchedAt(key: String): Instant? = fetchedAtByKey[key]

    override suspend fun replacePage(
        key: String,
        page: Paged<ReleaseId>,
        fetchedAt: Instant,
    ) {
        flowFor(key).value = page
        fetchedAtByKey[key] = fetchedAt
    }

    override suspend fun invalidate(prefix: String) {
        pages.keys.filter { it.startsWith(prefix) }.forEach { key ->
            flowFor(key).value = null
            fetchedAtByKey.remove(key)
        }
    }
}
