package com.aniko.database.store

import app.cash.sqldelight.db.SqlDriver
import com.aniko.database.AnikoDatabase
import com.aniko.database.createTestDriver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

/** `episodeProgress` (P4.T5/T6) — базовое покрытие: LWW у `EpisodeProgressStore` идентичен `ListMembershipStore`. */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeProgressStoreTest {
    private val driver: SqlDriver = createTestDriver()
    private val database = AnikoDatabase(driver)
    private val store = SqlDelightEpisodeProgressStore(database, UnconfinedTestDispatcher())

    @Test
    fun observeWatched_noRow_returnsFalse() =
        runTest {
            assertFalse(store.observeWatched(releaseId = 1, sourceId = 1, position = 1).first())
        }

    @Test
    fun setWatched_thenObserve_returnsTrue() =
        runTest {
            store.setWatched(1, sourceId = 2, position = 3, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))

            assertEquals(true, store.observeWatched(1, sourceId = 2, position = 3).first())
        }

    @Test
    fun setWatched_olderWriteAfterNewer_doesNotOverride() =
        runTest {
            store.setWatched(1, sourceId = 2, position = 3, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))
            store.setWatched(1, sourceId = 2, position = 3, isWatched = false, updatedAt = Instant.fromEpochMilliseconds(500))

            assertEquals(true, store.observeWatched(1, sourceId = 2, position = 3).first())
        }

    @Test
    fun observeWatchedPositions_returnsFullStateIncludingUnwatchedForSource() =
        runTest {
            store.setWatched(1, sourceId = 2, position = 1, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))
            store.setWatched(1, sourceId = 2, position = 2, isWatched = false, updatedAt = Instant.fromEpochMilliseconds(1_000))
            store.setWatched(1, sourceId = 2, position = 3, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))
            // Другой источник той же серии — не должен попасть в выборку.
            store.setWatched(1, sourceId = 9, position = 1, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))

            // Позиция 2 — явный локальный false — должна остаться в карте, а не выпасть из выборки
            // (баг, который это исправление устраняет: явный unwatch неотличим от "не трогали").
            assertEquals(mapOf(1 to true, 2 to false, 3 to true), store.observeWatchedPositions(1, sourceId = 2).first())
        }
}
