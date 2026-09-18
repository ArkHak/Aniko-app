package com.aniko.data.repository

import com.aniko.data.api.EpisodeApi
import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.ProfileListApi
import com.aniko.data.cache.FakeClock
import com.aniko.data.cache.FakeReleaseCacheStore
import com.aniko.data.cache.FakeReleaseListStore
import com.aniko.data.sync.FakeEpisodeProgressStore
import com.aniko.data.sync.FakeListMembershipStore
import com.aniko.data.sync.FakeSyncQueueStore
import com.aniko.data.sync.SyncQueueWorker
import com.aniko.model.AnixError
import com.aniko.model.ListStatus
import com.aniko.network.AnixJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Тесты `LibraryRepository` (Фаза 6 + P4.T7/S3) поверх `MockEngine`.
 *
 * Методы чтения (`myList`/`favorites`/`history`) — прямые suspend-вызовы `Api`, поведение не
 * изменилось (Фаза 6): (а) репозиторий доходит до правильного пути и маппит DTO в домен,
 * (б) `code != 0` в ответе превращается в `AnixError.Api`.
 *
 * Методы записи (`addToList`/`removeFromList`/`addFavorite`/`removeFavorite`/`addHistory`/
 * `removeFromHistory`) с P4.T7 больше НЕ бьют в `Api` напрямую и не бросают исключение при сбое
 * сети/API — они пишут оптимистично в локальный стор и уходят через `SyncQueueStore`/
 * `SyncQueueWorker.drain()` (см. `SyncQueuePlannerTest` про сам воркер). Здесь проверяется
 * интеграция: (а) локальный стор обновлён сразу после вызова репозитория, (б) `drain()` внутри
 * репозитория действительно достучался до правильного эндпоинта и вычистил очередь при успехе
 * либо при перманентной доменной ошибке (без исключения наружу).
 */
class LibraryRepositoryTest {
    private val now = Instant.fromEpochMilliseconds(0)

    private val sampleReleaseJson =
        """
        {
            "id": 186,
            "title_ru": "Тестовый релиз",
            "title_original": "Test Release",
            "image": "https://s.anixmirai.com/poster.jpg",
            "year": "2021",
            "episodes_total": 12,
            "episodes_released": 12,
            "grade": 8.5,
            "genres": "Экшен, Драма",
            "profile_list_status": 1,
            "is_favorite": true
        }
        """.trimIndent()

    private fun pageableResponse(code: Int = 0): String =
        """
        {
            "code": $code,
            "content": [$sampleReleaseJson],
            "current_page": 0,
            "total_page_count": 3,
            "total_count": 42
        }
        """.trimIndent()

    private fun simpleResponse(code: Int = 0): String = """{"code": $code}"""

    /** Пучок фейков одного теста — [membership]/[queue] нужны наружу, чтобы проверить их состояние после вызова. */
    private class Fixture(
        val repository: LibraryRepository,
        val membership: FakeListMembershipStore,
        val queue: FakeSyncQueueStore,
    )

    private fun fixture(
        expectedPath: String,
        responseBody: String,
    ): Fixture {
        val mockEngine =
            MockEngine { request ->
                val path = request.url.encodedPath
                check(path == expectedPath) { "Unexpected path: $path, expected: $expectedPath" }
                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val httpClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) { json(AnixJson) }
            }
        val membership = FakeListMembershipStore()
        val queue = FakeSyncQueueStore()
        val worker =
            SyncQueueWorker(
                queue = queue,
                membership = membership,
                progress = FakeEpisodeProgressStore(),
                profileListApi = ProfileListApi(httpClient),
                favoriteApi = FavoriteApi(httpClient),
                historyApi = HistoryApi(httpClient),
                episodeApi = EpisodeApi(httpClient),
                clock = FakeClock(now),
            )
        val repository =
            LibraryRepository(
                profileListApi = ProfileListApi(client = httpClient),
                favoriteApi = FavoriteApi(client = httpClient),
                historyApi = HistoryApi(client = httpClient),
                listMembershipStore = membership,
                releaseCacheStore = FakeReleaseCacheStore(),
                releaseListStore = FakeReleaseListStore(),
                syncQueueStore = queue,
                syncQueueWorker = worker,
                clock = FakeClock(now),
            )
        return Fixture(repository, membership, queue)
    }

    // ---- Списки по статусу ------------------------------------------------------------

    @Test
    fun myList_happyPath_returnsMappedPage() =
        runTest {
            val repository = fixture("/profile/list/all/1/0", pageableResponse()).repository

            val page = repository.myList(ListStatus.WATCHING, page = 0)

            assertEquals(1, page.items.size)
            assertEquals(186, page.items.first().id)
            assertEquals(0, page.currentPage)
            assertEquals(3, page.totalPages)
        }

    @Test
    fun myList_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = fixture("/profile/list/all/1/0", pageableResponse(code = 7)).repository

            assertFailsWith<AnixError.Api> {
                repository.myList(ListStatus.WATCHING, page = 0)
            }
        }

    @Test
    fun addToList_happyPath_writesLocalStatus_andClearsQueue() =
        runTest {
            val fixture = fixture("/profile/list/add/1/186", simpleResponse())

            fixture.repository.addToList(ListStatus.WATCHING, releaseId = 186)

            assertEquals(ListStatus.WATCHING, fixture.membership.observeStatus(186).first())
            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    @Test
    fun addToList_nonZeroCode_doesNotThrow_keepsOptimisticWrite_dropsFromQueue() =
        runTest {
            // HTTP 200, но code != 0 — доменная ошибка API, permanent (см. SyncQueueWorker.classify) —
            // операция снимается с очереди, а не ретраится, и addToList не бросает исключение.
            val fixture = fixture("/profile/list/add/1/186", simpleResponse(code = 5))

            fixture.repository.addToList(ListStatus.WATCHING, releaseId = 186)

            assertEquals(ListStatus.WATCHING, fixture.membership.observeStatus(186).first())
            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    @Test
    fun removeFromList_happyPath_clearsLocalStatus_andClearsQueue() =
        runTest {
            val fixture = fixture("/profile/list/delete/1/186", simpleResponse())
            fixture.membership.seedStatus(186, ListStatus.WATCHING)

            fixture.repository.removeFromList(releaseId = 186)

            assertNull(fixture.membership.observeStatus(186).first())
            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    @Test
    fun removeFromList_capturesPreviousStatus_beforeClearingIt_soWorkerCanResolveDeleteUrl() =
        runTest {
            // Регрессия: если бы репозиторий не передал старый статус явно в SyncOperation,
            // SyncQueueWorker.resolveRemovalStatus прочитал бы уже обнулённый ListMembershipStore
            // и решил бы, что удалять нечего — запрос на "/profile/list/delete/1/186" не ушёл бы
            // вовсе, и MockEngine здесь упал бы с "Unexpected path" на любой другой запрос.
            val fixture = fixture("/profile/list/delete/1/186", simpleResponse())
            fixture.membership.seedStatus(186, ListStatus.WATCHING)

            fixture.repository.removeFromList(releaseId = 186)

            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    // ---- Избранное ----------------------------------------------------------------------

    @Test
    fun favorites_happyPath_returnsMappedPage() =
        runTest {
            val repository = fixture("/favorite/all/0", pageableResponse()).repository

            val page = repository.favorites(page = 0)

            assertEquals(1, page.items.size)
            assertEquals(true, page.items.first().isFavorite)
        }

    @Test
    fun favorites_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = fixture("/favorite/all/0", pageableResponse(code = 3)).repository

            assertFailsWith<AnixError.Api> {
                repository.favorites(page = 0)
            }
        }

    @Test
    fun addFavorite_happyPath_writesLocalFavorite_andClearsQueue() =
        runTest {
            val fixture = fixture("/favorite/add/186", simpleResponse())

            fixture.repository.addFavorite(releaseId = 186)

            assertTrue(fixture.membership.observeFavorite(186).first())
            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    @Test
    fun removeFavorite_happyPath_clearsLocalFavorite_andClearsQueue() =
        runTest {
            val fixture = fixture("/favorite/delete/186", simpleResponse())
            fixture.membership.setFavorite(186, isFavorite = true, updatedAt = now)

            fixture.repository.removeFavorite(releaseId = 186)

            assertEquals(false, fixture.membership.observeFavorite(186).first())
            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    // ---- История просмотра ---------------------------------------------------------------

    @Test
    fun history_happyPath_returnsMappedPage() =
        runTest {
            val repository = fixture("/history/0", pageableResponse()).repository

            val page = repository.history(page = 0)

            assertEquals(1, page.items.size)
            assertEquals(186, page.items.first().id)
        }

    @Test
    fun history_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = fixture("/history/0", pageableResponse(code = 2)).repository

            assertFailsWith<AnixError.Api> {
                repository.history(page = 0)
            }
        }

    @Test
    fun addHistory_happyPath_clearsQueueAfterDrain() =
        runTest {
            val fixture = fixture("/history/add/186/8/1", simpleResponse())

            fixture.repository.addHistory(releaseId = 186, sourceId = 8, position = 1)

            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    @Test
    fun removeFromHistory_happyPath_clearsQueueAfterDrain() =
        runTest {
            val fixture = fixture("/history/delete/186", simpleResponse())

            fixture.repository.removeFromHistory(releaseId = 186)

            assertTrue(fixture.queue.snapshot().isEmpty())
        }

    // ---- Обогащение списков прогрессом из истории (2026-09-18) ----------------------------

    /** Вариант фикстуры с диспетчеризацией по пути — обогащение дёргает и список, и историю. */
    private fun fixtureByPath(responses: Map<String, String>): LibraryRepository {
        val mockEngine =
            MockEngine { request ->
                val body = responses[request.url.encodedPath] ?: error("Unexpected path: ${request.url.encodedPath}")
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val httpClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) { json(AnixJson) }
            }
        val membership = FakeListMembershipStore()
        val queue = FakeSyncQueueStore()
        val worker =
            SyncQueueWorker(
                queue = queue,
                membership = membership,
                progress = FakeEpisodeProgressStore(),
                profileListApi = ProfileListApi(httpClient),
                favoriteApi = FavoriteApi(httpClient),
                historyApi = HistoryApi(httpClient),
                episodeApi = EpisodeApi(httpClient),
                clock = FakeClock(now),
            )
        return LibraryRepository(
            profileListApi = ProfileListApi(client = httpClient),
            favoriteApi = FavoriteApi(client = httpClient),
            historyApi = HistoryApi(client = httpClient),
            listMembershipStore = membership,
            releaseCacheStore = FakeReleaseCacheStore(),
            releaseListStore = FakeReleaseListStore(),
            syncQueueStore = queue,
            syncQueueWorker = worker,
            clock = FakeClock(now),
        )
    }

    private val listItemWithoutProgressJson =
        """{"id":186,"title_ru":"Тестовый релиз","episodes_total":12,"last_view_episode":null}"""

    private fun historyPageWithPosition(position: Int?): String =
        """
        {
            "code": 0,
            "content": [
                {
                    "id": 186,
                    "title_ru": "Тестовый релиз",
                    "last_view_episode": ${position?.let { """{"@id":1,"releaseId":186,"position":$it}""" } ?: "null"}
                }
            ],
            "current_page": 0,
            "total_page_count": 1
        }
        """.trimIndent()

    @Test
    fun listPaginator_enrichesWatchedPosition_fromHistoryWhenListOmitsIt() =
        runTest {
            // Живая проверка 2026-09-18: profile/list отдаёт last_view_episode=null всегда,
            // history — объектом эпизода; «N из M» в списках собирается из неё.
            val repository =
                fixtureByPath(
                    mapOf(
                        "/profile/list/all/1/0" to
                            """{"code":0,"content":[$listItemWithoutProgressJson],"current_page":0,"total_page_count":1}""",
                        "/history/0" to historyPageWithPosition(5),
                    ),
                )
            val paginator = repository.listPaginator(ListStatus.WATCHING)

            paginator.loadNext()

            val items = paginator.state.value.items
            assertEquals(5, items.first().lastViewEpisode)
        }

    @Test
    fun listPaginator_historyFailure_keepsListAsIs() =
        runTest {
            // Сбой истории не роняет сам список — страница возвращается без прогресса.
            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/profile/list/all/1/0" ->
                            respond(
                                content =
                                    """{"code":0,"content":[$listItemWithoutProgressJson],"current_page":0,"total_page_count":1}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        else -> respond(content = "boom", status = HttpStatusCode.InternalServerError)
                    }
                }
            val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(AnixJson) } }
            val membership = FakeListMembershipStore()
            val queue = FakeSyncQueueStore()
            val repository =
                LibraryRepository(
                    profileListApi = ProfileListApi(client = httpClient),
                    favoriteApi = FavoriteApi(client = httpClient),
                    historyApi = HistoryApi(client = httpClient),
                    listMembershipStore = membership,
                    releaseCacheStore = FakeReleaseCacheStore(),
                    releaseListStore = FakeReleaseListStore(),
                    syncQueueStore = queue,
                    syncQueueWorker =
                        SyncQueueWorker(
                            queue = queue,
                            membership = membership,
                            progress = FakeEpisodeProgressStore(),
                            profileListApi = ProfileListApi(httpClient),
                            favoriteApi = FavoriteApi(httpClient),
                            historyApi = HistoryApi(httpClient),
                            episodeApi = EpisodeApi(httpClient),
                            clock = FakeClock(now),
                        ),
                    clock = FakeClock(now),
                )
            val paginator = repository.listPaginator(ListStatus.WATCHING)

            paginator.loadNext()

            val state = paginator.state.value
            assertEquals(1, state.items.size)
            assertNull(state.items.first().lastViewEpisode)
        }
}
