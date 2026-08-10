package com.aniko.database.store

import app.cash.sqldelight.db.SqlDriver
import com.aniko.database.AnikoDatabase
import com.aniko.database.createTestDriver
import com.aniko.model.Release
import com.aniko.model.ReleaseStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * `ReleaseCacheStore.upsert` — единый метод и для полной карточки (`release/{id}`), и для
 * "деградированных" данных листинга (`search/releases/{page}` не даёт `status`, листинги вообще не
 * дают `description`) — см. KDoc `Release.sq`. Проверяем, что деградированный follow-up не портит
 * уже сохранённую полную карточку.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseCacheStoreTest {
    private val driver: SqlDriver = createTestDriver()
    private val database = AnikoDatabase(driver)
    private val store = SqlDelightReleaseCacheStore(database, UnconfinedTestDispatcher())

    private val fullRelease =
        Release(
            id = 1,
            title = "Full Title",
            description = "Полное описание",
            status = ReleaseStatus.ONGOING,
            genres = listOf("Драма", "Комедия"),
        )

    @Test
    fun upsert_degradedFollowUp_doesNotClearDescription() =
        runTest {
            store.upsert(fullRelease, Instant.fromEpochMilliseconds(1_000))
            store.upsert(fullRelease.copy(title = "Updated From Listing", description = null), Instant.fromEpochMilliseconds(2_000))

            val stored = store.observe(1).first()

            assertEquals("Полное описание", stored?.description)
            assertEquals("Updated From Listing", stored?.title)
        }

    @Test
    fun upsert_degradedFollowUp_doesNotResetStatusToUnknown() =
        runTest {
            store.upsert(fullRelease, Instant.fromEpochMilliseconds(1_000))
            store.upsert(fullRelease.copy(status = ReleaseStatus.UNKNOWN), Instant.fromEpochMilliseconds(2_000))

            assertEquals(ReleaseStatus.ONGOING, store.observe(1).first()?.status)
        }

    @Test
    fun upsert_degradedFollowUp_doesNotClearGenres() =
        runTest {
            store.upsert(fullRelease, Instant.fromEpochMilliseconds(1_000))
            store.upsert(fullRelease.copy(genres = emptyList()), Instant.fromEpochMilliseconds(2_000))

            assertEquals(listOf("Драма", "Комедия"), store.observe(1).first()?.genres)
        }

    @Test
    fun upsert_fullReleaseAfterDegraded_restoresFullFields() =
        runTest {
            val degraded = fullRelease.copy(description = null, status = ReleaseStatus.UNKNOWN, genres = emptyList())
            store.upsert(degraded, Instant.fromEpochMilliseconds(1_000))
            store.upsert(fullRelease, Instant.fromEpochMilliseconds(2_000))

            val stored = store.observe(1).first()

            assertEquals("Полное описание", stored?.description)
            assertEquals(ReleaseStatus.ONGOING, stored?.status)
            assertEquals(listOf("Драма", "Комедия"), stored?.genres)
        }

    @Test
    fun observe_neverUpserted_returnsNull() =
        runTest {
            assertNull(store.observe(999).first())
        }

    @Test
    fun fetchedAt_neverUpserted_returnsNull() =
        runTest {
            assertNull(store.fetchedAt(999))
        }

    @Test
    fun fetchedAt_afterUpsert_returnsWrittenTimestamp() =
        runTest {
            store.upsert(fullRelease, Instant.fromEpochMilliseconds(1_234))

            assertEquals(Instant.fromEpochMilliseconds(1_234), store.fetchedAt(1))
        }

    @Test
    fun delete_removesRelease() =
        runTest {
            store.upsert(fullRelease, Instant.fromEpochMilliseconds(1_000))
            store.delete(1)

            assertNull(store.observe(1).first())
        }
}
