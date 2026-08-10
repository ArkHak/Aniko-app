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
    fun observeWatchedPositions_returnsOnlyWatchedPositionsForSource() =
        runTest {
            store.setWatched(1, sourceId = 2, position = 1, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))
            store.setWatched(1, sourceId = 2, position = 2, isWatched = false, updatedAt = Instant.fromEpochMilliseconds(1_000))
            store.setWatched(1, sourceId = 2, position = 3, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))
            // Другой источник той же серии — не должен попасть в выборку.
            store.setWatched(1, sourceId = 9, position = 1, isWatched = true, updatedAt = Instant.fromEpochMilliseconds(1_000))

            assertEquals(setOf(1, 3), store.observeWatchedPositions(1, sourceId = 2).first())
        }
}
