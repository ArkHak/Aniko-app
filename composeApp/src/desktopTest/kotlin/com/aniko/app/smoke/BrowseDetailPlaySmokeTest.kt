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
 * P11.T2 — Browse → Detail → Play.
 *
 * Домашний экран (раздел «Continue Watching», `discover/watching/0`) → карточка релиза 186
 * (`release/186`, [ApiFixtures.release186Extended]) → кнопка "Watch" (резолвит всю цепочку плеера
 * types → sources → episodes сама, см. `ReleaseDetailsViewModel.resolvePlayTargetChain`) →
 * [AnixTestTags.PLAYER_SCREEN_ROOT].
 *
 * Цепочка плеера НЕ полностью реальная фикстура: `episode/186` (типы озвучки) — реальный
 * [ApiFixtures.episodeTypes186] (уже в `defaultFixtureRoutes`). Кнопка "Watch"
 * (`ReleaseDetailsViewModel.resolvePlayTargetChain`) берёт `types.firstOrNull()` — это ПЕРВАЯ
 * запись массива `episode/186`, `{"@id":1,"id":17,"name":"SHIZA Project",...}`, то есть typeId=17,
 * а НЕ id=1/AniDUB (тот идёт вторым в массиве) — легко перепутать по имени файла
 * `episode_sources_186_typeId.json`, которое ничего не говорит про порядок. `episode/186/17`
 * (источники для typeId=17) — сконструирован вручную: реального сэмпла именно под этот typeId в
 * `docs/api/samples` нет (`episode_sources_186_typeId.json` снят под другой typeId, с другим
 * sourceId). Источник с `id=8`/name="Kodik" выбран НЕ произвольно — он ровно совпадает с
 * `sourceId=8`, которым реально помечены [ApiFixtures.episodeList1868Kodik] и
 * [ApiFixtures.episodeTarget186Kodik] (`position=1`), поэтому весь хвост цепочки — подлинные
 * ответы сервера, синтетический только список источников озвучки.
 */
@OptIn(ExperimentalTestApi::class)
class BrowseDetailPlaySmokeTest {
    @Test
    fun browseDetailPlay() {
        val playerChainRoutes =
            mapOf(
                "episode/186/17" to { """{"code":0,"sources":[{"id":8,"name":"Kodik","episodes_count":12}]}""" },
                "episode/186/17/8" to { ApiFixtures.episodeList1868Kodik },
                "episode/target/186/8/1" to { ApiFixtures.episodeTarget186Kodik },
            )

        runAnikoSmokeTest(
            apiRoutes = playerChainRoutes,
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()

            // Фаза 11, T9 (устройство): AnixPoster теперь озвучивает заголовок через
            // clearAndSetSemantics{contentDescription=...} (найдено на устройстве — иначе
            // TalkBack фокусировал кликабельную карточку без имени), поэтому поиск по
            // видимому Text больше не находит узел — только по contentDescription.
            onNodeWithContentDescription("Темнее Черного", substring = true)
                .performScrollTo()
                .performClick()

            onNodeWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).assertIsDisplayed()

            onNodeWithContentDescription("Watch").performClick()

            // `resolvePlayTarget()` идёт в отдельной корутине (`scope.launch` в
            // `ReleaseHeaderSection.onWatchClick`) — клик синхронно её только запускает, сама
            // цепочка types→sources→episodes (даже на MockEngine) не гарантированно успевает
            // отработать в кадре клика.
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTag(AnixTestTags.PLAYER_SCREEN_ROOT).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(AnixTestTags.PLAYER_SCREEN_ROOT).assertIsDisplayed()
        }
    }
}
