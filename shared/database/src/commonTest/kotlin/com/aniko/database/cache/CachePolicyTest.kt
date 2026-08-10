package com.aniko.database.cache

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class CachePolicyTest {
    private val fetchedAt = Instant.fromEpochMilliseconds(0)
    private val policy = CachePolicy(1.hours)

    @Test
    fun isFresh_justBeforeTtlBoundary_returnsTrue() {
        val now = fetchedAt + 1.hours - 1.milliseconds

        assertTrue(policy.isFresh(fetchedAt = fetchedAt, now = now))
    }

    @Test
    fun isFresh_exactlyAtTtlBoundary_returnsFalse() {
        val now = fetchedAt + 1.hours

        assertFalse(policy.isFresh(fetchedAt = fetchedAt, now = now))
    }

    @Test
    fun isFresh_pastTtlBoundary_returnsFalse() {
        val now = fetchedAt + 1.hours + 1.milliseconds

        assertFalse(policy.isFresh(fetchedAt = fetchedAt, now = now))
    }

    @Test
    fun isFresh_fetchedAtInTheFuture_returnsTrue() {
        val now = fetchedAt - 1.hours

        assertTrue(policy.isFresh(fetchedAt = fetchedAt, now = now))
    }

    @Test
    fun isFresh_neverPolicy_alwaysReturnsTrue() {
        val never = CachePolicy(Duration.INFINITE)
        val now = fetchedAt + 10_000.hours

        assertTrue(never.isFresh(fetchedAt = fetchedAt, now = now))
    }
}
