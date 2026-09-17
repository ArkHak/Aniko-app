package com.aniko.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Живая регрессия 2026-09-17 (релиз id 20236, extended_mode): `episode_last_update` пришёл
 * объектом `{"last_episode_update_date": ...}` и ронял парсинг всей расширенной карточки —
 * см. KDoc [EpisodeLastUpdateSerializer].
 */
class EpisodeLastUpdateSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun numericForm_parsesAsLong() {
        val dto = json.decodeFromString<ReleaseDto>("""{"episode_last_update":1758026400}""")
        assertEquals(1_758_026_400L, dto.episodeLastUpdate)
    }

    @Test
    fun objectForm_parsesNestedDate() {
        val dto =
            json.decodeFromString<ReleaseDto>(
                """{"episode_last_update":{"last_episode_update_date":1758026400}}""",
            )
        assertEquals(1_758_026_400L, dto.episodeLastUpdate)
    }

    @Test
    fun quotedNumericForm_parsesAsLong() {
        val dto = json.decodeFromString<ReleaseDto>("""{"episode_last_update":"1758026400"}""")
        assertEquals(1_758_026_400L, dto.episodeLastUpdate)
    }

    @Test
    fun nullForm_parsesAsNull() {
        val dto = json.decodeFromString<ReleaseDto>("""{"episode_last_update":null}""")
        assertNull(dto.episodeLastUpdate)
    }

    @Test
    fun unknownShape_degradesToNull() {
        val dto = json.decodeFromString<ReleaseDto>("""{"episode_last_update":{"unexpected":1}}""")
        assertNull(dto.episodeLastUpdate)
    }

    @Test
    fun missingField_defaultsToNull() {
        val dto = json.decodeFromString<ReleaseDto>("""{}""")
        assertNull(dto.episodeLastUpdate)
    }
}
