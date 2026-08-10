package com.aniko.database.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.aniko.database.AnikoDatabase
import com.aniko.database.SelectById
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseId
import com.aniko.model.ReleaseStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * `ReleaseQueries` + LEFT JOIN на `listMembership` (P4.T1/T4, трек A) — см. KDoc `Release.sq` про
 * то, почему [upsert] намеренно НЕ пишет в `listMembership` сам (риск затереть pending-мутацию
 * свежим сетевым фетчем).
 */
class SqlDelightReleaseCacheStore(
    private val database: AnikoDatabase,
    private val dispatcher: CoroutineDispatcher,
) : ReleaseCacheStore {
    override fun observe(releaseId: ReleaseId): Flow<Release?> =
        database.releaseQueries
            .selectById(releaseId.toLong())
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }

    override suspend fun fetchedAt(releaseId: ReleaseId): Instant? =
        withContext(dispatcher) {
            database.releaseQueries
                .fetchedAt(releaseId.toLong())
                .executeAsOneOrNull()
                ?.let(Instant::fromEpochMilliseconds)
        }

    override suspend fun upsert(
        release: Release,
        fetchedAt: Instant,
    ) {
        withContext(dispatcher) {
            database.releaseQueries.upsert(
                id = release.id.toLong(),
                title = release.title,
                original_title = release.originalTitle,
                poster_url = release.posterUrl,
                description = release.description,
                year = release.year?.toLong(),
                episodes_total = release.episodesTotal?.toLong(),
                episodes_released = release.episodesReleased?.toLong(),
                grade = release.grade,
                status = release.status.name,
                genres = release.genres.joinToString(separator = ","),
                fetched_at = fetchedAt.toEpochMilliseconds(),
                updated_at = fetchedAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun delete(releaseId: ReleaseId) {
        withContext(dispatcher) {
            database.releaseQueries.delete(releaseId.toLong())
        }
    }
}

private fun SelectById.toDomain(): Release =
    Release(
        id = id.toInt(),
        title = title,
        originalTitle = original_title,
        posterUrl = poster_url,
        description = description,
        year = year?.toInt(),
        episodesTotal = episodes_total?.toInt(),
        episodesReleased = episodes_released?.toInt(),
        grade = grade,
        status = runCatching { ReleaseStatus.valueOf(status) }.getOrDefault(ReleaseStatus.UNKNOWN),
        genres = genres.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        myListStatus = ListStatus.fromApiValue(my_list_status?.toInt()),
        isFavorite = my_is_favorite ?: false,
    )
