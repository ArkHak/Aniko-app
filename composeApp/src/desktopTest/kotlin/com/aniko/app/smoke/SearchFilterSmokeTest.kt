package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/**
 * P11.T4 — поиск + фильтр.
 *
 * Нижняя навигация → «Search» ([AnixTestTags.SEARCH_SCREEN_ROOT], он же Catalog) → ввод запроса в
 * поле поиска (`strings.searchPlaceholder` как маркер поля) → результат из
 * `search/releases/0` ([com.aniko.app.smoke.fixtures.ApiFixtures.searchReleasesPage0NoApiVersionHeader],
 * первый тайтл — «Наруто») → переключение вкладки каталога «New Arrivals» (`catalogTabNew`),
 * которая гоняет `filter/0` ([com.aniko.app.smoke.fixtures.ApiFixtures.filterPage0], первый тайтл
 * — «Копэн») с другим сортом — проверяет, что фильтр-запрос отрабатывает и список обновляется.
 */
@OptIn(ExperimentalTestApi::class)
class SearchFilterSmokeTest {
    @Test
    fun searchThenSwitchFilterTab() {
        runAnikoSmokeTest(
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            onNodeWithTag(AnixTestTags.bottomNavItem("Search")).performClick()
            onNodeWithTag(AnixTestTags.SEARCH_SCREEN_ROOT).assertIsDisplayed()

            val queryField = onNode(hasSetTextAction())
            queryField.performTextInput("Наруто")

            onNodeWithText("Наруто", substring = true).assertIsDisplayed()

            // Очищаем запрос перед переключением вкладки — поле запроса и вкладки Все/Новинки
            // сосуществуют в одном `CatalogBody` (см. KDoc теста), непустой `query` иначе
            // продолжал бы держать результаты поиска поверх вкладочного листинга.
            // `performTextClearance()` вместо клика по иконке "✕": та же самая операция
            // (`onQueryChange("")`), но без завязки на видимость/поиск хрупкого однознакового
            // текстового узла иконки, из-за которой тест был флаки под нагрузкой всего набора.
            queryField.performTextClearance()

            // Фаза 11, T9 (устройство): ChipRow/AnixPoster теперь озвучивают подпись через
            // clearAndSetSemantics{contentDescription=...}, видимый Text больше не находится.
            onNodeWithContentDescription("New Arrivals").performClick()

            onNodeWithContentDescription("Копэн", substring = true).assertIsDisplayed()
        }
    }
}
