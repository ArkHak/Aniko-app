package com.aniko.app.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Знак и кламп вертикального жеста уровней (P16.T9).
 *
 * Тест на чистую функцию, а не на Compose-жест: у `detectVerticalDragGestures` нет способа
 * «прокрутить» жест без устройства, а вся арифметика, где реально возможна ошибка — перевёрнутый
 * знак (в Compose `dragAmount` растёт ВНИЗ) и выход за `0..1`, — живёт здесь.
 */
class PlayerLevelGestureTest {
    @Test
    fun swipeUpRaisesLevel() {
        // Отрицательный dragAmount = палец вверх: уровень обязан вырасти (громче/ярче).
        val result = playerLevelAfterDrag(current = 0.4f, dragAmountPx = -100f, heightPx = 1000f)

        assertTrue(result > 0.4f, "свайп вверх обязан увеличивать уровень, получено $result")
    }

    @Test
    fun swipeDownLowersLevel() {
        val result = playerLevelAfterDrag(current = 0.6f, dragAmountPx = 100f, heightPx = 1000f)

        assertTrue(result < 0.6f, "свайп вниз обязан уменьшать уровень, получено $result")
    }

    @Test
    fun levelIsClampedToUnitRange() {
        assertEquals(1f, playerLevelAfterDrag(current = 0.9f, dragAmountPx = -10_000f, heightPx = 1000f))
        assertEquals(0f, playerLevelAfterDrag(current = 0.1f, dragAmountPx = 10_000f, heightPx = 1000f))
    }

    @Test
    fun zeroHeightKeepsLevelInsteadOfDividingByZero() {
        // Высота области ноль возможна на первом кадре до измерения раскладки: NaN здесь означал бы
        // «яркость/громкость пропали» без единой ошибки в логе.
        assertEquals(0.25f, playerLevelAfterDrag(current = 0.25f, dragAmountPx = -50f, heightPx = 0f))
    }
}
