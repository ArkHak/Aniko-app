package com.anixkmp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListStatusTest {

    @Test
    fun apiValuesMatchAnixartContract() {
        assertEquals(1, ListStatus.WATCHING.apiValue)
        assertEquals(2, ListStatus.PLANNED.apiValue)
        assertEquals(3, ListStatus.COMPLETED.apiValue)
        assertEquals(4, ListStatus.ON_HOLD.apiValue)
        assertEquals(5, ListStatus.DROPPED.apiValue)
    }

    @Test
    fun fromApiValueRoundTrips() {
        ListStatus.entries.forEach { status ->
            assertEquals(status, ListStatus.fromApiValue(status.apiValue))
        }
    }

    @Test
    fun fromApiValueReturnsNullForUnknown() {
        assertNull(ListStatus.fromApiValue(null))
        assertNull(ListStatus.fromApiValue(0))
        assertNull(ListStatus.fromApiValue(6))
    }
}
