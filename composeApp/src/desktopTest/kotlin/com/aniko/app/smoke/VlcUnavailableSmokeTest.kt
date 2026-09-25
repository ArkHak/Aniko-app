package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aniko.app.smoke.fixtures.ApiFixtures
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/**
 * Issue #108 — на Mac нет VLC: экран плеера объясняет причину и предлагает «Скачать VLC», а не падает.
 *
 * Тот же путь Browse → Detail → Play, что в [BrowseDetailPlaySmokeTest], но под системным свойством
 * `aniko.playerTestVlcUnavailable=true`: в тестовом режиме настоящий vlcj-плеер не создаётся, а
 * `EmbedPlayerView` (desktop) вместо этого сообщает `EmbedVideoState.engineProblem` — ровно так же,
 * как при реальном сбое `CallbackMediaPlayerComponent()`.
 */
@OptIn(ExperimentalTestApi::class)
class VlcUnavailableSmokeTest {
    @Test
    fun playerExplainsThatVlcIsRequired() {
        val playerChainRoutes =
            mapOf(
                "episode/186/17" to { """{"code":0,"sources":[{"id":8,"name":"Kodik","episodes_count":12}]}""" },
                "episode/186/17/8" to { ApiFixtures.episodeList1868Kodik },
                "episode/target/186/8/1" to { ApiFixtures.episodeTarget186Kodik },
            )

        System.setProperty(VLC_UNAVAILABLE_PROPERTY, "true")
        try {
            runAnikoSmokeTest(
                apiRoutes = playerChainRoutes,
                initialToken = "fake-token",
                koinDeclaration = forceEnglishLocale(),
            ) {
                onNodeWithContentDescription("Темнее Черного", substring = true)
                    .performScrollTo()
                    .performClick()
                onNodeWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).assertIsDisplayed()
                onNodeWithContentDescription("Watch").performClick()

                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag(AnixTestTags.PLAYER_SCREEN_ROOT).fetchSemanticsNodes().isNotEmpty()
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText(VLC_MESSAGE_FRAGMENT, substring = true).fetchSemanticsNodes().isNotEmpty()
                }

                // assertExists, а не assertIsDisplayed: область видео в тестовом окне может быть нулевой высоты.
                onNodeWithText(VLC_MESSAGE_FRAGMENT, substring = true).assertExists()
                onNodeWithContentDescription("Download VLC").assertExists()
            }
        } finally {
            System.clearProperty(VLC_UNAVAILABLE_PROPERTY)
        }
    }

    private companion object {
        const val VLC_UNAVAILABLE_PROPERTY = "aniko.playerTestVlcUnavailable"
        const val VLC_MESSAGE_FRAGMENT = "needs VLC"
    }
}
