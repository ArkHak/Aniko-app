package com.aniko.data.mapper

import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseDto
import com.aniko.data.dto.ReleaseStreamingPlatformDto
import com.aniko.network.AnixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Регрессионный тест на живую верификацию легальных стриминг-площадок (2026-09-23, curl
 * `https://api-s.anixsekai.com/...`, НЕ из РФ — см. KDoc [ReleaseStreamingPlatformDto]):
 * `GET release/streaming/platform/{releaseId}` — обычная `PageableResponseDto`, ровно как у
 * комментариев ([ReleaseCommentMapperTest]).
 */
class ReleaseStreamingPlatformMapperTest {
    @Test
    fun pageableResponse_decodesSinglePlatform() {
        // GET release/streaming/platform/20257
        val json =
            """
            {
                "code": 0,
                "content": [
                    {
                        "id": 1,
                        "name": "Кинопоиск",
                        "icon": "https://s3.anixmirai.com/streamings/kinopoisk.jpg",
                        "url": "https://hd.kinopoisk.ru/film/4be416847fb596e59ecc9dfee0918fa2?content_tab=series",
                        "position": 1
                    }
                ],
                "total_count": 1,
                "total_page_count": 0,
                "current_page": 0
            }
            """.trimIndent()
        val serializer = PageableResponseDto.serializer(ReleaseStreamingPlatformDto.serializer())

        val response = AnixJson.decodeFromString(serializer, json)

        assertEquals(0, response.code)
        assertEquals(1, response.content.size)
        val dto = response.content.single()
        assertEquals(1L, dto.id)
        assertEquals("Кинопоиск", dto.name)
        assertEquals("https://s3.anixmirai.com/streamings/kinopoisk.jpg", dto.icon)
        assertEquals(1, dto.position)
    }

    @Test
    fun pageableResponse_decodesMultiplePlatformsOrderedByPosition() {
        // GET release/streaming/platform/19346
        val json =
            """
            {
                "code": 0,
                "content": [
                    {
                        "id": 1,
                        "name": "Кинопоиск",
                        "icon": "https://s3.anixmirai.com/streamings/kinopoisk.jpg",
                        "url": "https://hd.kinopoisk.ru/film/x",
                        "position": 1
                    },
                    {
                        "id": 5,
                        "name": "Иви",
                        "icon": "https://s3.anixmirai.com/streamings/ivi.jpg",
                        "url": "https://www.ivi.ru/watch/y",
                        "position": 2
                    }
                ],
                "total_count": 2,
                "total_page_count": 0,
                "current_page": 0
            }
            """.trimIndent()
        val serializer = PageableResponseDto.serializer(ReleaseStreamingPlatformDto.serializer())

        val response = AnixJson.decodeFromString(serializer, json)
        val domain = response.content.map { it.toDomain() }

        assertEquals(2, domain.size)
        assertEquals("Кинопоиск", domain[0].name)
        assertEquals("Иви", domain[1].name)
        assertEquals(1L, domain[0].id)
        assertEquals(5L, domain[1].id)
        assertEquals("https://www.ivi.ru/watch/y", domain[1].url)
    }

    @Test
    fun pageableResponse_decodesEmptyContentWithoutError() {
        // GET release/streaming/platform/20223 — релиз без легальных площадок, не ошибка.
        val json =
            """
            {"code":0,"content":[],"total_count":0,"total_page_count":0,"current_page":0}
            """.trimIndent()
        val serializer = PageableResponseDto.serializer(ReleaseStreamingPlatformDto.serializer())

        val response = AnixJson.decodeFromString(serializer, json)

        assertTrue(response.content.isEmpty())
        assertEquals(0, response.code)
    }

    @Test
    fun releaseStreamingPlatformDto_toDomain_mapsFieldsThrough() {
        val dto =
            ReleaseStreamingPlatformDto(
                id = 1,
                name = "Кинопоиск",
                icon = "https://s3.anixmirai.com/streamings/kinopoisk.jpg",
                url = "https://hd.kinopoisk.ru/film/x",
                position = 1,
            )

        val domain = dto.toDomain()

        assertEquals(1L, domain.id)
        assertEquals("Кинопоиск", domain.name)
        assertEquals("https://s3.anixmirai.com/streamings/kinopoisk.jpg", domain.iconUrl)
        assertEquals("https://hd.kinopoisk.ru/film/x", domain.url)
    }

    /**
     * `is_third_party_platforms_disabled` отсутствовал в живых ответах 2026-09-23 (не `false`) —
     * маппер обязан схлопнуть `null` к `false`, а не упасть/протащить `null` в домен (см. KDoc
     * [ReleaseDto]/`ReleaseDetails.isThirdPartyPlatformsDisabled`).
     */
    @Test
    fun releaseDto_toReleaseDetails_defaultsThirdPartyFlagToFalseWhenFieldMissing() {
        val json =
            """
            {"id": 20257, "note": "Данный материал лицензирован на территории вашей страны."}
            """.trimIndent()
        val dto = AnixJson.decodeFromString(ReleaseDto.serializer(), json)

        val details = dto.toReleaseDetails()

        assertFalse(details.isThirdPartyPlatformsDisabled)
        assertEquals("Данный материал лицензирован на территории вашей страны.", details.note)
        assertNull(details.noteBackgroundColorLight)
        assertNull(details.noteBackgroundColorDark)
    }

    @Test
    fun releaseDto_toReleaseDetails_mapsThirdPartyFlagAndNoteColorsWhenPresent() {
        val json =
            """
            {
                "id": 1,
                "note": "Материал лицензирован.",
                "note_background_color_light": "#FFFFFF",
                "note_background_color_dark": "#000000",
                "note_text_color_light": "#111111",
                "note_text_color_dark": "#EEEEEE",
                "is_third_party_platforms_disabled": true
            }
            """.trimIndent()
        val dto = AnixJson.decodeFromString(ReleaseDto.serializer(), json)

        val details = dto.toReleaseDetails()

        assertTrue(details.isThirdPartyPlatformsDisabled)
        assertEquals("#FFFFFF", details.noteBackgroundColorLight)
        assertEquals("#000000", details.noteBackgroundColorDark)
        assertEquals("#111111", details.noteTextColorLight)
        assertEquals("#EEEEEE", details.noteTextColorDark)
    }
}
