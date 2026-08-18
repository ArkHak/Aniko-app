package com.aniko.data.mapper

import com.aniko.data.dto.EpisodeSourceDto
import com.aniko.data.dto.EpisodeTargetDto
import com.aniko.data.dto.EpisodeTypeDto
import com.aniko.data.dto.TypesResponseDto
import com.aniko.model.VideoHost
import com.aniko.network.AnixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Регрессионный тест на живую верификацию (R3, `episode/186`): реальный `TypesResponse` отдаёт
 * `workers` строкой (`"Ancord"` либо `""`), а не массивом. До фикса `EpisodeTypeDto.workers:
 * List<String>` ронял бы десериализацию на каждом релизе — `ignoreUnknownKeys` не спасает от
 * несовпадения типа известного поля. Сэмплы см. `docs/api/samples/episode_types_186.json`.
 */
class EpisodeMapperTest {
    @Test
    fun typesResponse_decodesWorkersAsString_notArray() {
        // Сокращённый реальный ответ `GET episode/186` (см. docs/api/samples/episode_types_186.json).
        val json =
            """
            {
                "code": 0,
                "types": [
                    {
                        "id": 17,
                        "name": "SHIZA Project",
                        "workers": null,
                        "episodes_count": 16
                    },
                    {
                        "id": 1,
                        "name": "AniDUB",
                        "workers": "Ancord",
                        "episodes_count": 12
                    },
                    {
                        "id": 12,
                        "name": "AniMedia",
                        "workers": "",
                        "episodes_count": 12
                    }
                ]
            }
            """.trimIndent()

        val response = AnixJson.decodeFromString(TypesResponseDto.serializer(), json)
        assertEquals(3, response.types.size)

        val domain = response.types.map { it.toDomain() }
        assertEquals(null, domain[0].workers)
        assertEquals("Ancord", domain[1].workers)
        assertEquals("", domain[2].workers)
    }

    @Test
    fun episodeTypeDto_toDomain_mapsFieldsThrough() {
        val dto = EpisodeTypeDto(id = 1, name = "AniDUB", episodesCount = 12, workers = "Ancord")
        val domain = dto.toDomain()

        assertEquals(1, domain.id)
        assertEquals("AniDUB", domain.name)
        assertEquals(12, domain.episodesCount)
        assertEquals("Ancord", domain.workers)
    }

    /**
     * Живая верификация (P8.T6, `GET https://api-s.anixsekai.com/episode/1`, без токена):
     * `is_sub`/`view_count`/`pinned` реально присутствуют в сыром ответе — не выдумка под мокап.
     * Сэмпл — сокращённый реальный ответ (три типа: дубляж, субтитры, ещё один дубляж).
     */
    @Test
    fun typesResponse_decodesIsSubViewCountAndPinned() {
        val json =
            """
            {
                "code": 0,
                "types": [
                    {
                        "id": 1,
                        "name": "AniDUB",
                        "workers": "Ancord",
                        "is_sub": false,
                        "episodes_count": 104,
                        "view_count": 51287,
                        "pinned": false
                    },
                    {
                        "id": 24,
                        "name": "Субтитры",
                        "workers": null,
                        "is_sub": true,
                        "episodes_count": 104,
                        "view_count": 5971,
                        "pinned": false
                    }
                ]
            }
            """.trimIndent()

        val domain = AnixJson.decodeFromString(TypesResponseDto.serializer(), json).types.map { it.toDomain() }

        assertFalse(domain[0].isSub)
        assertEquals(51287, domain[0].viewCount)
        assertFalse(domain[0].pinned)
        assertTrue(domain[1].isSub)
        assertEquals(5971, domain[1].viewCount)
    }

    /** Поля `is_sub`/`view_count`/`pinned` не всегда есть в ответе — дефолты не должны падать. */
    @Test
    fun episodeTypeDto_defaultsMissingIsSubViewCountPinned() {
        val domain = EpisodeTypeDto(id = 1, name = "AniDUB").toDomain()

        assertFalse(domain.isSub)
        assertEquals(null, domain.viewCount)
        assertFalse(domain.pinned)
    }

    /**
     * Живая верификация (R3, `episode/186/{typeId}`): `sources[].name` — чистый машинный ключ
     * («Kodik», «Sibnet»), не локализованное название. `source_key` в реальном ответе не
     * встречается — `resolveHost()` работает прямо по `name`.
     */
    @Test
    fun episodeSourceDto_resolvesHostDirectlyFromName() {
        val kodik = EpisodeSourceDto(id = 8, name = "Kodik", episodesCount = 12).toDomain()
        val sibnet = EpisodeSourceDto(id = 1, name = "Sibnet", episodesCount = 12).toDomain()
        val unknown = EpisodeSourceDto(id = 99, name = "SomeNewHost", episodesCount = 1).toDomain()

        assertEquals(VideoHost.KODIK, kodik.host)
        assertEquals(VideoHost.SIBNET, sibnet.host)
        assertEquals(VideoHost.UNKNOWN, unknown.host)
    }

    /**
     * Живая верификация (R3, `episode/target/186/{sourceId}/{position}`): сервер отдаёт явное
     * булево поле `iframe` (Kodik → `true`, Sibnet → `false`). Поле пробрасывается в домен, но
     * не используется для ветвления — см. `EpisodeRepository.resolvePlaybackSource`.
     */
    @Test
    fun episodeTargetDto_toDomain_carriesIframeFlagThrough() {
        val kodikTarget =
            EpisodeTargetDto(
                position = 1,
                name = "1 серия",
                url = "https://kodikplayer.com/seria/548657/xxx/720p",
                iframe = true,
            ).toDomain()
        val sibnetTarget =
            EpisodeTargetDto(
                position = 0,
                name = "1 серия",
                url = "https://video.sibnet.ru/shell.php?videoid=3205851",
                iframe = false,
            ).toDomain()

        assertTrue(kodikTarget.iframe)
        assertFalse(sibnetTarget.iframe)
        assertEquals("https://kodikplayer.com/seria/548657/xxx/720p", kodikTarget.url)
    }
}
