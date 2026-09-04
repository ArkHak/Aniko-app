package com.aniko.ui.component.chart

import kotlin.test.Test
import kotlin.test.assertEquals

class ChartMathTest {
    // --- donutSweepAngles ---

    @Test
    fun donutSweepAngles_emptyList_isEmpty() {
        assertEquals(emptyList(), donutSweepAngles(emptyList()))
    }

    @Test
    fun donutSweepAngles_allZero_isEmpty() {
        assertEquals(emptyList(), donutSweepAngles(listOf(0f, 0f, 0f)))
    }

    @Test
    fun donutSweepAngles_proportionalSplit() {
        val result = donutSweepAngles(listOf(1f, 1f, 2f))

        assertEquals(3, result.size)
        assertEquals(0f, result[0].start)
        assertEquals(90f, result[0].endInclusive)
        assertEquals(90f, result[1].start)
        assertEquals(180f, result[1].endInclusive)
        assertEquals(180f, result[2].start)
        assertEquals(360f, result[2].endInclusive)
    }

    @Test
    fun donutSweepAngles_singleValue_isFullCircle() {
        val result = donutSweepAngles(listOf(5f))

        assertEquals(1, result.size)
        assertEquals(0f, result[0].start)
        assertEquals(360f, result[0].endInclusive)
    }

    // --- barHeightFractions ---

    @Test
    fun barHeightFractions_emptyList_isEmpty() {
        assertEquals(emptyList(), barHeightFractions(emptyList(), maxValue = null))
    }

    @Test
    fun barHeightFractions_allZeroAndNoExplicitMax_allFractionsAtMinVisibleFloor() {
        // Track A (точное соответствие макету Профиля, 2026-09-04): нулевые столбики больше не
        // полностью невидимы (`0f`) — минимальный видимый "огрызок" 6%, как в референсном
        // макете (`Math.max(6, ...)`), см. KDoc barHeightFractions.
        assertEquals(listOf(0.06f, 0.06f), barHeightFractions(listOf(0f, 0f), maxValue = null))
    }

    @Test
    fun barHeightFractions_noExplicitMax_usesValuesMax() {
        val result = barHeightFractions(listOf(1f, 2f, 4f), maxValue = null)

        assertEquals(listOf(0.25f, 0.5f, 1f), result)
    }

    @Test
    fun barHeightFractions_explicitMaxLowerThanValues_clampsToOne() {
        val result = barHeightFractions(listOf(1f, 5f), maxValue = 2f)

        assertEquals(listOf(0.5f, 1f), result)
    }

    @Test
    fun barHeightFractions_explicitMaxHigherThanValues_scalesDown() {
        val result = barHeightFractions(listOf(1f, 2f), maxValue = 10f)

        assertEquals(listOf(0.1f, 0.2f), result)
    }

    // --- progressFraction ---

    @Test
    fun progressFraction_maxCountZero_isZero() {
        assertEquals(0f, progressFraction(count = 3, maxCount = 0))
    }

    @Test
    fun progressFraction_maxCountNegative_isZero() {
        assertEquals(0f, progressFraction(count = 3, maxCount = -1))
    }

    @Test
    fun progressFraction_normalCase() {
        assertEquals(0.5f, progressFraction(count = 5, maxCount = 10))
    }

    @Test
    fun progressFraction_countGreaterThanMax_clampsToOne() {
        assertEquals(1f, progressFraction(count = 20, maxCount = 10))
    }

    @Test
    fun progressFraction_explicitMaxEqualsCount_isOne() {
        assertEquals(1f, progressFraction(count = 4, maxCount = 4))
    }
}
