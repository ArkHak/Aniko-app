package com.aniko.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Списковые эндпоинты (`profile/list`, `favorite`, `history`, `discover/watching`) отдают
 * `last_view_episode` объектом эпизода (`{"@id":...,"releaseId":...,"position":...}`), а не
 * числом — без чтения `position` «Мои списки» показывали вечный «0 из N» (2026-09-18).
 * См. KDoc [LastViewEpisodeSerializer].
 */
class LastViewEpisodeSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun numericForm_parsesAsInt() {
        val dto = json.decodeFromString<ReleaseDto>("""{"last_view_episode":7}""")
        assertEquals(7, dto.lastViewEpisode)
    }

    @Test
    fun objectForm_parsesPosition() {
        val dto =
            json.decodeFromString<ReleaseDto>(
                """{"last_view_episode":{"@id":12,"releaseId":34,"position":5}}""",
            )
        assertEquals(5, dto.lastViewEpisode)
    }

    @Test
    fun nullForm_parsesAsNull() {
        val dto = json.decodeFromString<ReleaseDto>("""{"last_view_episode":null}""")
        assertNull(dto.lastViewEpisode)
    }

    @Test
    fun objectWithoutPosition_degradesToNull() {
        val dto = json.decodeFromString<ReleaseDto>("""{"last_view_episode":{"@id":12}}""")
        assertNull(dto.lastViewEpisode)
    }

    @Test
    fun missingField_defaultsToNull() {
        val dto = json.decodeFromString<ReleaseDto>("""{}""")
        assertNull(dto.lastViewEpisode)
    }
}
