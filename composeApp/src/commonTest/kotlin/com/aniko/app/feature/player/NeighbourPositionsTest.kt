package com.aniko.app.feature.player

import com.aniko.model.Episode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Соседние серии для кнопок «пред./след.» и автоперехода ([neighbourPositions]): берутся по списку
 * серий источника, а не как `position ± 1` — нумерация бывает с 0, с 1 и с дырками.
 */
class NeighbourPositionsTest {
    private fun episodes(vararg positions: Int) = positions.map { Episode(position = it, name = "$it серия") }

    @Test
    fun middleEpisodeHasBothNeighbours() {
        assertEquals(1 to 3, neighbourPositions(episodes(1, 2, 3), 2))
    }

    @Test
    fun edgesHaveOneNeighbour() {
        assertEquals(null to 1, neighbourPositions(episodes(0, 1, 2), 0))
        assertEquals(1 to null, neighbourPositions(episodes(0, 1, 2), 2))
    }

    @Test
    fun gapsInNumberingAreSkipped() {
        // position ± 1 вёл бы в несуществующие 5 и 7.
        assertEquals(4 to 8, neighbourPositions(episodes(1, 4, 6, 8), 6))
    }

    @Test
    fun unsortedListIsSortedFirst() {
        assertEquals(1 to 3, neighbourPositions(episodes(3, 1, 2), 2))
    }

    @Test
    fun unknownPositionGivesNull() {
        assertNull(neighbourPositions(episodes(1, 2, 3), 9))
        assertNull(neighbourPositions(emptyList(), 1))
    }

    @Test
    fun singleEpisodeHasNoNeighbours() {
        assertEquals(null to null, neighbourPositions(episodes(1), 1))
    }
}
