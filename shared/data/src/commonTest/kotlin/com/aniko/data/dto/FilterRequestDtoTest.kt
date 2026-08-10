package com.aniko.data.dto

import com.aniko.network.AnixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `FilterRequestDto` не имеет `toDomain()` (заводится позже вместе с UI фильтра каталога),
 * поэтому здесь проверяется в первую очередь корректность `@SerialName` — то, что Kotlin-поля
 * camelCase лягут на ожидаемые JSON-ключи snake_case из декомпиленного `FilterRequest.java`
 * (см. KDoc [FilterRequestDto]).
 */
class FilterRequestDtoTest {
    @Test
    fun encodesDefaults_asEmptyObject() {
        // AnixJson не переопределяет `encodeDefaults` (по умолчанию `false`, см.
        // AnixHttpClient.AnixJson), поэтому DTO из одних дефолтных значений сериализуется
        // в пустой объект — сервер трактует отсутствие поля так же, как и его дефолт
        // (проверено вживую 2026-08-10: `{"sort":0,...}` с явными дефолтами тоже вернул 200,
        // т.е. оба варианта тела эквивалентны для сервера).
        val json = AnixJson.encodeToString(FilterRequestDto.serializer(), FilterRequestDto())

        assertEquals("{}", json)
    }

    @Test
    fun encodesAllFields_withExpectedSnakeCaseKeys() {
        val request =
            FilterRequestDto(
                categoryId = 1L,
                statusId = 2L,
                startYear = 2000,
                endYear = 2020,
                studio = "Studio Ghibli",
                source = "manga",
                episodesFrom = 1,
                episodesTo = 24,
                sort = FilterRequestDto.SORT_GRADE_DESC,
                country = "Japan",
                season = 1,
                episodeDurationFrom = 20,
                episodeDurationTo = 30,
                genres = listOf("comedy", "drama"),
                profileListExclusions = listOf(1, 2),
                types = listOf(1L, 2L),
                ageRatings = listOf(FilterRequestDto.AGE_RATING_MORE_THAN_13),
                isGenresExcludeModeEnabled = true,
                genresMode = FilterRequestDto.GENRES_MODE_ANY,
            )

        val json = AnixJson.encodeToString(FilterRequestDto.serializer(), request)

        assertTrue(json.contains("\"category_id\":1"))
        assertTrue(json.contains("\"status_id\":2"))
        assertTrue(json.contains("\"start_year\":2000"))
        assertTrue(json.contains("\"end_year\":2020"))
        assertTrue(json.contains("\"studio\":\"Studio Ghibli\""))
        assertTrue(json.contains("\"source\":\"manga\""))
        assertTrue(json.contains("\"episodes_from\":1"))
        assertTrue(json.contains("\"episodes_to\":24"))
        assertTrue(json.contains("\"sort\":1"))
        assertTrue(json.contains("\"country\":\"Japan\""))
        assertTrue(json.contains("\"season\":1"))
        assertTrue(json.contains("\"episode_duration_from\":20"))
        assertTrue(json.contains("\"episode_duration_to\":30"))
        assertTrue(json.contains("\"genres\":[\"comedy\",\"drama\"]"))
        assertTrue(json.contains("\"profile_list_exclusions\":[1,2]"))
        assertTrue(json.contains("\"types\":[1,2]"))
        assertTrue(json.contains("\"age_ratings\":[2]"))
        assertTrue(json.contains("\"is_genres_exclude_mode_enabled\":true"))
        assertTrue(json.contains("\"genres_mode\":1"))
    }

    @Test
    fun decodesMinimalLiveResponseBody_roundTrips() {
        // Тело, подтверждённое живой проверкой 2026-08-10 (см. KDoc FilterRequestDto).
        val liveBody =
            """
            {"sort":0,"genres":[],"types":[],"age_ratings":[],"profile_list_exclusions":[]}
            """.trimIndent()

        val decoded = AnixJson.decodeFromString(FilterRequestDto.serializer(), liveBody)

        assertEquals(FilterRequestDto(), decoded)
    }
}
