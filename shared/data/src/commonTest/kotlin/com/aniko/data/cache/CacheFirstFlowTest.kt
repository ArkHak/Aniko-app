package com.aniko.data.cache

import com.aniko.database.cache.CachePolicy
import com.aniko.model.AnixError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Тесты на [cacheFirstFlow] (P4.T4, track B).
 *
 * `local` — [MutableStateFlow], имитирует реактивный поток из стора: `refresh()` в фейках просто
 * пишет новое значение в этот `StateFlow`, как это делал бы настоящий стор через SQLDelight.
 */
class CacheFirstFlowTest {
    private val policy = CachePolicy(1.hours)
    private val fetchedAt = Instant.fromEpochMilliseconds(0)

    @Test
    fun freshCache_emitsOnlyCache_andDoesNotCallRefresh() =
        runTest {
            val local = MutableStateFlow<String?>("cached-value")
            val clock = FakeClock(now = fetchedAt + 30.minutes)
            var refreshCalls = 0

            val result =
                cacheFirstFlow(
                    local = local,
                    stampAt = { fetchedAt },
                    policy = policy,
                    refresh = { refreshCalls++ },
                    clock = clock,
                ).toList()

            assertEquals(1, result.size)
            val emission = result.single()
            assertEquals("cached-value", emission.value)
            assertEquals(Cached.Origin.CACHE, emission.origin)
            assertFalse(emission.isStale)
            assertNull(emission.refreshError)
            assertEquals(0, refreshCalls)
        }

    @Test
    fun staleCache_emitsCacheThenNetwork() =
        runTest {
            val local = MutableStateFlow<String?>("stale-value")
            val clock = FakeClock(now = fetchedAt + 2.hours)
            var refreshCalls = 0

            val result =
                cacheFirstFlow(
                    local = local,
                    stampAt = { fetchedAt },
                    policy = policy,
                    refresh = {
                        refreshCalls++
                        local.value = "fresh-value"
                    },
                    clock = clock,
                ).take(2).toList()

            assertEquals(1, refreshCalls)
            assertEquals(2, result.size)

            val first = result[0]
            assertEquals("stale-value", first.value)
            assertEquals(Cached.Origin.CACHE, first.origin)
            assertTrue(first.isStale)
            assertNull(first.refreshError)

            val second = result[1]
            assertEquals("fresh-value", second.value)
            assertEquals(Cached.Origin.NETWORK, second.origin)
            assertFalse(second.isStale)
            assertNull(second.refreshError)
        }

    @Test
    fun noCache_callsRefreshImmediately_andEmitsNetworkOnce() =
        runTest {
            val local = MutableStateFlow<String?>(null)
            val clock = FakeClock(now = fetchedAt)
            var refreshCalls = 0

            val result =
                cacheFirstFlow(
                    local = local,
                    stampAt = { null },
                    policy = policy,
                    refresh = {
                        refreshCalls++
                        local.value = "network-value"
                    },
                    clock = clock,
                ).toList()

            assertEquals(1, refreshCalls)
            assertEquals(1, result.size)
            val emission = result.single()
            assertEquals("network-value", emission.value)
            assertEquals(Cached.Origin.NETWORK, emission.origin)
            assertFalse(emission.isStale)
            assertNull(emission.refreshError)
        }

    @Test
    fun staleCache_refreshFails_emitsCacheTwice_secondWithRefreshError_doesNotThrow() =
        runTest {
            val local = MutableStateFlow<String?>("stale-value")
            val clock = FakeClock(now = fetchedAt + 2.hours)
            val networkError = AnixError.Network()

            val result =
                cacheFirstFlow(
                    local = local,
                    stampAt = { fetchedAt },
                    policy = policy,
                    refresh = { throw networkError },
                    clock = clock,
                ).take(2).toList()

            assertEquals(2, result.size)

            val first = result[0]
            assertEquals("stale-value", first.value)
            assertEquals(Cached.Origin.CACHE, first.origin)
            assertTrue(first.isStale)
            assertNull(first.refreshError)

            val second = result[1]
            assertEquals("stale-value", second.value)
            assertEquals(Cached.Origin.CACHE, second.origin)
            assertTrue(second.isStale)
            assertEquals(networkError, second.refreshError)
        }

    @Test
    fun noCache_refreshFails_flowThrowsThatError() =
        runTest {
            val local = MutableStateFlow<String?>(null)
            val clock = FakeClock(now = fetchedAt)
            val networkError = AnixError.Network()

            val thrown =
                assertFailsWith<AnixError.Network> {
                    cacheFirstFlow(
                        local = local,
                        stampAt = { null },
                        policy = policy,
                        refresh = { throw networkError },
                        clock = clock,
                    ).toList()
                }

            assertEquals(networkError, thrown)
        }
}
