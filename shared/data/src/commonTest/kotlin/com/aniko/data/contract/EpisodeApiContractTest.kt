package com.aniko.data.contract

import com.aniko.data.api.EpisodeApi
import com.aniko.data.fixtures.ApiFixtures
import com.aniko.data.mapper.toDomain
import com.aniko.model.VideoHost
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Контрактные тесты `EpisodeApi` (P11.T1) — вся цепочка резолвинга плеера, один логический
 * тест-файл на 6 фикстур (`types` → `sources` → `episodes` → `target`), т.к. это один Api-класс
 * с одной сквозной цепочкой релиза 186.
 *
 * Покрывает 6 из 13 фикстур: `episodeTypes186`, `episodeSources186TypeId`,
 * `episodeList1861Sibnet`, `episodeList1868Kodik`, `episodeTarget186Kodik`,
 * `episodeTarget186Sibnet`.
 */
class EpisodeApiContractTest {
    @Test
    fun types_mapsToVoiceTypesWithRealValues() =
        runTest {
            val client = mockAnixClient("/episode/186", ApiFixtures.episodeTypes186)
            val api = EpisodeApi(client)

            val dto = api.types(releaseId = 186)
            val types = dto.types.map { it.toDomain() }

            assertEquals(3, types.size)

            val shiza = types[0]
            assertEquals(17, shiza.id)
            assertEquals("SHIZA Project", shiza.name)
            assertEquals(16, shiza.episodesCount)
            assertNull(shiza.workers)
            assertEquals(33743, shiza.viewCount)
            assertEquals(false, shiza.pinned)

            val anidub = types[1]
            assertEquals(1, anidub.id)
            assertEquals("AniDUB", anidub.name)
            assertEquals("Ancord", anidub.workers)
            assertEquals(12, anidub.episodesCount)
            assertEquals(15350, anidub.viewCount)

            val animedia = types[2]
            assertEquals(12, animedia.id)
            assertEquals("AniMedia", animedia.name)
            assertNull(animedia.workers)
            assertEquals(6674, animedia.viewCount)
        }

    @Test
    fun sources_mapsToEpisodeSourceWithResolvedHost() =
        runTest {
            val client = mockAnixClient("/episode/186/17", ApiFixtures.episodeSources186TypeId)
            val api = EpisodeApi(client)

            val dto = api.sources(releaseId = 186, typeId = 17)
            val sources = dto.sources.map { it.toDomain() }

            assertEquals(1, sources.size)
            val kodik = sources.single()
            assertEquals(17, kodik.id)
            assertEquals("Kodik", kodik.name)
            assertEquals(16, kodik.episodesCount)
            assertEquals(VideoHost.KODIK, kodik.host) // resolved via VideoHost.fromKey("Kodik")
        }

    @Test
    fun episodes_sibnetSource_mapsToTwelveEpisodesWithRealValues() =
        runTest {
            val client = mockAnixClient("/episode/186/17/1", ApiFixtures.episodeList1861Sibnet)
            val api = EpisodeApi(client)

            val dto = api.episodes(releaseId = 186, typeId = 17, sourceId = 1)
            val episodes = dto.episodes.map { it.toDomain() }

            assertEquals(12, episodes.size)
            val first = episodes.first()
            assertEquals(0, first.position)
            assertEquals("1 серия", first.name)
            assertEquals(false, first.isWatched)
        }

    @Test
    fun episodes_kodikSource_mapsToTwelveEpisodesWithRealValues() =
        runTest {
            val client = mockAnixClient("/episode/186/17/8", ApiFixtures.episodeList1868Kodik)
            val api = EpisodeApi(client)

            val dto = api.episodes(releaseId = 186, typeId = 17, sourceId = 8)
            val episodes = dto.episodes.map { it.toDomain() }

            assertEquals(12, episodes.size)
            val first = episodes.first()
            assertEquals(1, first.position)
            assertEquals("1 серия", first.name)

            val last = episodes.last()
            assertEquals(12, last.position)
            assertEquals("12 серия", last.name)
        }

    @Test
    fun target_kodik_resolvesToIframeUrl() =
        runTest {
            val client = mockAnixClient("/episode/target/186/8/1", ApiFixtures.episodeTarget186Kodik)
            val api = EpisodeApi(client)

            val dto = api.target(releaseId = 186, sourceId = 8, position = 1)
            val target = requireNotNull(dto.episode).toDomain()

            assertEquals(1, target.position)
            assertEquals("1 серия", target.name)
            assertEquals(true, target.iframe)
            assertEquals(
                "https://kodikplayer.com/seria/548657/6587525887f75a3d5f0c9cc227bfc24d/720p" +
                    "?d=2026080601&s=SYNTHETICSIGNATURESYNTHETICSIGNATURESYNTHETICSIGNATURESYNTHETICS&ip=203.0.113.10",
                target.url,
            )
            assertEquals(VideoHost.KODIK, VideoHost.fromUrl(target.url))
        }

    @Test
    fun target_sibnet_resolvesToNonIframeUrl() =
        runTest {
            val client = mockAnixClient("/episode/target/186/1/0", ApiFixtures.episodeTarget186Sibnet)
            val api = EpisodeApi(client)

            val dto = api.target(releaseId = 186, sourceId = 1, position = 0)
            val target = requireNotNull(dto.episode).toDomain()

            assertEquals(0, target.position)
            assertEquals("1 серия", target.name)
            assertEquals(false, target.iframe)
            assertEquals(
                "https://video.sibnet.ru/shell.php?videoid=3205851" +
                    "&d=2026080601&s=SYNTHETICSIGNATURESYNTHETICSIGNATURESYNTHETICSIGNATURESYNTHETICS&ip=203.0.113.10",
                target.url,
            )
            assertEquals(VideoHost.SIBNET, VideoHost.fromUrl(target.url))
        }
}
