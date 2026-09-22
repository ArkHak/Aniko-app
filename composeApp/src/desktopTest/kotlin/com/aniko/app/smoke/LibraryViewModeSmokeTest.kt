package com.aniko.app.smoke

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.aniko.app.feature.library.LibraryTestTags
import com.aniko.data.librarypreferences.LibraryPreferencesStore
import com.aniko.data.librarypreferences.LibraryViewMode
import com.aniko.ui.testing.AnixTestTags
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val VIEW_MODE_AWAIT_TIMEOUT_MS = 5_000L

/** Заголовки релизов на вкладке «Смотрю» в фикстуре: достаточно, чтобы заполнить сетку в несколько рядов. */
private val VIEW_MODE_TITLES = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel")

/** Постоянная часть `contentDescription` строки/карточки вида «Список»: подпись прогресса «3 of 12». */
private const val PROGRESS_LABEL = "3 of 12"

/** Страница `profile/list/all/1/0` («Смотрю»): у каждого релиза прогресс «3 of 12». */
private fun viewModePage(): String {
    val items =
        VIEW_MODE_TITLES.mapIndexed { index, title ->
            """{"id":${100 + index},"title_ru":"$title","episodes_total":12,"last_view_episode":3}"""
        }
    val content = items.joinToString(",")
    return """{"code":0,"content":[$content],"current_page":0,"total_page_count":1}"""
}

/** Заголовок единственного релиза второй страницы в пагинационном сценарии. */
private const val SECOND_PAGE_TITLE = "Zulu"

/** Число релизов первой страницы в пагинационном сценарии: заведомо больше, чем помещается на экран. */
private const val LONG_PAGE_SIZE = 60

/** Первая страница «Смотрю» из [LONG_PAGE_SIZE] релизов, дальше есть ещё одна (`total_page_count: 2`). */
private fun longFirstPage(): String {
    val items =
        (0 until LONG_PAGE_SIZE).map { index ->
            """{"id":${1000 + index},"title_ru":"Show $index","episodes_total":12,"last_view_episode":3}"""
        }
    return """{"code":0,"content":[${items.joinToString(",")}],"current_page":0,"total_page_count":2}"""
}

/** Вторая (последняя) страница: один релиз [SECOND_PAGE_TITLE]. */
private fun secondPage(): String =
    """{"code":0,"content":[{"id":2000,"title_ru":"$SECOND_PAGE_TITLE","episodes_total":12,"last_view_episode":3}],""" +
        """"current_page":1,"total_page_count":2}"""

/**
 * Английская локаль + (необязательно) уже сохранённый выбор вида — `Settings` живёт в Koin, а не в
 * самом экране, поэтому сохранённый выбор задаётся так же, как его оставил бы прошлый запуск.
 */
private fun viewModeSettings(savedViewMode: String? = null): KoinApplication.() -> Unit =
    {
        modules(
            module {
                single<Settings> {
                    MapSettings().apply {
                        putString("locale.language_tag", "en")
                        if (savedViewMode != null) putString("library.view_mode", savedViewMode)
                    }
                }
            },
        )
    }

/**
 * Переключатель вида «Список» ↔ «Сетка постеров» на «Мои списки».
 *
 * На каждом из трёх классов окна (Compact 390dp, Medium 720dp, Expanded 1400dp): стартовый вид
 * совпадает с прежним поведением экрана (Compact/Expanded — список, Medium — сетка), кнопка с
 * зоной нажатия не меньше 48dp меняет вид туда-обратно, выбор запоминается в
 * [LibraryPreferencesStore], а долгое нажатие с контекстным меню работает в обоих видах. Что
 * именно за вид на экране, определяется по `testTag` контейнера и по подписи прогресса «3 of 12»,
 * которая есть только у вида «Список» (плитка постера озвучивает лишь заголовок).
 */
@OptIn(ExperimentalTestApi::class)
class LibraryViewModeSmokeTest {
    private val routes: Map<String, () -> String> = mapOf("profile/list/all/1/0" to { viewModePage() })

    @Test
    fun compact_startsAsList_andToggleSwitchesToGridAndBack() = toggleScenario(COMPACT, WindowKind.Compact, startsAs = LibraryViewMode.List)

    @Test
    fun medium_startsAsGrid_andToggleSwitchesToListAndBack() = toggleScenario(MEDIUM, WindowKind.Medium, startsAs = LibraryViewMode.Grid)

    @Test
    fun expanded_startsAsList_andToggleSwitchesToGridAndBack() =
        toggleScenario(EXPANDED, WindowKind.Expanded, startsAs = LibraryViewMode.List)

    /** Сохранённый выбор сильнее размера окна: на телефоне «сетка», на планшете и десктопе «список». */
    @Test
    fun savedChoiceOverridesWindowSizeDefault() {
        runLibrary(COMPACT, WindowKind.Compact, saved = "grid") { assertViewMode(LibraryViewMode.Grid) }
        runLibrary(MEDIUM, WindowKind.Medium, saved = "list") { assertViewMode(LibraryViewMode.List) }
        runLibrary(EXPANDED, WindowKind.Expanded, saved = "grid") { assertViewMode(LibraryViewMode.Grid) }
    }

    /** Мусор в `Settings` читается как «не выбирал» — вид по умолчанию для размера окна. */
    @Test
    fun garbageInSettingsFallsBackToWindowSizeDefault() {
        runLibrary(COMPACT, WindowKind.Compact, saved = "carousel") { assertViewMode(LibraryViewMode.List) }
        runLibrary(MEDIUM, WindowKind.Medium, saved = "carousel") { assertViewMode(LibraryViewMode.Grid) }
    }

    /** Выбор общий для вкладок: на другой вкладке кнопка предлагает то же действие, а не сбрасывается. */
    @Test
    fun choiceIsSharedBetweenTabs() {
        runLibrary(COMPACT, WindowKind.Compact) {
            onNodeWithContentDescription("Show as grid").performClick()
            assertViewMode(LibraryViewMode.Grid)

            onNodeWithContentDescription("Plan to Watch").performClick()

            onNodeWithContentDescription("Show as list").assertIsDisplayed()
        }
    }

    @Test
    fun compact_contextMenuWorksInBothViews() = contextMenuScenario(COMPACT, WindowKind.Compact)

    @Test
    fun medium_contextMenuWorksInBothViews() = contextMenuScenario(MEDIUM, WindowKind.Medium)

    @Test
    fun expanded_contextMenuWorksInBothViews() = contextMenuScenario(EXPANDED, WindowKind.Expanded)

    @Test
    fun compact_paginationWorksInBothViews() = paginationScenario(COMPACT, WindowKind.Compact)

    @Test
    fun medium_paginationWorksInBothViews() = paginationScenario(MEDIUM, WindowKind.Medium)

    @Test
    fun expanded_paginationWorksInBothViews() = paginationScenario(EXPANDED, WindowKind.Expanded)

    /**
     * Стартовый вид → кнопка → противоположный вид (и кнопка предлагает вернуться) → выбор записан в
     * стор → кнопка → снова стартовый вид. Зона нажатия кнопки не меньше 48dp на любом размере.
     */
    private fun toggleScenario(
        size: Size,
        kind: WindowKind,
        startsAs: LibraryViewMode,
    ) = runLibrary(size, kind) {
        val store = KoinPlatform.getKoin().get<LibraryPreferencesStore>()
        assertNull(store.viewMode.value, "no explicit choice until the user presses the button")
        assertViewMode(startsAs)
        onNodeWithTag(LibraryTestTags.VIEW_MODE_TOGGLE)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)

        toggleButton(startsAs).performClick()
        assertViewMode(startsAs.toggled())
        assertEquals(startsAs.toggled(), store.viewMode.value, "choice must be persisted")

        toggleButton(startsAs.toggled()).performClick()
        assertViewMode(startsAs)
        assertEquals(startsAs, store.viewMode.value)
    }

    /**
     * Долгое нажатие на карточке открывает [com.aniko.app.feature.library.LibraryContextMenu], и
     * выбранный в нём пункт реально переносит релиз в другой список (карточка пропадает из вкладки
     * «Смотрю») — в каждом из двух видов, каждый вид отдельным запуском с сохранённым выбором.
     *
     * Пункт меню нажимается семантическим действием `OnClick`, а не касанием по координатам: у
     * узлов попапа в headless-сцене `boundsInRoot` не совпадают с координатами окна, и касание по
     * ним попадало в соседнюю карточку под меню (открывалась её страница). Долгое нажатие по самой
     * карточке — настоящий ввод, он и открывает меню.
     */
    private fun contextMenuScenario(
        size: Size,
        kind: WindowKind,
    ) = LibraryViewMode.entries.forEach { mode ->
        runLibrary(size, kind, saved = mode.name.lowercase()) {
            assertViewMode(mode)
            val first = VIEW_MODE_TITLES.first()

            onNodeWithContentDescription(first, substring = true).performTouchInput { longClick() }
            onNode(hasText(MOVE_TO_PLAN) and hasClickAction()).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)

            await { onAllNodesWithContentDescription(first, substring = true).fetchSemanticsNodes().isEmpty() }
            // Остальные карточки вкладки на месте: пропала ровно та, что перенесли.
            onAllNodesWithContentDescription(VIEW_MODE_TITLES[1], substring = true).onFirst().assertIsDisplayed()
            assertViewMode(mode)
        }
    }

    /**
     * Подгрузка страниц в каждом из двух видов: пока конец списка не показан, вторая страница не
     * запрашивается; после прокрутки к последнему элементу она запрашивается и её релиз появляется в
     * конце списка. Первая страница — [LONG_PAGE_SIZE] релизов, то есть заведомо длиннее экрана.
     */
    private fun paginationScenario(
        size: Size,
        kind: WindowKind,
    ) = LibraryViewMode.entries.forEach { mode ->
        val secondPageRequested = AtomicBoolean(false)
        val pagedRoutes =
            mapOf(
                "profile/list/all/1/0" to { longFirstPage() },
                "profile/list/all/1/1" to {
                    secondPageRequested.set(true)
                    secondPage()
                },
            )
        runLibrary(size, kind, saved = mode.name.lowercase(), apiRoutes = pagedRoutes, firstTitle = "Show 0") {
            assertViewMode(mode)
            val containerTag = if (mode == LibraryViewMode.List) LibraryTestTags.LIST_CONTENT else LibraryTestTags.GRID_CONTENT
            waitForIdle()
            assertEquals(false, secondPageRequested.get(), "$mode: next page must wait until the end of the list is near")

            onNodeWithTag(containerTag).performScrollToIndex(LONG_PAGE_SIZE - 1)
            await { secondPageRequested.get() }

            onNodeWithTag(containerTag).performScrollToIndex(LONG_PAGE_SIZE)
            await { onAllNodesWithContentDescription(SECOND_PAGE_TITLE, substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    // ---- инфраструктура -------------------------------------------------------------------------

    @Suppress("LongParameterList") // Параметры одного запуска: окно, сохранённый вид, маршруты, ожидаемый заголовок, тело.
    private fun runLibrary(
        size: Size,
        kind: WindowKind,
        saved: String? = null,
        apiRoutes: Map<String, () -> String> = routes,
        firstTitle: String = VIEW_MODE_TITLES.first(),
        body: SkikoComposeUiTest.(pressBack: () -> Unit) -> Unit,
    ) = runAnikoSmokeTest(
        apiRoutes = apiRoutes,
        initialToken = "fake-token",
        koinDeclaration = viewModeSettings(saved),
        windowSize = size,
    ) { pressBack ->
        onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()
        when (kind) {
            WindowKind.Compact -> onNodeWithTag(AnixTestTags.bottomNavItem("Library")).performClick()
            // Рельса Medium и сайдбар Expanded подписаны видимым текстом, тегов у них нет.
            WindowKind.Medium, WindowKind.Expanded -> onAllNodes(hasText("My Lists") and hasClickAction()).onFirst().performClick()
        }
        onNodeWithTag(AnixTestTags.LIBRARY_SCREEN_ROOT).assertIsDisplayed()
        await { onAllNodesWithContentDescription(firstTitle, substring = true).fetchSemanticsNodes().isNotEmpty() }
        body(pressBack)
    }

    /** Кнопка-переключатель: подпись описывает вид, НА который переключимся, — по ней и ищем. */
    private fun SkikoComposeUiTest.toggleButton(currentMode: LibraryViewMode) =
        onNodeWithContentDescription(
            when (currentMode) {
                LibraryViewMode.List -> "Show as grid"
                LibraryViewMode.Grid -> "Show as list"
            },
        )

    /** Текущий вид по подписи кнопки: «Show as grid» — значит сейчас список. */
    private fun SkikoComposeUiTest.currentViewMode(): LibraryViewMode =
        if (onAllNodesWithContentDescription("Show as grid").fetchSemanticsNodes().isNotEmpty()) {
            LibraryViewMode.List
        } else {
            LibraryViewMode.Grid
        }

    /**
     * На экране ровно [mode]: контейнер своего вида на месте, чужого нет, подпись прогресса «3 of 12»
     * есть у списка и отсутствует у сетки, кнопка предлагает противоположный вид.
     */
    private fun SkikoComposeUiTest.assertViewMode(mode: LibraryViewMode) {
        val listTag = LibraryTestTags.LIST_CONTENT
        val gridTag = LibraryTestTags.GRID_CONTENT
        val (shownTag, hiddenTag) = if (mode == LibraryViewMode.List) listTag to gridTag else gridTag to listTag
        await { onAllNodes(hasTestTag(shownTag)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(shownTag).assertIsDisplayed()
        onAllNodes(hasTestTag(hiddenTag)).assertCountEquals(0)
        val progressNodes = onAllNodesWithContentDescription(PROGRESS_LABEL, substring = true).fetchSemanticsNodes()
        assertEquals(mode == LibraryViewMode.List, progressNodes.isNotEmpty(), "progress label is a list-only element")
        toggleButton(mode).assertIsDisplayed()
    }

    /** Запас на асинхронный путь «клик → Flow/БД → перекомпозиция» (см. `LIBRARY_AWAIT_TIMEOUT_MS`). */
    private fun SkikoComposeUiTest.await(condition: () -> Boolean) {
        waitUntil(timeoutMillis = VIEW_MODE_AWAIT_TIMEOUT_MS, condition = condition)
    }

    private enum class WindowKind { Compact, Medium, Expanded }

    private companion object {
        const val MOVE_TO_PLAN = "Move to “Plan to Watch”"
        val MIN_TOUCH_TARGET = 48.dp

        val COMPACT = Size(390f, 844f)
        val MEDIUM = Size(720f, 900f)
        val EXPANDED = Size(1400f, 900f)
    }
}
