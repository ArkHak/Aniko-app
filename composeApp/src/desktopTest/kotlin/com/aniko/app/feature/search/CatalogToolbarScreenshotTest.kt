package com.aniko.app.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.aniko.data.paging.PagingState
import com.aniko.model.CatalogFilter
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.i18n.EnStrings
import com.aniko.ui.i18n.RuStrings
import com.aniko.ui.i18n.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Офскрин-рендер верхней панели Каталога (`CatalogScreenContent`) на всех размерах окна, в
 * светлой/тёмной теме, RU/EN, с закрытыми и открытыми меню чипов. Кадры сбрасываются в PNG
 * (`SCREENSHOT_DIR`) для глазной сверки, а сами тесты проверяют то, что глазом легко пропустить:
 * поле поиска не ниже 48.dp и подсказка реально нарисована и не обрезана (корневой баг: поле на
 * `OutlinedTextField` с `height(42.dp)` показывало пустой серый прямоугольник), зоны нажатия
 * чипов ≥ 48.dp, выпадающее меню/шторка не выходят за окно, число колонок сетки адаптивно.
 */
@OptIn(ExperimentalTestApi::class)
class CatalogToolbarScreenshotTest {
    // ---- Expanded (>= 840dp): один ряд, выпадающие меню -------------------------------------------

    @Test
    fun expandedLightEnPlain() =
        shot("expanded_light_en_plain", EXPANDED, AnixWindowSize.Expanded, EnStrings, dark = false, state = PLAIN) {
            assertGridColumns(expected = 5)
        }

    @Test
    fun expandedDarkRuActive() = shot("expanded_dark_ru_active", EXPANDED, AnixWindowSize.Expanded, RuStrings, dark = true, state = ACTIVE)

    @Test
    fun expandedLightRuTypedQuery() =
        shot("expanded_light_ru_typed", EXPANDED, AnixWindowSize.Expanded, RuStrings, dark = false, state = TYPED)

    @Test
    fun expandedLightRuGenresMenu() =
        shot(
            "expanded_light_ru_menu_genres",
            EXPANDED,
            AnixWindowSize.Expanded,
            RuStrings,
            dark = false,
            state = ACTIVE,
            openChipTag = CatalogTestTags.GENRES_CHIP,
        ) { assertInsideWindow(CatalogTestTags.MENU_GENRES, EXPANDED) }

    @Test
    fun expandedDarkEnStatusMenu() =
        shot(
            "expanded_dark_en_menu_status",
            EXPANDED,
            AnixWindowSize.Expanded,
            EnStrings,
            dark = true,
            state = ACTIVE,
            openChipTag = CatalogTestTags.STATUS_CHIP,
        ) { assertInsideWindow(CatalogTestTags.MENU_STATUS, EXPANDED) }

    /** Нижняя граница Expanded: сетка не сжимается в «марки» (3 колонки вместо 5), ряд скроллится. */
    @Test
    fun expandedNarrowAdaptiveColumns() =
        shot("expanded_narrow_light_ru_active", EXPANDED_NARROW, AnixWindowSize.Expanded, RuStrings, dark = false, state = ACTIVE) {
            assertGridColumns(expected = 3)
        }

    /** Окно почти минимальной высоты: меню жанров скроллится и не вылезает за окно. */
    @Test
    fun expandedShortWindowGenresMenuStaysInside() =
        shot(
            "expanded_short_light_ru_menu_genres",
            EXPANDED_SHORT,
            AnixWindowSize.Expanded,
            RuStrings,
            dark = false,
            state = ACTIVE,
            openChipTag = CatalogTestTags.GENRES_CHIP,
        ) { assertInsideWindow(CatalogTestTags.MENU_GENRES, EXPANDED_SHORT) }

    // ---- Medium (600..840dp): один ряд, выпадающие меню -------------------------------------------

    @Test
    fun mediumLightRuPlain() = shot("medium_light_ru_plain", MEDIUM, AnixWindowSize.Medium, RuStrings, dark = false, state = PLAIN)

    @Test
    fun mediumDarkEnActive() = shot("medium_dark_en_active", MEDIUM, AnixWindowSize.Medium, EnStrings, dark = true, state = ACTIVE)

    // ---- Compact (< 600dp): поле на всю ширину, ряд чипов ниже, шторки ------------------------------

    @Test
    fun compactLightRuPlain() =
        shot("compact_light_ru_plain", COMPACT, AnixWindowSize.Compact, RuStrings, dark = false, state = PLAIN) {
            assertCompactTouchTargets(withActiveChips = false)
        }

    @Test
    fun compactDarkEnActive() =
        shot("compact_dark_en_active", COMPACT, AnixWindowSize.Compact, EnStrings, dark = true, state = ACTIVE) {
            assertCompactTouchTargets(withActiveChips = true)
        }

    @Test
    fun compactLightRuGenresSheet() =
        shot(
            "compact_light_ru_sheet_genres",
            COMPACT,
            AnixWindowSize.Compact,
            RuStrings,
            dark = false,
            state = ACTIVE,
            openChipTag = CatalogTestTags.GENRES_CHIP,
        ) { assertInsideWindow(CatalogTestTags.MENU_GENRES, COMPACT) }

    @Test
    fun compactDarkRuStatusSheet() =
        shot(
            "compact_dark_ru_sheet_status",
            COMPACT,
            AnixWindowSize.Compact,
            RuStrings,
            dark = true,
            state = ACTIVE,
            openChipTag = CatalogTestTags.STATUS_CHIP,
        ) { assertInsideWindow(CatalogTestTags.MENU_STATUS, COMPACT) }

    // ---- инфраструктура ---------------------------------------------------------------------------

    @Suppress("LongParameterList") // Параметры одного кадра: размер/тема/локаль/состояние/меню.
    private fun shot(
        name: String,
        size: Size,
        windowSize: AnixWindowSize,
        strings: Strings,
        dark: Boolean,
        state: SearchState,
        openChipTag: String? = null,
        extra: SkikoComposeUiTest.() -> Unit = {},
    ) {
        runCatalogUiTest(
            size = size,
            windowSize = windowSize,
            strings = strings,
            darkTheme = dark,
            content = {
                val screen: @Composable () -> Unit = { CatalogScreenContent(state = state, onIntent = {}, onReleaseClick = {}) }
                if (windowSize == AnixWindowSize.Expanded) ExpandedShell(screen) else screen()
            },
        ) {
            waitForIdle()
            onNodeWithTag(CatalogTestTags.SEARCH_FIELD).assertIsDisplayed().assertHeightIsAtLeast(MIN_TOUCH_TARGET)
            if (openChipTag != null) {
                onNodeWithTag(openChipTag).performClick()
                waitForIdle()
            }
            extra()
            val frame = captureToImage()
            saveShot(frame, name)
            if (state.query.isEmpty() && openChipTag == null) assertHintIsRendered(frame)
        }
    }

    /**
     * Подсказка пустого поля реально нарисована и не обрезана по вертикали: между иконкой и правым
     * краем поля есть «чернильные» пиксели, а полосы у верхней/нижней рамки — чистые.
     */
    private fun SkikoComposeUiTest.assertHintIsRendered(frame: ImageBitmap) {
        val bounds = onNodeWithTag(CatalogTestTags.SEARCH_FIELD).getBoundsInRoot()
        val left = (bounds.left + HINT_LEFT_INSET).value.toInt()
        val right = (bounds.right - HINT_RIGHT_INSET).value.toInt()
        val top = bounds.top.value.toInt()
        val bottom = bounds.bottom.value.toInt()
        val background = frame.pixel(right, (top + bottom) / 2)
        val ink = frame.inkPixels(left, top + EDGE_BAND, right, bottom - EDGE_BAND, background)
        assertTrue(ink > MIN_HINT_INK_PIXELS, "hint must be drawn inside the search field, ink pixels = $ink")
        assertEquals(
            0,
            frame.inkPixels(left, top + EDGE_BAND, right, top + EDGE_BAND + CLEAN_BAND, background),
            "hint is clipped at the top",
        )
        assertEquals(
            0,
            frame.inkPixels(left, bottom - EDGE_BAND - CLEAN_BAND, right, bottom - EDGE_BAND, background),
            "hint is clipped at the bottom",
        )
    }

    /** Меню/шторка целиком внутри окна (`boundsInWindow` — в координатах окна, в том числе у popup). */
    private fun SkikoComposeUiTest.assertInsideWindow(
        tag: String,
        size: Size,
    ) {
        val node = onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val bounds = node.boundsInWindow
        assertTrue(
            bounds.left >= 0f && bounds.top >= 0f && bounds.right <= size.width && bounds.bottom <= size.height,
            "menu $tag must stay inside the ${size.width}x${size.height} window, bounds = $bounds",
        )
    }

    /** Число колонок первой строки сетки выдачи: ячейки (кликабельные, с названием) на одной верхней границе. */
    private fun SkikoComposeUiTest.assertGridColumns(expected: Int) {
        val titles = sampleReleases().map { it.title }.toSet()
        val cells =
            onAllNodes(hasClickAction())
                .fetchSemanticsNodes()
                .filter { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it in titles } == true }
        assertTrue(cells.isNotEmpty(), "grid cells must be on screen")
        val firstRowTop = cells.minOf { it.boundsInRoot.top }
        assertEquals(expected, cells.count { it.boundsInRoot.top == firstRowTop }, "grid columns in the first row")
    }

    /** Зоны нажатия панели на Compact: поле, вкладки, чипы, ✕, «Сбросить» — не меньше 48×48.dp. */
    private fun SkikoComposeUiTest.assertCompactTouchTargets(withActiveChips: Boolean) {
        val tags = mutableListOf(CatalogTestTags.STATUS_CHIP, CatalogTestTags.GENRES_CHIP)
        if (withActiveChips) tags += listOf(CatalogTestTags.STATUS_CLEAR, CatalogTestTags.GENRES_CLEAR, CatalogTestTags.RESET)
        tags.forEach { tag ->
            onNodeWithTag(tag).assertHeightIsAtLeast(MIN_TOUCH_TARGET).assertWidthIsAtLeast(MIN_TOUCH_TARGET)
        }
    }

    private companion object {
        val MIN_TOUCH_TARGET = 48.dp
        val HINT_LEFT_INSET = 52.dp
        val HINT_RIGHT_INSET = 28.dp
        const val EDGE_BAND = 3
        const val CLEAN_BAND = 5
        const val MIN_HINT_INK_PIXELS = 40
        const val MAX_COLUMNS_PROBE = 8

        val EXPANDED = Size(1400f, 900f)
        val EXPANDED_NARROW = Size(860f, 800f)
        val EXPANDED_SHORT = Size(1200f, 460f)
        val MEDIUM = Size(720f, 900f)
        val COMPACT = Size(390f, 844f)

        val PLAIN = SearchState(pagingState = PagingState(items = sampleReleases()))
        val ACTIVE =
            SearchState(
                filter = CatalogFilter(statusId = 2, genres = setOf("экшен", "драма")),
                pagingState = PagingState(items = sampleReleases()),
            )
        val TYPED = PLAIN.copy(query = "Магическая битва")
    }
}
