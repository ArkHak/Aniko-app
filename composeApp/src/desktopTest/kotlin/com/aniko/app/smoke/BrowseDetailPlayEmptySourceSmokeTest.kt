package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aniko.app.smoke.fixtures.ApiFixtures
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/**
 * Регрессия (живая находка 2026-09-28, релиз 74 «Монстр»): у выбранной озвучки ПЕРВЫМ в списке
 * стоял опустевший источник (`episodes_count: 0`, пустой ответ `episodes`) при живом следующем —
 * `ReleaseDetailsViewModel.resolvePlayTargetChain` слепо брал `sources.firstOrNull()`, получал
 * пустой список серий и возвращал `null`, и кнопка "Watch" молча не открывала плеер ни на одной
 * платформе. Цепочка обязана перебрать кандидатов до первого источника с непустыми сериями.
 *
 * Сценарий повторяет [BrowseDetailPlaySmokeTest] (те же реальные фикстуры релиза 186 и хвоста
 * цепочки Kodik), отличие одно: сконструированный ответ `episode/186/17` ставит первым пустой
 * Sibnet (`id=1`, `episodes_count=0`), а рабочий Kodik (`id=8`) — вторым. Маршрут
 * `episode/186/17/1` (серии пустого источника) оставлен на случай, если кандидаты переупорядочатся:
 * цепочка не должна на нём останавливаться, даже если запросит его.
 */
@OptIn(ExperimentalTestApi::class)
class BrowseDetailPlayEmptySourceSmokeTest {
    @Test
    fun watchFallsThroughEmptySource() {
        val playerChainRoutes =
            mapOf(
                "episode/186/17" to {
                    """{"code":0,"sources":[""" +
                        """{"id":1,"name":"Sibnet","episodes_count":0},""" +
                        """{"id":8,"name":"Kodik","episodes_count":12}]}"""
                },
                "episode/186/17/1" to { """{"code":0,"episodes":[]}""" },
                "episode/186/17/8" to { ApiFixtures.episodeList1868Kodik },
                "episode/target/186/8/1" to { ApiFixtures.episodeTarget186Kodik },
            )

        runAnikoSmokeTest(
            apiRoutes = playerChainRoutes,
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()

            onNodeWithContentDescription("Темнее Черного", substring = true)
                .performScrollTo()
                .performClick()

            onNodeWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).assertIsDisplayed()

            onNodeWithContentDescription("Watch").performClick()

            // До фикса плеер здесь не появлялся вовсе: цепочка молча возвращала null.
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTag(AnixTestTags.PLAYER_SCREEN_ROOT).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(AnixTestTags.PLAYER_SCREEN_ROOT).assertIsDisplayed()
        }
    }
}
