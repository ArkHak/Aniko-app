package com.aniko.data.mapper

import com.aniko.data.dto.ProfileDetailsDto
import com.aniko.data.dto.ProfileResponseDto
import com.aniko.network.AnixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Регрессионные тесты на поля профиля, домапленные в Фазе 9 (P9.T8/T9/T11): `watch_dynamics`,
 * `preferred_genres` и `history`.
 *
 * JSON ниже — сокращённый реальный ответ живой проверки 2026-08-18
 * (`GET https://api-s.anixsekai.com/profile/1000001`, без токена), она подтвердила вердикт
 * аудита Фазы 0 (P0.T4) по всем трём полям.
 */
class ProfileMapperTest {
    @Test
    fun profileResponse_decodesWatchDynamicsPreferredGenresAndHistory() {
        val response = AnixJson.decodeFromString(ProfileResponseDto.serializer(), LIVE_PROFILE_JSON)
        val profile = requireNotNull(response.profile).toDomain()

        assertEquals(4, profile.watchDynamics.size)
        assertEquals(2, profile.preferredGenres.size)
        assertEquals("экшен", profile.preferredGenres[0].name)
        assertEquals(10, profile.preferredGenres[0].percentage)
        assertEquals(listOf(2098, 20495), profile.recentlyWatched.map { it.id })
        assertEquals("Баскетбол Куроко: Последняя игра", profile.recentlyWatched[0].title)
    }

    /**
     * Живьём `watch_dynamics` — кольцевой буфер на 31 слот (по слоту на число месяца) в
     * произвольном порядке, с «протухшими» слотами прошлого месяца. Хронологию задаёт только
     * `timestamp`, и упорядочивает по нему маппер — иначе недельный график профиля (P9.T9)
     * нарисовал бы 7 случайных дней вместо семи последних.
     */
    @Test
    fun watchDynamics_isSortedByTimestampAscending_notByArrayOrder() {
        val response = AnixJson.decodeFromString(ProfileResponseDto.serializer(), LIVE_PROFILE_JSON)
        val points = requireNotNull(response.profile).toDomain().watchDynamics

        assertEquals(listOf(31, 1, 15, 16), points.map { it.day })
        assertTrue(points.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp })
    }

    /** Срез под недельный график — последние N точек уже отсортированного списка. */
    @Test
    fun recentWatchDynamics_takesLastPointsByTime() {
        val profile = requireNotNull(AnixJson.decodeFromString(ProfileResponseDto.serializer(), LIVE_PROFILE_JSON).profile)

        assertEquals(listOf(1, 15, 16), profile.toDomain().recentWatchDynamics(days = 3).map { it.day })
        assertEquals(4, profile.toDomain().recentWatchDynamics().size)
    }

    /** `watched_time` приходит в минутах (вердикт P0.T4), UI показывает часы. */
    @Test
    fun watchedHours_convertsMinutesToWholeHours() {
        val profile = ProfileDetailsDto(id = 1, login = "user", watchedTime = 51212).toDomain()

        assertEquals(51212, profile.watchedTime)
        assertEquals(853, profile.watchedHours)
    }

    /** Ни одного из трёх полей может не быть в ответе — дефолты не должны ронять парсинг. */
    @Test
    fun profileWithoutPhase9Fields_defaultsToEmptyLists() {
        val json = """{"code": 0, "profile": {"id": 1, "login": "user"}}"""

        val profile = requireNotNull(AnixJson.decodeFromString(ProfileResponseDto.serializer(), json).profile).toDomain()

        assertEquals(emptyList(), profile.watchDynamics)
        assertEquals(emptyList(), profile.preferredGenres)
        assertEquals(emptyList(), profile.recentlyWatched)
        assertEquals(0, profile.watchedHours)
    }

    private companion object {
        val LIVE_PROFILE_JSON =
            """
            {
                "code": 0,
                "profile": {
                    "id": 1000001,
                    "login": "user",
                    "watched_time": 51212,
                    "watched_episode_count": 2105,
                    "watch_dynamics": [
                        {"id": 83955914, "day": 31, "count": 18, "timestamp": 1769807382},
                        {"id": 83671101, "day": 16, "count": 0, "timestamp": 1786828144},
                        {"id": 83402961, "day": 1, "count": 1, "timestamp": 1780261738},
                        {"id": 83652621, "day": 15, "count": 2, "timestamp": 1786741738}
                    ],
                    "preferred_genres": [
                        {"name": "экшен", "percentage": 10},
                        {"name": "романтика", "percentage": 8}
                    ],
                    "preferred_audiences": [{"name": "сёнен", "percentage": 7}],
                    "history": [
                        {
                            "@id": 1,
                            "id": 2098,
                            "title_ru": "Баскетбол Куроко: Последняя игра",
                            "image": "https://s.anixmirai.com/posters/q0rsUzC893Et7MPpXm6iTOgZgTA5z0.jpg",
                            "episodes_released": 1,
                            "episodes_total": 1,
                            "last_view_timestamp": 1786996880
                        },
                        {
                            "@id": 6,
                            "id": 20495,
                            "title_ru": "Магическая битва 2",
                            "image": "https://s.anixmirai.com/posters/example.jpg",
                            "episodes_released": 23,
                            "episodes_total": 23,
                            "last_view_timestamp": 1786910480
                        }
                    ]
                }
            }
            """.trimIndent()
    }
}
