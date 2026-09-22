package com.aniko.app.smoke

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.aniko.app.feature.library.LibraryTestTags
import com.aniko.app.feature.search.inkPixels
import com.aniko.app.feature.search.pixel
import com.aniko.app.feature.search.saveShot
import com.aniko.ui.testing.AnixTestTags
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

private const val SHOT_AWAIT_TIMEOUT_MS = 5_000L

/** Пустая страница списка: вкладка без релизов. */
private const val EMPTY_PAGE = """{"code":0,"content":[],"current_page":0,"total_page_count":1}"""

/** Разные по длине названия: одна строка, перенос на две, обрезка троеточием — как на живом экране. */
private val SHOT_TITLES =
    listOf(
        "Тёмнее чёрного",
        "Магическая битва",
        "Ходячий замок",
        "Клинок, рассекающий демонов: Поезд «Бесконечный»",
        "Атака титанов",
        "Хантер х Хантер",
        "Ковбой Бибоп",
        "Стальной алхимик",
        "Клан Сопрано",
        "Тетрадь смерти",
        "Скрытые тайны",
        "Евангелион",
        "Ван-Пис",
        "Наруто",
    )

/** Страница «Смотрю»: разный прогресс, рейтинги и избранное, чтобы бейджи постеров были видны. */
private fun shotPage(): String {
    val items =
        SHOT_TITLES.mapIndexed { index, title ->
            val grade = 4.0 + (index % 10) / 10.0
            """{"id":${100 + index},"title_ru":"$title","episodes_total":${12 + index},""" +
                """"last_view_episode":${(index * 3) % 12},"grade":$grade,"profile_list_status":1,""" +
                """"is_favorite":${index % 4 == 0}}"""
        }
    val content = items.joinToString(",")
    return """{"code":0,"content":[$content],"current_page":0,"total_page_count":1}"""
}

private fun shotSettings(
    dark: Boolean,
    viewMode: String,
): KoinApplication.() -> Unit =
    {
        modules(
            module {
                single<Settings> {
                    MapSettings().apply {
                        putString("locale.language_tag", "ru")
                        putString("library.view_mode", viewMode)
                        if (dark) putString("theme.mode", "dark")
                    }
                }
            },
        )
    }

/**
 * Офскрин-кадры экрана «Мои списки» в обоих видах на Compact/Medium/Expanded, в светлой и тёмной теме
 * (RU) — для глазной сверки, PNG пишутся через `saveShot` (см. `SCREENSHOT_DIR`). Сами тесты
 * проверяют то, что глазом легко пропустить: кнопка вида подписана по-русски и стоит на месте, а её
 * иконка реально нарисована (в квадрате кнопки есть «чернильные» пиксели, а не пустой фон).
 */
@OptIn(ExperimentalTestApi::class)
class LibraryViewModeScreenshotTest {
    @Test fun compactListLight() = shot("library_compact_list_light_ru", COMPACT, "list", dark = false, compact = true)

    @Test fun compactListDark() = shot("library_compact_list_dark_ru", COMPACT, "list", dark = true, compact = true)

    @Test fun compactGridLight() = shot("library_compact_grid_light_ru", COMPACT, "grid", dark = false, compact = true)

    @Test fun compactGridDark() = shot("library_compact_grid_dark_ru", COMPACT, "grid", dark = true, compact = true)

    @Test fun mediumListLight() = shot("library_medium_list_light_ru", MEDIUM, "list", dark = false, compact = false)

    @Test fun mediumListDark() = shot("library_medium_list_dark_ru", MEDIUM, "list", dark = true, compact = false)

    @Test fun mediumGridLight() = shot("library_medium_grid_light_ru", MEDIUM, "grid", dark = false, compact = false)

    @Test fun mediumGridDark() = shot("library_medium_grid_dark_ru", MEDIUM, "grid", dark = true, compact = false)

    @Test fun expandedListLight() = shot("library_expanded_list_light_ru", EXPANDED, "list", dark = false, compact = false)

    @Test fun expandedListDark() = shot("library_expanded_list_dark_ru", EXPANDED, "list", dark = true, compact = false)

    @Test fun expandedGridLight() = shot("library_expanded_grid_light_ru", EXPANDED, "grid", dark = false, compact = false)

    @Test fun expandedGridDark() = shot("library_expanded_grid_dark_ru", EXPANDED, "grid", dark = true, compact = false)

    /** Пустая вкладка на Expanded: сообщение «Список пуст» под чипами, кнопка вида на месте. */
    @Test
    fun expandedEmptyTab() =
        runAnikoSmokeTest(
            apiRoutes = mapOf("profile/list/all/1/0" to { shotPage() }, "profile/list/all/2/0" to { EMPTY_PAGE }),
            initialToken = "fake-token",
            koinDeclaration = shotSettings(dark = false, viewMode = "list"),
            windowSize = EXPANDED,
        ) {
            openLibraryExpanded()
            onNode(hasText("В планах") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = SHOT_AWAIT_TIMEOUT_MS) {
                onAllNodes(hasText("Список пуст")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Показать сеткой").assertIsDisplayed()
            saveShot(captureToImage(), "library_expanded_empty_light_ru")
        }

    /** Ошибка загрузки вкладки на Expanded: маршрута нет (404) — «Не удалось загрузить список» + повтор. */
    @Test
    fun expandedErrorTab() =
        runAnikoSmokeTest(
            apiRoutes = mapOf("profile/list/all/1/0" to { shotPage() }),
            initialToken = "fake-token",
            koinDeclaration = shotSettings(dark = false, viewMode = "grid"),
            windowSize = EXPANDED,
        ) {
            openLibraryExpanded()
            onNode(hasText("В планах") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = SHOT_AWAIT_TIMEOUT_MS) {
                onAllNodes(hasText("Не удалось загрузить список")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Показать списком").assertIsDisplayed()
            saveShot(captureToImage(), "library_expanded_error_light_ru")
        }

    private fun SkikoComposeUiTest.openLibraryExpanded() {
        onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()
        onAllNodes(hasText("Мои списки") and hasClickAction()).onFirst().performClick()
        onNodeWithTag(AnixTestTags.LIBRARY_SCREEN_ROOT).assertIsDisplayed()
        waitUntil(timeoutMillis = SHOT_AWAIT_TIMEOUT_MS) {
            onAllNodesWithContentDescription(SHOT_TITLES.first(), substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shot(
        name: String,
        size: Size,
        viewMode: String,
        dark: Boolean,
        compact: Boolean,
    ) = runAnikoSmokeTest(
        apiRoutes = mapOf("profile/list/all/1/0" to { shotPage() }),
        initialToken = "fake-token",
        koinDeclaration = shotSettings(dark, viewMode),
        windowSize = size,
    ) {
        onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()
        if (compact) {
            onNodeWithTag(AnixTestTags.bottomNavItem("Library")).performClick()
        } else {
            onAllNodes(hasText("Мои списки") and hasClickAction()).onFirst().performClick()
        }
        onNodeWithTag(AnixTestTags.LIBRARY_SCREEN_ROOT).assertIsDisplayed()
        waitUntil(timeoutMillis = SHOT_AWAIT_TIMEOUT_MS) {
            onAllNodesWithContentDescription(SHOT_TITLES.first(), substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()

        // Подпись кнопки — русская и описывает вид, НА который переключимся.
        val label = if (viewMode == "list") "Показать сеткой" else "Показать списком"
        onNodeWithContentDescription(label).assertIsDisplayed()

        val frame = captureToImage()
        saveShot(frame, name)
        assertIconIsDrawn(frame)
    }

    /**
     * Иконка кнопки вида нарисована: в её центральном квадрате есть пиксели, заметно отличающиеся от
     * фона кнопки (угол видимого квадрата, где иконки заведомо нет). Пустой глиф/«тофу» без чернил
     * этого не пройдёт.
     */
    private fun SkikoComposeUiTest.assertIconIsDrawn(frame: androidx.compose.ui.graphics.ImageBitmap) {
        val bounds = onNodeWithTag(LibraryTestTags.VIEW_MODE_TOGGLE).getBoundsInRoot()
        val centerX = ((bounds.left + bounds.right) / 2).value.toInt()
        val centerY = ((bounds.top + bounds.bottom) / 2).value.toInt()
        // Видимый квадрат 28/32dp: фон кнопки берём у самого края квадрата (иконка 18dp его не касается).
        val background = frame.pixel(centerX - ICON_PROBE_BACKGROUND_OFFSET, centerY - ICON_PROBE_BACKGROUND_OFFSET)
        val ink =
            frame.inkPixels(
                left = centerX - ICON_HALF,
                top = centerY - ICON_HALF,
                right = centerX + ICON_HALF,
                bottom = centerY + ICON_HALF,
                background = background,
            )
        assertTrue(ink > MIN_ICON_INK_PIXELS, "view mode icon must be drawn inside the button, ink pixels = $ink")
    }

    private companion object {
        const val ICON_HALF = 8
        const val ICON_PROBE_BACKGROUND_OFFSET = 12
        const val MIN_ICON_INK_PIXELS = 30

        val COMPACT = Size(390f, 844f)
        val MEDIUM = Size(720f, 900f)
        val EXPANDED = Size(1400f, 900f)
    }
}
