package com.aniko.data.repository

import com.aniko.data.api.ScheduleApi
import com.aniko.data.cache.Cached
import com.aniko.data.cache.FakeClock
import com.aniko.data.cache.FakeReleaseCacheStore
import com.aniko.data.cache.FakeReleaseListStore
import com.aniko.data.sync.FakeListMembershipStore
import com.aniko.model.WeekDay
import com.aniko.network.AnixJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Тесты `ScheduleRepository` (P4.T7, S3 — первый потребитель `ScheduleApi`).
 *
 * Проверяет именно интеграцию: один сетевой ответ `GET schedule` раскладывается на 7 страниц
 * [com.aniko.database.store.ReleaseListStore] (по одной на [WeekDay]) и обратно собирается в
 * [com.aniko.model.Schedule] через `hydratePagedIds` — сам JSON→domain маппинг уже покрыт
 * `ScheduleMapperTest`, здесь его по минимуму (один тайтл на понедельник, остальные дни пустые).
 */
class ScheduleRepositoryTest {
    private val now = Instant.fromEpochMilliseconds(0)

    private fun scheduleJson(): String =
        """
        {
            "code": 0,
            "monday": [
                {
                    "id": 8013036,
                    "title_ru": "Тайтл понедельника",
                    "title_original": "Monday Title",
                    "image": "https://s.anixmirai.com/poster/monday.jpg",
                    "description": null,
                    "year": "2026",
                    "episodes_total": 12,
                    "episodes_released": 3,
                    "grade": 7.5,
                    "status": { "id": 1, "name": "Онгоинг" },
                    "genres": "Экшен",
                    "profile_list_status": null,
                    "is_favorite": false
                }
            ],
            "tuesday": [],
            "wednesday": [],
            "thursday": [],
            "friday": [],
            "saturday": [],
            "sunday": []
        }
        """.trimIndent()

    private fun repository(): ScheduleRepository {
        val mockEngine =
            MockEngine { request ->
                check(request.url.encodedPath == "/schedule") { "Unexpected path: ${request.url.encodedPath}" }
                respond(
                    content = scheduleJson(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(AnixJson) } }
        return ScheduleRepository(
            scheduleApi = ScheduleApi(client = httpClient),
            releaseCacheStore = FakeReleaseCacheStore(),
            releaseListStore = FakeReleaseListStore(),
            listMembershipStore = FakeListMembershipStore(),
            clock = FakeClock(now),
        )
    }

    @Test
    fun observeSchedule_noCache_fetchesAndHydratesAllSevenDays() =
        runTest {
            val repository = repository()

            val result = repository.observeSchedule().toList()

            assertEquals(1, result.size)
            val emission = result.single()
            assertEquals(Cached.Origin.NETWORK, emission.origin)
            assertEquals(1, emission.value.releasesOn(WeekDay.MONDAY).size)
            assertEquals(
                "Тайтл понедельника",
                emission.value
                    .releasesOn(WeekDay.MONDAY)
                    .first()
                    .title,
            )
            assertTrue(emission.value.releasesOn(WeekDay.TUESDAY).isEmpty())
            assertTrue(emission.value.releasesOn(WeekDay.SUNDAY).isEmpty())
        }

    @Test
    fun observeSchedule_freshCache_doesNotHitNetworkAgain() =
        runTest {
            val repository = repository()
            // Первый вызов — заполняет кэш (сеть дёргается один раз).
            repository.observeSchedule().toList()

            // Второй вызов сразу после первого — тот же `clock`/`fetchedAt`, кэш в пределах TTL
            // (CachePolicy.CatalogListing = 24h) — сеть не должна вызываться повторно.
            val second = repository.observeSchedule().toList()

            assertEquals(1, second.size)
            assertEquals(Cached.Origin.CACHE, second.single().origin)
            assertEquals(false, second.single().isStale)
        }
}
