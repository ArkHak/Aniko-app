package com.aniko.database.store

import app.cash.sqldelight.db.SqlDriver
import com.aniko.database.AnikoDatabase
import com.aniko.database.createTestDriver
import com.aniko.model.Paged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * `ReleaseListStore` — `cacheMeta` (свежесть/пагинация) + `releaseListEntry` (id) на один
 * cache_key = одна страница листинга (P4.T3/T4). Ключевая проверка: пустая (но загруженная)
 * страница отличима от ещё не загруженной — по наличию/отсутствию строки в `cacheMeta`, не по
 * пустоте списка id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheTtlIntegrationTest {
    private val driver: SqlDriver = createTestDriver()
    private val database = AnikoDatabase(driver)
    private val store = SqlDelightReleaseListStore(database, UnconfinedTestDispatcher())

    @Test
    fun observePage_neverFetched_returnsNull() =
        runTest {
            assertNull(store.observePage("watching:0").first())
        }

    @Test
    fun fetchedAt_neverFetched_returnsNull() =
        runTest {
            assertNull(store.fetchedAt("watching:0"))
        }

    @Test
    fun replacePage_thenObserve_returnsWrittenPage() =
        runTest {
            val page = Paged(items = listOf(10, 20, 30), currentPage = 0, totalPages = 3, totalCount = 30)

            store.replacePage("watching:0", page, Instant.fromEpochMilliseconds(1_000))

            assertEquals(page, store.observePage("watching:0").first())
            assertEquals(Instant.fromEpochMilliseconds(1_000), store.fetchedAt("watching:0"))
        }

    @Test
    fun replacePage_withEmptyItems_isDistinctFromNeverFetched() =
        runTest {
            val emptyPage = Paged<Int>(items = emptyList(), currentPage = 0, totalPages = 0, totalCount = 0)

            store.replacePage("mylist:5:0", emptyPage, Instant.fromEpochMilliseconds(1_000))

            // Пустой, но загруженный листинг — валидный Paged с пустым items, а НЕ null (см. тест
            // observePage_neverFetched_returnsNull выше — вот та ситуация действительно даёт null).
            assertEquals(emptyPage, store.observePage("mylist:5:0").first())
        }

    @Test
    fun replacePage_calledTwice_replacesOldEntriesEntirely() =
        runTest {
            store.replacePage("watching:0", Paged(listOf(1, 2, 3), 0, 1, 3), Instant.fromEpochMilliseconds(1_000))
            store.replacePage("watching:0", Paged(listOf(4, 5), 0, 1, 2), Instant.fromEpochMilliseconds(2_000))

            assertEquals(listOf(4, 5), store.observePage("watching:0").first()?.items)
        }

    @Test
    fun invalidate_removesOnlyMatchingPrefix() =
        runTest {
            store.replacePage("watching:0", Paged(listOf(1), 0, 1, 1), Instant.fromEpochMilliseconds(1_000))
            store.replacePage("watching:1", Paged(listOf(2), 1, 2, 2), Instant.fromEpochMilliseconds(1_000))
            store.replacePage("favorites:0", Paged(listOf(3), 0, 1, 1), Instant.fromEpochMilliseconds(1_000))

            store.invalidate("watching:")

            assertNull(store.observePage("watching:0").first())
            assertNull(store.observePage("watching:1").first())
            assertEquals(listOf(3), store.observePage("favorites:0").first()?.items)
        }
}
