package com.aniko.ui.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals

class AnixWindowSizeTest {
    @Test
    fun fromWidthDp_599_isCompact() {
        assertEquals(AnixWindowSize.Compact, AnixWindowSize.fromWidthDp(599))
    }

    @Test
    fun fromWidthDp_600_isMedium() {
        assertEquals(AnixWindowSize.Medium, AnixWindowSize.fromWidthDp(600))
    }

    @Test
    fun fromWidthDp_839_isMedium() {
        assertEquals(AnixWindowSize.Medium, AnixWindowSize.fromWidthDp(839))
    }

    @Test
    fun fromWidthDp_840_isExpanded() {
        assertEquals(AnixWindowSize.Expanded, AnixWindowSize.fromWidthDp(840))
    }
}
