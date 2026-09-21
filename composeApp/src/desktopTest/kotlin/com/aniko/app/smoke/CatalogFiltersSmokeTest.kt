package com.aniko.app.smoke

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.aniko.app.feature.search.CatalogTestTags
import com.aniko.app.navigation.DeepLinkDispatcher
import com.aniko.app.navigation.PendingCatalogFilterLink
import com.aniko.network.AnixJson
import com.aniko.ui.testing.AnixTestTags
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test

/**
 * Сквозные сценарии единой верхней панели Каталога (`CatalogToolbar`) поверх всего приложения и
 * фейковой сети: открыть меню чипа → выбрать → фильтр реально уехал в `POST filter/0` → сбросить.
 *
 * Тело каждого `filter/{page}`-запроса записывается [FilterRequestLog] — так «фильтр применился»
 * проверяется по факту отправки на сервер (`genres`/`status_id` в теле), а не по перекраске чипа.
 * Expanded — выпадающие меню (Esc / клик вне закрывает, «Жанры» остаётся открытым при выборе);
 * Compact — `ModalBottomSheet`. Живёт в `desktopTest` по той же причине, что и остальные смоуки
 * (см. KDoc [runAnikoSmokeTest]).
 */
@OptIn(ExperimentalTestApi::class)
class CatalogFiltersSmokeTest {
    @Test
    fun expandedGenresMenuStaysOpenAppliesFilterClosesOnOutsideClickAndResets() {
        val log = FilterRequestLog()
        runCatalog(log, EXPANDED_WINDOW) {
            openCatalogExpanded()

            onNodeWithTag(CatalogTestTags.GENRES_CHIP).performClick()
            onNodeWithTag(CatalogTestTags.MENU_GENRES).assertIsDisplayed()

            onNodeWithContentDescription("экшен").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { log.any { it.contains("\"genres\":[\"экшен\"]") } }
            onNodeWithContentDescription("экшен").assertIsSelected()

            // Множественный выбор: меню осталось открытым, второй жанр добавляется к первому.
            onNodeWithTag(CatalogTestTags.MENU_GENRES).assertIsDisplayed()
            onNodeWithContentDescription("драма").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { log.any { it.contains("\"genres\":[\"экшен\",\"драма\"]") } }
            waitUntil(timeoutMillis = WAIT_MS) { hasNode("Genres, 2 selected") }

            // Клик вне меню закрывает его.
            onNodeWithTag(AnixTestTags.SEARCH_SCREEN_ROOT).performTouchInput { click(Offset(OUTSIDE_CLICK_X, OUTSIDE_CLICK_Y)) }
            waitUntil(timeoutMillis = WAIT_MS) { noNodeWithTag(CatalogTestTags.MENU_GENRES) }

            // «Сбросить» снимает всё разом и сам исчезает.
            onNodeWithTag(CatalogTestTags.RESET).performClick()
            waitUntil(timeoutMillis = WAIT_MS) { log.last()?.contains("экшен") == false }
            waitUntil(timeoutMillis = WAIT_MS) { noNodeWithTag(CatalogTestTags.RESET) }
            onNodeWithContentDescription("Genres").assertIsDisplayed()
        }
    }

    /**
     * Esc закрывает выпадающее меню (штатное поведение `Popup` с `dismissOnBackPress`). Платформенная
     * конвертация Esc → back — код `ComposeSceneMediator` реального окна, в headless-харнесе её
     * эмулирует `pressBack` (тот же `NavigationEventDispatcher`, см. KDoc `SmokeBackNavigationInput`).
     */
    @Test
    fun expandedGenresMenuClosesOnEscape() {
        val log = FilterRequestLog()
        runCatalog(log, EXPANDED_WINDOW) { pressBack ->
            openCatalogExpanded()

            onNodeWithTag(CatalogTestTags.GENRES_CHIP).performClick()
            onNodeWithTag(CatalogTestTags.MENU_GENRES).assertIsDisplayed()

            pressBack()
            waitUntil(timeoutMillis = WAIT_MS) { noNodeWithTag(CatalogTestTags.MENU_GENRES) }
        }
    }

    @Test
    fun expandedStatusMenuIsSingleChoiceClosesAndClearsFromChip() {
        val log = FilterRequestLog()
        runCatalog(log, EXPANDED_WINDOW) {
            openCatalogExpanded()

            onNodeWithTag(CatalogTestTags.STATUS_CHIP).performClick()
            onNodeWithContentDescription("Ongoing").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { log.any { it.contains("\"status_id\":2") } }

            // Одиночный выбор: меню закрылось само, чип показывает выбор.
            waitUntil(timeoutMillis = WAIT_MS) { noNodeWithTag(CatalogTestTags.MENU_STATUS) }
            onNodeWithContentDescription("Status: Ongoing").assertIsDisplayed()

            // ✕ на чипе — быстрый сброс только статуса.
            onNodeWithTag(CatalogTestTags.STATUS_CLEAR).performClick()
            waitUntil(timeoutMillis = WAIT_MS) { log.last()?.contains("\"status_id\":2") == false }
            onNodeWithContentDescription("Status").assertIsDisplayed()
        }
    }

    @Test
    fun compactGenresSheetAppliesFilterAndKeepsSheetOpen() {
        val log = FilterRequestLog()
        runCatalog(log, COMPACT_WINDOW) {
            onNodeWithTag(AnixTestTags.bottomNavItem("Search")).performClick()
            waitUntil(timeoutMillis = WAIT_MS) { hasNode(FIXTURE_TITLE_PREFIX, substring = true) }

            onNodeWithTag(CatalogTestTags.GENRES_CHIP).performClick()
            onNodeWithTag(CatalogTestTags.MENU_GENRES).assertIsDisplayed()

            onNodeWithContentDescription("комедия").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { log.any { it.contains("\"genres\":[\"комедия\"]") } }
            onNodeWithTag(CatalogTestTags.MENU_GENRES).assertIsDisplayed()

            // «Сбросить» в шапке шторки снимает жанры, не закрывая шторку.
            onNodeWithText("Reset").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { log.last()?.contains("комедия") == false }
            onNodeWithTag(CatalogTestTags.MENU_GENRES).assertIsDisplayed()
        }
    }

    /**
     * Сквозной deep link: `aniko://catalog?...` → `DeepLinkDispatcher` → `PendingCatalogFilterLink` →
     * `SearchViewModel` → чипы и запрос `filter/0`. Индекс жанра `3` — «драма» (`AnixGenres.popular`).
     */
    @Test
    fun deepLinkFilterIsAppliedAndReflectedInChips() {
        val log = FilterRequestLog()
        try {
            runCatalog(log, EXPANDED_WINDOW) {
                DeepLinkDispatcher.dispatch("aniko://catalog?status=2&genres=3")

                waitUntil(timeoutMillis = WAIT_MS) { hasNode("Status: Ongoing") }
                waitUntil(timeoutMillis = WAIT_MS) { hasNode("Genres, 1 selected") }
                waitUntil(timeoutMillis = WAIT_MS) {
                    log.any { it.contains("\"status_id\":2") && it.contains("\"genres\":[\"драма\"]") }
                }
                onNodeWithTag(CatalogTestTags.RESET).assertIsDisplayed()
            }
        } finally {
            // Глобальные мосты deep link не должны переживать тест и влиять на соседние.
            DeepLinkDispatcher.consume()
            PendingCatalogFilterLink.consume()
        }
    }

    // ---- инфраструктура ---------------------------------------------------------------------------

    private fun runCatalog(
        log: FilterRequestLog,
        windowSize: Size,
        body: SkikoComposeUiTest.(pressBack: () -> Unit) -> Unit,
    ) {
        val routes = mapOf("filter/0" to { catalogPage(CATALOG_ITEM_COUNT) })
        runAnikoSmokeTest(
            apiRoutes = routes,
            initialToken = "fake-token",
            koinDeclaration = recordingDeclaration(log, routes),
            windowSize = windowSize,
            body = body,
        )
    }

    private fun SkikoComposeUiTest.openCatalogExpanded() {
        // На Expanded нижней навигации нет — переход по лейблу пункта сайдбара.
        onAllNodesWithText("Catalog").onFirst().performClick()
        waitUntil(timeoutMillis = WAIT_MS) { hasNode(FIXTURE_TITLE_PREFIX, substring = true) }
    }

    private fun SkikoComposeUiTest.hasNode(
        contentDescription: String,
        substring: Boolean = false,
    ): Boolean = onAllNodesWithContentDescription(contentDescription, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun SkikoComposeUiTest.noNodeWithTag(tag: String): Boolean = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()

    private companion object {
        const val WAIT_MS = 10_000L
        const val CATALOG_ITEM_COUNT = 12
        const val FIXTURE_TITLE_PREFIX = "Фикстура"
        const val OUTSIDE_CLICK_X = 4f
        const val OUTSIDE_CLICK_Y = 4f
        val EXPANDED_WINDOW = Size(1400f, 900f)
        val COMPACT_WINDOW = Size(390f, 844f)
    }
}

/** Тела всех `POST filter/{page}` в порядке отправки (движок пишет из своего потока — отсюда CopyOnWrite). */
private class FilterRequestLog {
    private val bodies = CopyOnWriteArrayList<String>()

    fun add(body: String) {
        bodies += body
    }

    fun any(predicate: (String) -> Boolean): Boolean = bodies.any(predicate)

    fun last(): String? = bodies.lastOrNull()
}

/**
 * Подменяет `HttpClient` фейкового графа движком, который дополнительно пишет тела запросов
 * `filter/{page}` в [log] (базовый `fakeApiEngine` запросы не запоминает) + принудительно
 * включает английскую локаль (см. [forceEnglishLocale]).
 */
private fun recordingDeclaration(
    log: FilterRequestLog,
    routes: Map<String, () -> String>,
): KoinAppDeclaration =
    {
        forceEnglishLocale().invoke(this)
        modules(
            module {
                single<HttpClient> {
                    val engine =
                        MockEngine { request ->
                            val path = request.url.encodedPath.removePrefix("/")
                            if (path.startsWith("filter/")) {
                                log.add((request.body as? TextContent)?.text.orEmpty())
                            }
                            val body = (defaultFixtureRoutes + routes)[path]?.invoke()
                            if (body != null) {
                                respond(
                                    content = body,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            } else {
                                respond(
                                    content = """{"code":-1}""",
                                    status = HttpStatusCode.NotFound,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        }
                    HttpClient(engine) {
                        expectSuccess = true
                        install(ContentNegotiation) { json(AnixJson) }
                    }
                }
            },
        )
    }

private const val FIXTURE_ID_BASE = 40000

/**
 * Синтетическая страница выдачи: минимальное подмножество `ReleaseDto`, `total_page_count = 1` —
 * пагинация не дёргает несуществующий `filter/1`. Названия «Фикстура N» — маркер загрузки списка.
 */
private fun catalogPage(itemCount: Int): String {
    val items =
        (0 until itemCount).joinToString(separator = ",") { index ->
            """{"id":${FIXTURE_ID_BASE + index},"title_ru":"Фикстура $index",""" +
                """"image":"https://example.com/poster$index.jpg","grade":4.5,""" +
                """"status":{"id":1,"name":"Вышел"},"episodes_released":12}"""
        }
    return """{"code":0,"content":[$items],"total_count":$itemCount,"total_page_count":1,"current_page":0}"""
}
