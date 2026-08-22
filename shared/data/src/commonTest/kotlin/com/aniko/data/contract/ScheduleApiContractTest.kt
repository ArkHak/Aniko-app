package com.aniko.data.contract

import com.aniko.data.api.ScheduleApi
import com.aniko.data.fixtures.ApiFixtures
import com.aniko.data.mapper.toDomain
import com.aniko.model.ReleaseStatus
import com.aniko.model.WeekDay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Контрактный тест `ScheduleApi` (P11.T1) — покрывает фикстуру `schedule`.
 */
class ScheduleApiContractTest {
    @Test
    fun schedule_mapsToSevenDaysWithRealValues() =
        runTest {
            val client = mockAnixClient("/schedule", ApiFixtures.schedule)
            val api = ScheduleApi(client)

            val dto = api.schedule()
            val schedule = dto.toDomain()

            // Каждый день фикстуры несёт ровно один релиз.
            for (day in WeekDay.entries) {
                assertEquals(1, schedule.releasesOn(day).size, "day=$day")
            }

            val monday = schedule.releasesOn(WeekDay.MONDAY).single()
            assertEquals(19588, monday.id)
            assertEquals(
                "Аккуратная и симпатичная девочка в моей новой школе — подруга детства, " +
                    "с которой я играл, думая, что она мальчик",
                monday.title,
            )
            assertEquals(2026, monday.year)
            assertEquals(5, monday.episodesReleased)
            assertEquals(12, monday.episodesTotal)
            assertEquals(ReleaseStatus.UNKNOWN, monday.status) // status: null в фикстуре
            assertTrue(monday.genres.contains("романтика"))
        }
}
