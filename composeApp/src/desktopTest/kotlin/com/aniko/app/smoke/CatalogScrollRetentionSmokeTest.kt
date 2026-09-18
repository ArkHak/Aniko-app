package com.aniko.app.smoke

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.aniko.app.smoke.fixtures.ApiFixtures
import kotlin.test.Test

/**
 * Регрессионный тест на баг 2026-09-18 «каталог прыгает наверх при открытии карточки».
 *
 * Корень был в `ListDetailHost` (`composeApp/.../navigation/ListDetailHost.kt`): список рисовался
 * из ДВУХ разных позиций композиции (early-return ветка без ящика / первый ребёнок `Box` с
 * ящиком), поэтому открытие/закрытие правого ящика деталей на Expanded уничтожало всё поддерево
 * списка — включая `LazyVerticalGrid` со скроллом, — и создавало его заново с позиции 0
 * (`rememberSaveable` не спасал: при переносе между call site внутри одного реестра нода
 * unregister'ится, не дожидаясь `performSave()`). Фикс — единственный стабильный call site
 * `listPane()` + условный ящик вторым ребёнком того же `Box`.
 *
 * Сценарий (Expanded-окно, сайдбар): Catalog → скролл сетки до элемента №15 → клик по карточке
 * (открывается правый ящик деталей) → элемент №15 всё ещё на экране → закрытие ящика кнопкой
 * «Back» → элемент №15 по-прежнему на экране. До фикса обе проверки после клика падали — список
 * пересоздавался наверху. Живёт в `desktopTest`, а не `commonTest`, по той же причине, что и
 * остальные смоук-тесты (см. KDoc [runAnikoSmokeTest]).
 *
 * Фикстура каталога синтетическая ([catalogFixturePage]): боевой `filter_page0.json` содержит
 * ровно один тайтл, для скролла нужно несколько страниц ячеек. Поля — минимальное подмножество
 * `ReleaseDto` (все поля DTO с дефолтами, см. `shared:data`), `total_page_count = 1`, чтобы
 * пагинация не дёргала несуществующий `filter/1`.
 */
@OptIn(ExperimentalTestApi::class)
class CatalogScrollRetentionSmokeTest {
    @Test
    fun catalogKeepsScrollPositionWhenDetailDrawerOpensAndCloses() {
        val clickedItemId = FIXTURE_ID_BASE + SCROLL_TARGET_INDEX
        val routes =
            mapOf(
                "filter/0" to { catalogFixturePage(CATALOG_ITEM_COUNT) },
                // Ящик деталей должен открыться рабочим экраном (иначе на нём нет кнопки «Back»
                // для шага закрытия) — содержимое релиза на проверки не влияет. `id` внутри
                // фикстуры подменяется на id кликнутой карточки: `ReleaseRepository` кладёт
                // запись в БД под id ИЗ ОТВЕТА, а `observeRelease(releaseId)` слушает ключ
                // запрошенного id — при рассинхроне ящик остался бы на вечном спиннере.
                "release/$clickedItemId" to {
                    ApiFixtures.release186Extended.replaceFirst(
                        oldValue = "\"id\":186",
                        newValue = "\"id\":$clickedItemId",
                    )
                },
            )

        runAnikoSmokeTest(
            apiRoutes = routes,
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
            windowSize = EXPANDED_WINDOW_SIZE,
        ) {
            // На Expanded нижней навигации нет — переход по видимому лейблу пункта сайдбара.
            onAllNodesWithText("Catalog").onFirst().performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("$FIXTURE_TITLE_PREFIX 0", substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLL_TARGET_INDEX)
            onNodeWithContentDescription("$FIXTURE_TITLE_PREFIX $SCROLL_TARGET_INDEX", substring = true)
                .assertIsDisplayed()

            // Клик по карточке открывает правый ящик деталей поверх списка — скролл обязан
            // остаться на месте (до фикса список пересоздавался и прыгал наверх уже здесь).
            onNodeWithContentDescription("$FIXTURE_TITLE_PREFIX $SCROLL_TARGET_INDEX", substring = true)
                .performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("$FIXTURE_TITLE_PREFIX $SCROLL_TARGET_INDEX", substring = true)
                .assertIsDisplayed()

            // Закрытие ящика — второй триггер переключения веток в старом `ListDetailHost`.
            onNodeWithContentDescription("Back").performClick()
            onNodeWithContentDescription("$FIXTURE_TITLE_PREFIX $SCROLL_TARGET_INDEX", substring = true)
                .assertIsDisplayed()
        }
    }
}

private fun catalogFixturePage(itemCount: Int): String {
    val items =
        (0 until itemCount).joinToString(separator = ",") { index ->
            """{"id":${FIXTURE_ID_BASE + index},""" +
                """"title_ru":"$FIXTURE_TITLE_PREFIX $index",""" +
                """"image":"https://example.com/poster$index.jpg",""" +
                """"grade":4.5,"status":{"id":1,"name":"Вышел"},"episodes_released":12}"""
        }
    return """{"code":0,"content":[$items],"total_count":$itemCount,"total_page_count":1,"current_page":0}"""
}

private const val CATALOG_ITEM_COUNT = 30
private const val SCROLL_TARGET_INDEX = 15
private const val FIXTURE_ID_BASE = 30000
private const val FIXTURE_TITLE_PREFIX = "Фикстура"

/** Широкое окно (>840dp = `AnixWindowSize.Expanded`, см. `AnixWindowSize.kt`) — режим сайдбара
 *  и правого ящика деталей, в котором жил баг. */
private val EXPANDED_WINDOW_SIZE = Size(1400f, 900f)
