package com.aniko.data.cache

import com.aniko.database.store.ReleaseCacheStore
import com.aniko.model.Release
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * In-memory [ReleaseCacheStore] для тестов `ReleaseRepository`/`LibraryRepository`/
 * `ScheduleRepository` (P4.T7, S3), по образцу `com.aniko.data.sync.FakeListMembershipStore`.
 *
 * В отличие от тех фейков — [observe] обязан быть по-настоящему реактивным (backed by
 * [MutableStateFlow], не `flowOf(снимок)`): `cacheFirstFlow` вызывает `local.first()` дважды
 * (до и после `refresh()`), и второй вызов должен увидеть то, что записал [upsert] между ними.
 */
class FakeReleaseCacheStore : ReleaseCacheStore {
    private val releases = mutableMapOf<ReleaseId, MutableStateFlow<Release?>>()
    private val fetchedAtByRelease = mutableMapOf<ReleaseId, Instant>()

    private fun flowFor(releaseId: ReleaseId): MutableStateFlow<Release?> = releases.getOrPut(releaseId) { MutableStateFlow(null) }

    override fun observe(releaseId: ReleaseId): Flow<Release?> = flowFor(releaseId).asStateFlow()

    override suspend fun fetchedAt(releaseId: ReleaseId): Instant? = fetchedAtByRelease[releaseId]

    override suspend fun upsert(
        release: Release,
        fetchedAt: Instant,
    ) {
        flowFor(release.id).value = release
        fetchedAtByRelease[release.id] = fetchedAt
    }

    override suspend fun delete(releaseId: ReleaseId) {
        flowFor(releaseId).value = null
        fetchedAtByRelease.remove(releaseId)
    }
}
