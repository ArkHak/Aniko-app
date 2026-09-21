package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/** Заголовок релиза 186 в фикстурах (`release_186_extended.json`/`discover_watching_page0.json`). */
private const val RELEASE_186_TITLE = "Темнее Черного"

/**
 * Запас на асинхронный путь «мутация → локальная БД → Flow → перекомпозиция»: сторы `:shared:database`
 * работают на своём IO-диспетчере, поэтому одного `waitForIdle()` для них недостаточно.
 */
private const val LIBRARY_AWAIT_TIMEOUT_MS = 5_000L

/**
 * Одна страница списка `profile/list/all/{status}/{page}` из фикстуры: без [releaseIds] список
 * пуст. `profile_list_status` намеренно не проставляется — вкладка обязана опираться на локальное
 * членство (сам эндпоинт и говорит, в каком списке лежит элемент), а не на поле DTO.
 */
private fun listPage(vararg releaseIds: Int): String {
    val content =
        releaseIds.joinToString(",") { id ->
            """{"id":$id,"title_ru":"$RELEASE_186_TITLE","episodes_total":12}"""
        }
    return """{"code":0,"content":[$content],"current_page":0,"total_page_count":1}"""
}

/**
 * P11.T3 — добавление в список и реактивное перекладывание карточки между вкладками «Мои списки».
 *
 * Общая фикстура двух последних сценариев: на сервере список «Смотрю» СОДЕРЖИТ релиз 186, а список
 * «В планах» пуст, и перезапрашивать их никто не будет. То есть серверные страницы заведомо
 * устарели относительно действия пользователя — и тест проверяет ровно то, что вкладки живут на
 * локальной БД (см. KDoc `LibraryViewModel`), а не на последнем ответе сервера. Без этого релиз
 * оставался бы висеть в «Смотрю» и не появлялся в «В планах» до ручного перезахода на экран —
 * тот самый баг «меняю список у карточки, а он нигде не меняется».
 *
 * Сетевых маршрутов сверх [defaultFixtureRoutes] нужно всего два (страницы двух списков): сами
 * мутации оптимистичны и локальны, фоновая синхронизация в [fakeInfraModule] — no-op.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryAddSmokeTest {
    private val libraryRoutes: Map<String, () -> String> =
        mapOf(
            "profile/list/all/1/0" to { listPage(186) },
            "profile/list/all/2/0" to { listPage() },
        )

    /**
     * Домашний экран → карточка релиза 186 → кнопка "Add to list" в
     * [com.aniko.app.feature.release.ReleaseHeaderSection] (`HeroAddToListButton`, phone Compact —
     * `DropdownMenu` со всеми `ListStatus`, `onChangeListStatus`) → оптимистичная запись
     * (`ReleaseDetailsViewModel.changeListStatus`, D4-паттерн: пишет в фейковую in-memory БД сразу,
     * фоновая синхронизация — no-op в [fakeInfraModule]) → лейбл кнопки меняется на выбранный статус.
     *
     * Track A (design-match-remaining-screens, 2026-09-04): на Compact-раскладке одиночный ряд чипов
     * `ChipRow` над `ListStatus.entries` заменён на кнопку с выпадающим меню (макет Claude Design,
     * `selectedAddListLabel ⌄`) — тест обновлён под новый виджет (был написан под старый `ChipRow`,
     * `assertIsSelected()` для `DropdownMenuItem` не применим, семантика `selected` там не выставлена).
     *
     * Сетевых маршрутов сверх [defaultFixtureRoutes] не требуется — запись оптимистичная, локальная.
     */
    @Test
    fun addReleaseToWatchingList() {
        runAnikoSmokeTest(
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()

            openRelease186FromHome()

            // Релиз ещё не в списке — кнопка озвучивает себя как "Add to list" (см.
            // `Strings.titleDetailAddToList`), не как конкретный статус.
            onNodeWithContentDescription("Add to list")
                .performScrollTo()
                .performClick()

            // `DropdownMenuItem` рисует обычный `Text`, не переопределяет семантику вручную (в
            // отличие от кнопки-триггера/бывшего `ChipRow`) — ищем по видимому тексту пункта меню.
            // Просто `onNodeWithText("Watching")` неоднозначен: где-то в уже скомпонованном фоне
            // экрана (за модальным `DropdownMenu`) есть ещё один узел с тем же текстом без
            // clickable-семантики (не сам виджет статуса) — фильтруем по наличию клика, это и
            // есть сам пункт меню, а не случайное совпадение текста.
            onNode(hasText("Watching") and hasClickAction()).performClick()

            // После выбора кнопка озвучивает уже текущий статус вместо плейсхолдера.
            onNodeWithContentDescription("Watching").assertIsDisplayed()
        }
    }

    /**
     * Сценарий 1 симптома: смена списка НА КАРТОЧКЕ релиза видна на «Мои списки» без перезахода.
     *
     * Карточка 186 → «Plan to Watch» → переход на «Мои списки». Сервер по-прежнему отдаёт релиз в
     * «Смотрю» (и пустоту в «В планах»), но локальное членство уже говорит обратное: во вкладке
     * «Смотрю» карточки быть НЕ должно, во вкладке «В планах» она обязана появиться.
     */
    @Test
    fun statusChangedOnReleaseCard_movesCardBetweenLibraryTabs() {
        runAnikoSmokeTest(
            apiRoutes = libraryRoutes,
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            openRelease186FromHome()

            onNodeWithContentDescription("Add to list").performScrollTo().performClick()
            onNode(hasText("Plan to Watch") and hasClickAction()).performClick()
            onNodeWithContentDescription("Plan to Watch").assertIsDisplayed()

            openLibrary()

            // «Смотрю» — вкладка по умолчанию; серверная страница принесла сюда релиз, локальное
            // членство его отсюда убирает.
            assertReleaseRowAbsent()

            selectLibraryTab("Plan to Watch")
            assertReleaseRowDisplayed()
        }
    }

    /**
     * Сценарий 2 симптома: смена списка ВНУТРИ экрана «Мои списки».
     *
     * Долгое нажатие на строке во вкладке «Смотрю» → «Move to “Plan to Watch”»: карточка обязана
     * исчезнуть из текущей вкладки сразу и оказаться в целевой, хотя серверная страница целевой
     * вкладки пуста и никто её не перезапрашивал.
     */
    @Test
    fun statusChangedInsideLibrary_movesCardBetweenTabs() {
        runAnikoSmokeTest(
            apiRoutes = libraryRoutes,
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            openLibrary()
            assertReleaseRowDisplayed()

            // Compact-раскладка рисует элементы списка как `ProgressRow` с `combinedClickable`;
            // долгое нажатие раскрывает `LibraryContextMenu` (см. `LibraryScreen.kt`). Именно
            // `performTouchInput`, а не семантическое действие: `ProgressRow` закрывает свою
            // семантику `clearAndSetSemantics` (см. её KDoc), инжект реального касания это не
            // задевает.
            releaseRow().performTouchInput { longClick() }
            onNode(hasText("Move to “Plan to Watch”") and hasClickAction()).performClick()

            assertReleaseRowAbsent()

            selectLibraryTab("Plan to Watch")
            assertReleaseRowDisplayed()
        }
    }
}

// ---- Шаги, общие для сценариев -------------------------------------------------------------

/**
 * Фаза 11, T9 (устройство): `AnixPoster` озвучивает заголовок через
 * `clearAndSetSemantics{contentDescription=...}` (иначе TalkBack фокусировал её без имени) — поиск
 * по видимому `Text` этот узел не находит, только по `contentDescription`.
 */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.openRelease186FromHome() {
    onNodeWithContentDescription(RELEASE_186_TITLE, substring = true)
        .performScrollTo()
        .performClick()
    onNodeWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).assertIsDisplayed()
}

/** Нижняя навигация видна на всех маршрутах, кроме плеера (см. `AdaptiveScaffold.showNavigationChrome`). */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.openLibrary() {
    onNodeWithTag(AnixTestTags.bottomNavItem("Library")).performClick()
    onNodeWithTag(AnixTestTags.LIBRARY_SCREEN_ROOT).assertIsDisplayed()
}

/**
 * Чип-вкладка «Мои списки» ([com.aniko.ui.component.ChipRow]). Ищется по `contentDescription`, а не
 * по тексту: `ChipRow` переустанавливает семантику чипа через `clearAndSetSemantics` (см. её KDoc),
 * поэтому подпись `Text` в дереве семантики не видна.
 */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.selectLibraryTab(label: String) {
    onNodeWithContentDescription(label).performClick()
}

/**
 * Строка релиза в списке. `ProgressRow` сливает семантику потомков в один узел с
 * `contentDescription = "<заголовок>, N of M"` (см. её KDoc), поэтому строка ищется по подстроке
 * заголовка, а не по `Text`.
 */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.releaseRow() = onNodeWithContentDescription(RELEASE_186_TITLE, substring = true)

/** Дожидается появления/исчезновения строки релиза — путь «мутация → БД → Flow → UI» асинхронен. */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.awaitReleaseRow(present: Boolean) {
    waitUntil(timeoutMillis = LIBRARY_AWAIT_TIMEOUT_MS) {
        onAllNodesWithContentDescription(RELEASE_186_TITLE, substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty() == present
    }
}

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.assertReleaseRowDisplayed() {
    awaitReleaseRow(present = true)
    releaseRow().assertIsDisplayed()
}

/**
 * Карточки на вкладке нет. Проверяется и пустое состояние вкладки — чтобы отличить «список пуст»
 * от «экран ещё грузится» или «экран показывает ошибку».
 */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.assertReleaseRowAbsent() {
    awaitReleaseRow(present = false)
    onNodeWithText("List is empty").assertIsDisplayed()
}
