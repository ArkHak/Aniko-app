package com.aniko.data.mapper

import com.aniko.data.dto.ScheduleResponseDto
import com.aniko.model.ReleaseStatus
import com.aniko.model.WeekDay
import com.aniko.network.AnixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Регрессионный тест на живую верификацию (2026-08-10, `GET https://api-s.anixsekai.com/schedule`
 * без токена и параметров): `code: 0` + 7 ключей `monday..sunday`, каждый — массив полных
 * объектов `Release` (те же поля, что и в `discover/watching`/`release/{id}`). Ниже —
 * урезанный сэмпл на 2 дня по 1 релизу, чтобы не тащить в тест весь реальный ответ.
 */
class ScheduleMapperTest {
    @Test
    fun scheduleResponseDto_decodesDayKeysDirectlyAsReleaseArrays() {
        val json =
            """
            {
                "code": 0,
                "monday": [
                    {
                        "id": 8013036,
                        "title_ru": "Тайтл понедельника",
                        "title_original": "Monday Title",
                        "image": "https://s.anixmirai.com/poster/monday.jpg",
                        "description": "Описание",
                        "year": "2026",
                        "episodes_total": 12,
                        "episodes_released": 3,
                        "grade": 7.5,
                        "status": { "id": 1, "name": "Онгоинг" },
                        "genres": "Экшен, Комедия",
                        "profile_list_status": null,
                        "is_favorite": false
                    }
                ],
                "tuesday": [],
                "wednesday": [
                    {
                        "id": 42,
                        "title_ru": "Тайтл среды",
                        "title_original": null,
                        "image": "https://s.anixmirai.com/poster/wed.jpg",
                        "description": null,
                        "year": "2025",
                        "episodes_total": 24,
                        "episodes_released": 24,
                        "grade": 8.1,
                        "status": { "id": 2, "name": "Вышел" },
                        "genres": "Драма",
                        "profile_list_status": 1,
                        "is_favorite": true
                    }
                ],
                "thursday": [],
                "friday": [],
                "saturday": [],
                "sunday": []
            }
            """.trimIndent()

        val response = AnixJson.decodeFromString(ScheduleResponseDto.serializer(), json)

        assertEquals(0, response.code)
        assertEquals(1, response.monday.size)
        assertTrue(response.tuesday.isEmpty())
        assertEquals(1, response.wednesday.size)
        assertTrue(response.sunday.isEmpty())

        val schedule = response.toDomain()

        assertEquals(1, schedule.releasesOn(WeekDay.MONDAY).size)
        assertEquals("Тайтл понедельника", schedule.releasesOn(WeekDay.MONDAY).first().title)
        assertEquals(ReleaseStatus.ONGOING, schedule.releasesOn(WeekDay.MONDAY).first().status)

        assertTrue(schedule.releasesOn(WeekDay.TUESDAY).isEmpty())

        assertEquals(1, schedule.releasesOn(WeekDay.WEDNESDAY).size)
        assertEquals(ReleaseStatus.FINISHED, schedule.releasesOn(WeekDay.WEDNESDAY).first().status)
        assertTrue(schedule.releasesOn(WeekDay.WEDNESDAY).first().isFavorite)

        assertTrue(schedule.releasesOn(WeekDay.SUNDAY).isEmpty())

        // Порядок ключей в byDay — понедельник → воскресенье, как в самом ответе API.
        assertEquals(
            listOf(
                WeekDay.MONDAY,
                WeekDay.TUESDAY,
                WeekDay.WEDNESDAY,
                WeekDay.THURSDAY,
                WeekDay.FRIDAY,
                WeekDay.SATURDAY,
                WeekDay.SUNDAY,
            ),
            schedule.byDay.keys.toList(),
        )
    }
}
