package com.aniko.app.feature.profile

import androidx.compose.ui.graphics.Color
import com.aniko.ui.theme.AnixColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [profileGenreAccentColor] — локальный акцент витрины по любимому жанру (P16.T13).
 *
 * Точки риска: пустой/`null` жанр не должен подкрашивать ничего (иначе профиль без
 * `preferredGenres` внезапно получил бы случайный акцент), а один и тот же жанр обязан всегда
 * маппиться в один и тот же цвет палитры (иначе кольцо аватара «мигало» бы разными цветами между
 * рекомпозициями/перезапусками для одного и того же пользователя).
 */
class ProfileGenreAccentColorTest {
    private val palette = listOf(Color.Red, Color.Green, Color.Blue)

    @Test
    fun nullGenre_returnsNoAccent() {
        assertNull(profileGenreAccentColor(topGenreName = null, palette = palette))
    }

    @Test
    fun blankGenre_returnsNoAccent() {
        assertNull(profileGenreAccentColor(topGenreName = "   ", palette = palette))
    }

    @Test
    fun emptyPalette_returnsNoAccent() {
        assertNull(profileGenreAccentColor(topGenreName = "экшен", palette = emptyList()))
    }

    @Test
    fun sameGenre_alwaysMapsToSameColor() {
        val first = profileGenreAccentColor(topGenreName = "фэнтези", palette = palette)
        val second = profileGenreAccentColor(topGenreName = "фэнтези", palette = palette)

        assertEquals(first, second)
    }

    @Test
    fun differentGenres_mapToDifferentColors() {
        // "экшен" и "драма" хэшируются в разные индексы 3-элементной палитры (проверено —
        // hashCode % 3 даёт 2 и 1 соответственно) — конкретная, не вероятностная проверка того,
        // что мэппинг не константа "один цвет на все жанры".
        val action = profileGenreAccentColor(topGenreName = "экшен", palette = palette)
        val drama = profileGenreAccentColor(topGenreName = "драма", palette = palette)

        assertNotEquals(action, drama)
    }

    @Test
    fun genreHashesToDeterministicPaletteIndex() {
        val result = profileGenreAccentColor(topGenreName = "комедия", palette = palette)

        assertEquals(Color.Green, result)
    }

    @Test
    fun caseInsensitive_sameColorRegardlessOfLetterCase() {
        val lower = profileGenreAccentColor(topGenreName = "экшен", palette = palette)
        val upper = profileGenreAccentColor(topGenreName = "ЭКШЕН", palette = palette)

        assertEquals(lower, upper)
    }

    @Test
    fun realActionGenre_withRealChartSeriesPalette_getsGenuinelyColoredAccentNotGray() {
        // Живой баг (Pixel 7, реальный аккаунт, топ-жанр "экшен" 13%): "экшен".hashCode().mod(6)
        // == 5, а элемент под индексом 5 у настоящей `AnixColors().chartSeries` — `Color.Gray`
        // (нейтральный "filler" донат-легенды). Кольцо аватара оказывалось неотличимо от
        // "без акцента". Регресс проверяется на РЕАЛЬНОЙ палитре, не на синтетической.
        val realChartSeries = AnixColors().chartSeries

        val result = profileGenreAccentColor(topGenreName = "экшен", palette = realChartSeries)

        assertNotEquals(null, result)
        assertNotEquals(Color.Gray, result)
        val color = requireNotNull(result)
        val spread = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
        assertTrue(spread >= 0.02f, "expected a hue-bearing color, got near-gray $color (spread=$spread)")
    }

    @Test
    fun paletteOnlyGray_afterFilteringIsEmpty_returnsNoAccent() {
        assertNull(profileGenreAccentColor(topGenreName = "экшен", palette = listOf(Color.Gray)))
    }
}
