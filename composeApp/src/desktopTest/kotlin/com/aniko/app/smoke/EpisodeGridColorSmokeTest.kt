package com.aniko.app.smoke

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.aniko.model.Episode
import com.aniko.ui.component.EpisodeGrid
import com.aniko.ui.theme.AppTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Регрессия на живой баг (2026-09-18, репорт пользователя со скриншотом): «не видны номера серий,
 * просто чёрные квадратики» на Title Detail в светлой теме. Причина — в `EpisodeGrid.EpisodeCell`
 * фон ячейки красился `containerColor.copy(alpha = alpha)`, где `copy` ЗАМЕНЯЕТ альфу: для
 * непросмотренной серии `containerColor = Color.Transparent` (RGB = 0,0,0) превращался в
 * НЕПРОЗРАЧНЫЙ чёрный, и номер цветом `onSurface` (почти чёрный в светлой теме) сливался с фоном.
 * Фикс — умножение альф (`containerColor.alpha * alpha`) вместо замены.
 *
 * Тест рендерит сетку (непросмотренная / просмотренная / текущая серии) в обеих темах и проверяет
 * пиксели кадра (`captureToImage`): у непросмотренной ячейки фон обязан совпадать с фоном экрана
 * (прозрачная ячейка поверх `colorScheme.background`), глиф номера — реально присутствовать с
 * контрастом к фону ячейки, у просмотренной — виден accent-тинт. Рендеры дополнительно сбрасываются
 * в `composeApp/build/reports/episodeGrid_{light,dark}.png` для глазной сверки.
 */
@OptIn(ExperimentalTestApi::class)
class EpisodeGridColorSmokeTest {
    @Test
    fun episodeNumbersAreVisible_lightTheme() {
        runSkikoComposeUiTest(size = Size(390f, 200f), density = Density(1f)) {
            setContent {
                AppTheme(darkTheme = false) {
                    EpisodeGrid(episodes = EPISODES, currentPosition = 3, onEpisodeClick = {})
                }
            }
            val frame = captureToImage()
            saveFrame(frame, "episodeGrid_light.png")
            assertEpisodeCellsReadable(frame, expectedBackground = LIGHT_BACKGROUND_RGB)
        }
    }

    @Test
    fun episodeNumbersAreVisible_darkTheme() {
        runSkikoComposeUiTest(size = Size(390f, 200f), density = Density(1f)) {
            setContent {
                AppTheme(darkTheme = true) {
                    EpisodeGrid(episodes = EPISODES, currentPosition = 3, onEpisodeClick = {})
                }
            }
            val frame = captureToImage()
            saveFrame(frame, "episodeGrid_dark.png")
            assertEpisodeCellsReadable(frame, expectedBackground = DARK_BACKGROUND_RGB)
        }
    }

    private fun assertEpisodeCellsReadable(
        frame: ImageBitmap,
        expectedBackground: Int,
    ) {
        val pixels = frame.toPixelMap().buffer

        // Непросмотренная ячейка (серия 1): прозрачный фон поверх фона экрана — точка внутри
        // ячейки вне глифа и бордера обязана совпадать с фоном темы. До фикса здесь был
        // непрозрачный #FF000000 (Transparent.copy(alpha = 1f)).
        val cellInterior = pixels[UNWATCHED_INTERIOR_Y * frame.width + UNWATCHED_INTERIOR_X]
        assertTrue(
            channelsClose(cellInterior, expectedBackground, tolerance = CHANNEL_TOLERANCE),
            "unwatched cell background ${cellInterior.hex()} must match theme background " +
                expectedBackground.hex(),
        )

        // Глиф номера в центре той же ячейки обязан контрастировать с фоном ячейки: ищем
        // максимальное расхождение каналов в центральной зоне. До фикса (почти чёрный текст
        // на чёрном квадрате в светлой теме) расхождение было ~30 — ниже порога.
        val maxGlyphDiff = maxChannelDiffInRegion(pixels, frame.width, GLYPH_REGION, cellInterior)
        assertTrue(
            maxGlyphDiff > GLYPH_CONTRAST_THRESHOLD,
            "episode number glyph must contrast with cell background, maxDiff=$maxGlyphDiff",
        )

        // Просмотренная ячейка (серия 2): accent-тинт 0.22 поверх фона — обязана отличаться
        // от фона темы.
        val watchedInterior = pixels[WATCHED_INTERIOR_Y * frame.width + WATCHED_INTERIOR_X]
        assertTrue(
            !channelsClose(watchedInterior, expectedBackground, tolerance = CHANNEL_TOLERANCE),
            "watched cell must carry accent tint, got ${watchedInterior.hex()} vs background " +
                expectedBackground.hex(),
        )
    }

    private fun maxChannelDiffInRegion(
        pixels: IntArray,
        width: Int,
        region: IntRange,
        reference: Int,
    ): Int {
        var max = 0
        for (y in region) {
            for (x in region) {
                val px = pixels[y * width + x]
                val diff =
                    maxOf(
                        channelDiff(px, reference, 16),
                        channelDiff(px, reference, 8),
                        channelDiff(px, reference, 0),
                    )
                if (diff > max) max = diff
            }
        }
        return max
    }

    private fun channelDiff(a: Int, b: Int, shift: Int): Int =
        kotlin.math.abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF))

    private fun channelsClose(a: Int, b: Int, tolerance: Int): Boolean =
        channelDiff(a, b, 16) <= tolerance && channelDiff(a, b, 8) <= tolerance && channelDiff(a, b, 0) <= tolerance

    private fun Int.hex(): String = "#%08X".format(this)

    private fun saveFrame(frame: ImageBitmap, fileName: String) {
        val pixels = frame.toPixelMap().buffer
        val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, frame.width, frame.height, pixels, 0, frame.width)
        val out = File("build/reports/episodeGrid", fileName)
        out.parentFile?.mkdirs()
        ImageIO.write(image, "png", out)
    }

    private companion object {
        // Серия 1 — непросмотренная, 2 — просмотренная, 3 — непросмотренная текущая.
        val EPISODES =
            listOf(
                Episode(position = 1, name = null, isWatched = false),
                Episode(position = 2, name = null, isWatched = true),
                Episode(position = 3, name = null, isWatched = false),
            )

        // Ячейка 56dp, зазор 8dp, density = 1f, сетка прижата к (0,0) корневого Box AppTheme.
        // Точка (12,12) — внутри ячейки 1 вне глифа и бордера (corner 8dp, бордер 1dp).
        const val UNWATCHED_INTERIOR_X = 12
        const val UNWATCHED_INTERIOR_Y = 12

        // Ячейка 2 начинается с x = 56 + 8 = 64.
        const val WATCHED_INTERIOR_X = 64 + 12
        const val WATCHED_INTERIOR_Y = 12

        // Центральная зона ячейки 1, где лежит глиф номера (labelLarge ~16sp вокруг центра 28,28).
        val GLYPH_REGION = 18..38

        // Фоны тем из AnixPalette (iOS-like редизайн 2026-09-11): светлая — iOS
        // systemGroupedBackground light, тёмная — чистый чёрный.
        const val LIGHT_BACKGROUND_RGB = -0xd0d09 // #FFF2F2F7
        const val DARK_BACKGROUND_RGB = -0x1000000 // #FF000000

        const val CHANNEL_TOLERANCE = 12
        const val GLYPH_CONTRAST_THRESHOLD = 60
    }
}
