package com.aniko.database.store

import app.cash.sqldelight.db.SqlDriver
import com.aniko.database.AnikoDatabase
import com.aniko.database.createTestDriver
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `syncOperation` (P4.T6) — коалесcинг по `entity_key` (`ON CONFLICT`, см. KDoc `SyncQueue.sq`) и
 * фильтрация/сортировка [SqlDelightSyncQueueStore.dueOperations].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncQueueStoreTest {
    private val driver: SqlDriver = createTestDriver()
    private val database = AnikoDatabase(driver)
    private val store = SqlDelightSyncQueueStore(database, UnconfinedTestDispatcher())

    private fun favoriteOp(
        releaseId: Int,
        boolArg: Boolean,
        createdAt: Long,
    ) = SyncOperation(
        id = 0,
        kind = SyncOperationKind.FAVORITE_SET,
        entityKey = "favorite:$releaseId",
        releaseId = releaseId,
        boolArg = boolArg,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(createdAt),
    )

    @Test
    fun enqueue_sameEntityKeyFourTimes_coalescesIntoOneOperation() =
        runTest {
            repeat(4) { attempt ->
                store.enqueue(favoriteOp(releaseId = 1, boolArg = attempt % 2 == 0, createdAt = 1_000L + attempt))
            }

            assertEquals(1, store.observePending().first().size)
            assertEquals(1L, database.syncQueueQueries.countPending().executeAsOne())
        }

    @Test
    fun enqueue_sameEntityKey_preservesOriginalIdAndCreatedAt() =
        runTest {
            val firstId = store.enqueue(favoriteOp(releaseId = 1, boolArg = true, createdAt = 1_000L))
            val secondId = store.enqueue(favoriteOp(releaseId = 1, boolArg = false, createdAt = 2_000L))

            assertEquals(firstId, secondId)
            val op = store.observePending().first().single()
            assertEquals(firstId, op.id)
            assertEquals(Instant.fromEpochMilliseconds(1_000L), op.createdAt)
        }

    @Test
    fun enqueue_sameEntityKey_updatesToLatestValue() =
        runTest {
            store.enqueue(favoriteOp(releaseId = 1, boolArg = true, createdAt = 1_000L))
            store.enqueue(favoriteOp(releaseId = 1, boolArg = false, createdAt = 2_000L))

            val op = store.observePending().first().single()
            assertEquals(false, op.boolArg)
            assertEquals(Instant.fromEpochMilliseconds(2_000L), op.updatedAt)
        }

    @Test
    fun enqueue_sameEntityKey_resetsBackoffAfterFailedAttempt() =
        runTest {
            val id = store.enqueue(favoriteOp(releaseId = 1, boolArg = true, createdAt = 1_000L))
            store.recordAttempt(id, Instant.fromEpochMilliseconds(50_000L), "boom")

            store.enqueue(favoriteOp(releaseId = 1, boolArg = false, createdAt = 2_000L))

            val op = store.observePending().first().single()
            assertEquals(0, op.attemptCount)
            assertEquals(null, op.nextAttemptAt)
            assertEquals(null, op.lastError)
        }

    @Test
    fun dueOperations_excludesOperationsWithFutureBackoff() =
        runTest {
            val readyId = store.enqueue(favoriteOp(releaseId = 1, boolArg = true, createdAt = 1_000L))
            val backedOffId = store.enqueue(favoriteOp(releaseId = 2, boolArg = true, createdAt = 1_100L))
            store.recordAttempt(backedOffId, Instant.fromEpochMilliseconds(100_000L), "boom")

            val due = store.dueOperations(Instant.fromEpochMilliseconds(10_000L))

            assertEquals(listOf(readyId), due.map { it.id })
        }

    @Test
    fun dueOperations_sortedByIdAscending() =
        runTest {
            val firstId = store.enqueue(favoriteOp(releaseId = 1, boolArg = true, createdAt = 1_000L))
            val secondId = store.enqueue(favoriteOp(releaseId = 2, boolArg = true, createdAt = 1_100L))
            store.recordAttempt(firstId, Instant.fromEpochMilliseconds(2_000L), "boom")

            val due = store.dueOperations(Instant.fromEpochMilliseconds(5_000L))

            assertEquals(listOf(firstId, secondId), due.map { it.id })
            assertTrue(firstId < secondId)
        }

    @Test
    fun remove_takesOperationOutOfQueue() =
        runTest {
            val id = store.enqueue(favoriteOp(releaseId = 1, boolArg = true, createdAt = 1_000L))

            store.remove(id)

            assertEquals(emptyList(), store.observePending().first())
            assertEquals(emptyList(), store.dueOperations(Instant.fromEpochMilliseconds(1_000L)))
        }
}
