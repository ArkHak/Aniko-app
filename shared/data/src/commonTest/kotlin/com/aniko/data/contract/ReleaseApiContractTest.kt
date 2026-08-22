package com.aniko.data.contract

import com.aniko.data.api.ReleaseApi
import com.aniko.data.fixtures.ApiFixtures
import com.aniko.data.mapper.toDomain
import com.aniko.data.mapper.toReleaseDetails
import com.aniko.model.ReleaseStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Контрактные тесты `ReleaseApi` (P11.T1) — JSON → `MockEngine` → `ReleaseApi` → `ReleaseMapper` →
 * доменная модель, с конкретными `assertEquals` на реальные значения из фикстур.
 *
 * Покрывает 3 из 13 фикстур: `release186Extended`, `discoverInteresting`, `discoverWatchingPage0`.
 */
class ReleaseApiContractTest {
    @Test
    fun release_extended_mapsToReleaseDetailsWithRealValues() =
        runTest {
            val client = mockAnixClient("/release/186", ApiFixtures.release186Extended)
            val api = ReleaseApi(client)

            val dto = api.release(releaseId = 186, extendedMode = true)
            val details = requireNotNull(dto.release).toReleaseDetails()

            assertEquals(186, details.release.id)
            assertEquals("Темнее Черного: Близнецы и Падающая Звезда", details.release.title)
            assertEquals("Darker than Black: Ryuusei no Gemini", details.release.originalTitle)
            assertEquals(2009, details.release.year)
            assertEquals(12, details.release.episodesTotal)
            assertEquals(12, details.release.episodesReleased)
            assertEquals(ReleaseStatus.FINISHED, details.release.status) // status.name == "Вышел"
            assertEquals(
                listOf("тайна", "фантастика", "экшен", "супер сила"),
                details.release.genres,
            )

            assertEquals("Bones", details.studio)
            assertEquals("Япония", details.country)
            assertEquals("Окамура Тэнсай", details.director)
            assertEquals("оригинал", details.source)
            assertEquals("AleBaStr, mr. Well, Hollow", details.translators)
            assertEquals("Сериал", details.category) // category.name
            assertEquals("4", details.season) // Int -> String
            assertEquals("5", details.ageRating) // Int -> String
            assertEquals(24, details.duration)
            assertEquals(7, details.screenshotUrls.size)
            assertEquals(3, details.relatedReleases.size)
            assertEquals(15, details.recommendedReleases.size)
            assertEquals(25, details.recommendedReleases.first().id)
            assertEquals("Темнее чёрного", details.recommendedReleases.first().title)
            assertEquals(3, details.relatedCount)
            assertEquals(5, details.commentCount)

            assertEquals(listOf(51, 94, 464, 1583, 4418), details.voteCounts)
            assertEquals(6610, details.voteCount)
            assertNull(details.yourVote) // your_vote: null in fixture

            assertEquals(1739, details.communityLists.watching)
            assertEquals(9420, details.communityLists.plan)
            assertEquals(18116, details.communityLists.completed)
            assertEquals(826, details.communityLists.holdOn)
            assertEquals(648, details.communityLists.dropped)
            assertEquals(6695, details.communityLists.favorites)
            assertEquals(259, details.communityLists.collection)
        }

    @Test
    fun discoverInteresting_mapsToBannerListWithRealValues() =
        runTest {
            val client = mockAnixClient("/discover/interesting", ApiFixtures.discoverInteresting)
            val api = ReleaseApi(client)

            val page = api.discoverInteresting()

            assertEquals(0, page.code)
            assertEquals(24, page.content.size)

            val first = page.content.first().toDomain()
            assertEquals(8013036, first.id)
            assertEquals("Ты и я — полные противоположности 2", first.title)
            assertEquals(1, first.type)
            assertEquals(20756, first.releaseId) // action "20756" -> toIntOrNull()
            assertEquals(
                "https://s3.anixmirai.com/banner/4a96822a-c1ab-4e5d-aeb4-42d4b20aae02.webp",
                first.imageUrl,
            )
        }

    @Test
    fun discoverWatching_page0_mapsToPagedReleasesWithRealValues() =
        runTest {
            val client = mockAnixClient("/discover/watching/0", ApiFixtures.discoverWatchingPage0)
            val api = ReleaseApi(client)

            val page = api.discoverWatching(page = 0)

            assertEquals(0, page.code)
            assertEquals(0, page.currentPage)
            assertEquals(0, page.totalPageCount)
            assertEquals(88, page.totalCount)
            assertEquals(25, page.content.size)

            val first = page.content.first().toDomain()
            assertEquals(186, first.id)
            assertEquals("Темнее Черного: Близнецы и Падающая Звезда", first.title)
            assertEquals(4.546596066, first.grade)
            assertEquals(ReleaseStatus.FINISHED, first.status) // status.name == "Вышел"
            assertEquals(12, first.episodesTotal)
            assertEquals(12, first.episodesReleased)
        }
}
