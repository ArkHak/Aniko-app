package com.aniko.database.store

import app.cash.sqldelight.db.SqlDriver
import com.aniko.database.AnikoDatabase
import com.aniko.database.createTestDriver
import com.aniko.model.ListStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * LWW-семантика `listMembership` (P4.T6) — статус и избранное имеют независимые таймстампы
 * (`status_updated_at`/`favorite_updated_at`, см. KDoc `ListMembership.sq`), проверяем это отдельно
 * от самого LWW-сравнения.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListMembershipLwwTest {
    private val driver: SqlDriver = createTestDriver()
    private val database = AnikoDatabase(driver)
    private val store = SqlDelightListMembershipStore(database, UnconfinedTestDispatcher())

    private val releaseId = 1

    @Test
    fun setStatus_olderWriteAfterNewer_doesNotOverride() =
        runTest {
            store.setStatus(releaseId, ListStatus.WATCHING, Instant.fromEpochMilliseconds(1_000))
            store.setStatus(releaseId, ListStatus.PLANNED, Instant.fromEpochMilliseconds(500))

            assertEquals(ListStatus.WATCHING, store.observeStatus(releaseId).first())
        }

    @Test
    fun setStatus_newerWriteAfterOlder_overrides() =
        runTest {
            store.setStatus(releaseId, ListStatus.WATCHING, Instant.fromEpochMilliseconds(1_000))
            store.setStatus(releaseId, ListStatus.PLANNED, Instant.fromEpochMilliseconds(2_000))

            assertEquals(ListStatus.PLANNED, store.observeStatus(releaseId).first())
        }

    @Test
    fun setStatus_winningWrite_advancesStoredTimestamp() =
        runTest {
            store.setStatus(releaseId, ListStatus.WATCHING, Instant.fromEpochMilliseconds(1_000))
            // "Проигравшая" (более старая) правка не должна сдвинуть таймстамп вперёд или назад.
            store.setStatus(releaseId, ListStatus.PLANNED, Instant.fromEpochMilliseconds(500))

            val row = database.listMembershipQueries.selectByReleaseId(releaseId.toLong()).executeAsOne()

            assertEquals(1_000L, row.status_updated_at)
        }

    @Test
    fun setStatus_removingFromList_isRespectedByLww() =
        runTest {
            store.setStatus(releaseId, ListStatus.WATCHING, Instant.fromEpochMilliseconds(1_000))
            store.setStatus(releaseId, null, Instant.fromEpochMilliseconds(2_000))

            assertNull(store.observeStatus(releaseId).first())
        }

    @Test
    fun setFavorite_olderWriteAfterNewer_doesNotOverride() =
        runTest {
            store.setFavorite(releaseId, true, Instant.fromEpochMilliseconds(1_000))
            store.setFavorite(releaseId, false, Instant.fromEpochMilliseconds(500))

            assertTrue(store.observeFavorite(releaseId).first())
        }

    @Test
    fun setFavorite_newerWriteAfterOlder_overrides() =
        runTest {
            store.setFavorite(releaseId, true, Instant.fromEpochMilliseconds(1_000))
            store.setFavorite(releaseId, false, Instant.fromEpochMilliseconds(2_000))

            assertFalse(store.observeFavorite(releaseId).first())
        }

    @Test
    fun setStatusAndSetFavorite_haveIndependentLwwTimestamps() =
        runTest {
            // Избранное выставлено давно (t=1) — более свежая правка статуса (t=2000) не должна на
            // него повлиять, а более старая "серверная" попытка сбросить статус (t=500) должна
            // проиграть, не трогая при этом избранное вовсе.
            store.setFavorite(releaseId, true, Instant.fromEpochMilliseconds(1))
            store.setStatus(releaseId, ListStatus.WATCHING, Instant.fromEpochMilliseconds(1_000))
            store.setStatus(releaseId, ListStatus.PLANNED, Instant.fromEpochMilliseconds(2_000))
            store.setStatus(releaseId, ListStatus.DROPPED, Instant.fromEpochMilliseconds(500))

            assertEquals(ListStatus.PLANNED, store.observeStatus(releaseId).first())
            assertTrue(store.observeFavorite(releaseId).first())
        }

    @Test
    fun observeStatus_noRow_returnsNull() =
        runTest {
            assertNull(store.observeStatus(999).first())
        }

    @Test
    fun observeFavorite_noRow_returnsFalse() =
        runTest {
            assertFalse(store.observeFavorite(999).first())
        }

    @Test
    fun initFromServer_noExistingRow_populatesFromServer() =
        runTest {
            store.initFromServer(releaseId, ListStatus.WATCHING, isFavorite = true, fetchedAt = Instant.fromEpochMilliseconds(1_000))

            assertEquals(ListStatus.WATCHING, store.observeStatus(releaseId).first())
            assertTrue(store.observeFavorite(releaseId).first())
        }

    @Test
    fun initFromServer_existingLocalMutation_doesNotOverride() =
        runTest {
            // Локальная оптимистичная правка ещё не отправлена на сервер (свежее время, t=5000) —
            // последующий фоновый фетч релиза с УСТАРЕВШИМ для сервера значением (t=1000, сервер
            // ещё не знает про правку) не должен её затереть, см. KDoc initFromServer/reviewer-finding.
            store.setStatus(releaseId, ListStatus.WATCHING, Instant.fromEpochMilliseconds(5_000))

            store.initFromServer(releaseId, ListStatus.PLANNED, isFavorite = false, fetchedAt = Instant.fromEpochMilliseconds(1_000))

            assertEquals(ListStatus.WATCHING, store.observeStatus(releaseId).first())
        }

    @Test
    fun initFromServer_calledTwice_secondCallIsNoOp() =
        runTest {
            store.initFromServer(releaseId, ListStatus.WATCHING, isFavorite = true, fetchedAt = Instant.fromEpochMilliseconds(1_000))
            store.initFromServer(releaseId, ListStatus.PLANNED, isFavorite = false, fetchedAt = Instant.fromEpochMilliseconds(2_000))

            assertEquals(ListStatus.WATCHING, store.observeStatus(releaseId).first())
            assertTrue(store.observeFavorite(releaseId).first())
        }
}
