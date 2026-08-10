package com.aniko.data.sync

import com.aniko.data.api.EpisodeApi
import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.ProfileListApi
import com.aniko.data.cache.FakeClock
import com.aniko.database.store.EpisodeProgressStore
import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.SyncQueueStore
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Тесты планировщика офлайн-очереди (P4.T5/P4.T6, трек C) — [SyncQueueWorker.drain] поверх
 * `MockEngine` + реальных Api-классов, по образцу `LibraryRepositoryTest`/`SessionInvalidationHttpTest`
 * (маршрутизация по `request.url.encodedPath`, `expectSuccess = true`, чтобы non-2xx статусы
 * прилетали как исключения ktor так же, как в проде через `AnixHttpClient`).
 *
 * [FakeSyncQueueStore] воспроизводит семантику `ON CONFLICT(entity_key)` реальной SQL-таблицы
 * (трек A) — см. её собственный KDoc.
 */
class SyncQueuePlannerTest {
    private val now = Instant.fromEpochMilliseconds(0)

    private fun simpleResponse(code: Int = 0): String = """{"code": $code}"""

    private fun mockClient(
        calls: MutableList<String>,
        routes: Map<String, Pair<HttpStatusCode, String>>,
    ): HttpClient {
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                calls.add(path)
                val (status, body) = routes[path] ?: error("Unexpected request path in test: $path")
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(AnixJson) }
        }
    }

    private fun worker(
        queue: SyncQueueStore,
        client: HttpClient,
        clock: Clock,
        membership: ListMembershipStore = FakeListMembershipStore(),
        progress: EpisodeProgressStore = FakeEpisodeProgressStore(),
    ): SyncQueueWorker =
        SyncQueueWorker(
            queue = queue,
            membership = membership,
            progress = progress,
            profileListApi = ProfileListApi(client),
            favoriteApi = FavoriteApi(client),
            historyApi = HistoryApi(client),
            episodeApi = EpisodeApi(client),
            clock = clock,
        )

    private fun favoriteOp(
        releaseId: Int,
        want: Boolean,
        entityKey: String = "favorite:$releaseId",
    ): SyncOperation =
        SyncOperation(
            id = 0,
            kind = SyncOperationKind.FAVORITE_SET,
            entityKey = entityKey,
            releaseId = releaseId,
            boolArg = want,
            createdAt = now,
            updatedAt = now,
        )

    private fun listSetStatusOp(
        releaseId: Int,
        status: ListStatus,
    ): SyncOperation =
        SyncOperation(
            id = 0,
            kind = SyncOperationKind.LIST_SET_STATUS,
            entityKey = "list:$releaseId",
            releaseId = releaseId,
            statusApiValue = status.apiValue,
            createdAt = now,
            updatedAt = now,
        )

    private fun listRemoveOp(releaseId: Int): SyncOperation =
        SyncOperation(
            id = 0,
            kind = SyncOperationKind.LIST_REMOVE,
            entityKey = "list:$releaseId",
            releaseId = releaseId,
            createdAt = now,
            updatedAt = now,
        )

    private fun episodeSetWatchedOp(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        want: Boolean,
    ): SyncOperation =
        SyncOperation(
            id = 0,
            kind = SyncOperationKind.EPISODE_SET_WATCHED,
            entityKey = "episode:$releaseId:$sourceId:$position",
            releaseId = releaseId,
            sourceId = sourceId,
            position = position,
            boolArg = want,
            createdAt = now,
            updatedAt = now,
        )

    private fun historyAddOp(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ): SyncOperation =
        SyncOperation(
            id = 0,
            kind = SyncOperationKind.HISTORY_ADD,
            entityKey = "history:$releaseId",
            releaseId = releaseId,
            sourceId = sourceId,
            position = position,
            createdAt = now,
            updatedAt = now,
        )

    private fun historyRemoveOp(releaseId: Int): SyncOperation =
        SyncOperation(
            id = 0,
            kind = SyncOperationKind.HISTORY_REMOVE,
            entityKey = "history:$releaseId",
            releaseId = releaseId,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun drain_fifoOrder_processesInEnqueueOrder() =
        runTest {
            val queue = FakeSyncQueueStore()
            queue.enqueue(favoriteOp(releaseId = 1, want = true))
            queue.enqueue(favoriteOp(releaseId = 2, want = true))
            val calls = mutableListOf<String>()
            val client =
                mockClient(
                    calls,
                    mapOf(
                        "/favorite/add/1" to (HttpStatusCode.OK to simpleResponse()),
                        "/favorite/add/2" to (HttpStatusCode.OK to simpleResponse()),
                    ),
                )

            val result = worker(queue, client, FakeClock(now)).drain()

            assertEquals(SyncQueueWorker.DrainResult.Completed, result)
            assertEquals(listOf("/favorite/add/1", "/favorite/add/2"), calls)
            assertTrue(queue.snapshot().isEmpty())
        }

    @Test
    fun enqueue_coalescesByEntityKey_keepsOriginalPosition_lastWriteWins() =
        runTest {
            val queue = FakeSyncQueueStore()
            val idA = queue.enqueue(favoriteOp(releaseId = 1, want = true))
            queue.enqueue(favoriteOp(releaseId = 2, want = true))
            // Повторный enqueue того же entityKey ("favorite:1") — должен схлопнуться под тем же id
            // (не добавиться третьей строкой) и заменить намерение на противоположное.
            val idARewritten = queue.enqueue(favoriteOp(releaseId = 1, want = false))

            assertEquals(idA, idARewritten)
            assertEquals(2, queue.snapshot().size)

            val calls = mutableListOf<String>()
            val client =
                mockClient(
                    calls,
                    mapOf(
                        // Финальное намерение по releaseId=1 — false, значит должен вызваться delete, не add.
                        "/favorite/delete/1" to (HttpStatusCode.OK to simpleResponse()),
                        "/favorite/add/2" to (HttpStatusCode.OK to simpleResponse()),
                    ),
                )

            val result = worker(queue, client, FakeClock(now)).drain()

            assertEquals(SyncQueueWorker.DrainResult.Completed, result)
            // Порядок обработки остаётся A, затем B — коалесцирование не переставило позицию A.
            assertEquals(listOf("/favorite/delete/1", "/favorite/add/2"), calls)
        }

    @Test
    fun drain_unauthorized_stopsPass_leavesRemainingOperationsUntouched() =
        runTest {
            val queue = FakeSyncQueueStore()
            queue.enqueue(favoriteOp(releaseId = 1, want = true))
            queue.enqueue(favoriteOp(releaseId = 2, want = true))
            val calls = mutableListOf<String>()
            val client =
                mockClient(
                    calls,
                    mapOf(
                        "/favorite/add/1" to (HttpStatusCode.Unauthorized to "Unauthorized"),
                        // Намеренно нет маршрута для "/favorite/add/2" — если воркер всё же его
                        // вызовет после 401, MockEngine упадёт с `error(...)`, и тест провалится.
                    ),
                )

            val result = worker(queue, client, FakeClock(now)).drain()

            assertEquals(SyncQueueWorker.DrainResult.Unauthorized, result)
            assertEquals(listOf("/favorite/add/1"), calls)
            // Обе операции остались в очереди нетронутыми (включая ту, что словила 401).
            assertEquals(2, queue.snapshot().size)
            assertTrue(queue.snapshot().all { it.attemptCount == 0 })
        }

    @Test
    fun drain_permanentHttpError_removesOperationFromQueue() =
        runTest {
            val queue = FakeSyncQueueStore()
            queue.enqueue(listSetStatusOp(releaseId = 186, status = ListStatus.WATCHING))
            val calls = mutableListOf<String>()
            val client =
                mockClient(
                    calls,
                    mapOf(
                        "/profile/list/add/1/186" to (HttpStatusCode.NotFound to "Not Found"),
                    ),
                )

            val result = worker(queue, client, FakeClock(now)).drain()

            assertEquals(SyncQueueWorker.DrainResult.Completed, result)
            assertEquals(listOf("/profile/list/add/1/186"), calls)
            assertTrue(queue.snapshot().isEmpty())
        }

    @Test
    fun drain_domainApiError_removesOperationFromQueue() =
        runTest {
            val queue = FakeSyncQueueStore()
            queue.enqueue(favoriteOp(releaseId = 1, want = true))
            val calls = mutableListOf<String>()
            val client =
                mockClient(
                    calls,
                    mapOf(
                        // HTTP 200, но code != 0 — доменная ошибка API (`AnixError.Api`), тоже перманентная.
                        "/favorite/add/1" to (HttpStatusCode.OK to simpleResponse(code = 7)),
                    ),
                )

            val result = worker(queue, client, FakeClock(now)).drain()

            assertEquals(SyncQueueWorker.DrainResult.Completed, result)
            assertTrue(queue.snapshot().isEmpty())
        }

    @Test
    fun drain_serverError_keepsOperation_growingBackoffOnRepeatedFailure() =
        runTest {
            val queue = FakeSyncQueueStore()
            queue.enqueue(favoriteOp(releaseId = 1, want = true))
            val calls = mutableListOf<String>()
            val client =
                mockClient(
                    calls,
                    mapOf(
                        "/favorite/add/1" to (HttpStatusCode.InternalServerError to "Server Error"),
                    ),
                )
            val clock = FakeClock(now)

            val firstResult = worker(queue, client, clock).drain()

            check(firstResult is SyncQueueWorker.DrainResult.Backoff) { "Expected Backoff, got $firstResult" }
            assertEquals(1, queue.snapshot().size)
            val afterFirstFailure = queue.snapshot().single()
            assertEquals(1, afterFirstFailure.attemptCount)
            val nextAttemptAt1 = requireNotNull(afterFirstFailure.nextAttemptAt)
            assertTrue(nextAttemptAt1 > now)

            // Продвигаем время ровно до момента, когда операция снова "созревает".
            clock.now = nextAttemptAt1

            val secondResult = worker(queue, client, clock).drain()

            check(secondResult is SyncQueueWorker.DrainResult.Backoff) { "Expected Backoff, got $secondResult" }
            val afterSecondFailure = queue.snapshot().single()
            assertEquals(2, afterSecondFailure.attemptCount)
            // Растущий backoff: второй провал подряд должен дать больший retryAfter, чем первый.
            assertTrue(secondResult.retryAfter > firstResult.retryAfter)
            assertEquals(listOf("/favorite/add/1", "/favorite/add/1"), calls)
        }

    @Test
    fun drain_dispatchesEachOperationKindToItsOwnApiEndpoint() =
        runTest {
            val queue = FakeSyncQueueStore()
            queue.enqueue(listSetStatusOp(releaseId = 10, status = ListStatus.WATCHING))
            queue.enqueue(listRemoveOp(releaseId = 11))
            queue.enqueue(favoriteOp(releaseId = 12, want = true))
            queue.enqueue(episodeSetWatchedOp(releaseId = 13, sourceId = 5, position = 2, want = true))
            queue.enqueue(historyAddOp(releaseId = 14, sourceId = 6, position = 3))
            queue.enqueue(historyRemoveOp(releaseId = 15))

            val membership = FakeListMembershipStore()
            // LIST_REMOVE не несёт статус сам по себе (см. SyncQueueWorker KDoc, пункт 2) — воркер
            // должен подсмотреть его в ListMembershipStore.
            membership.seedStatus(releaseId = 11, status = ListStatus.WATCHING)

            val calls = mutableListOf<String>()
            val client =
                mockClient(
                    calls,
                    mapOf(
                        "/profile/list/add/1/10" to (HttpStatusCode.OK to simpleResponse()),
                        "/profile/list/delete/1/11" to (HttpStatusCode.OK to simpleResponse()),
                        "/favorite/add/12" to (HttpStatusCode.OK to simpleResponse()),
                        "/episode/watch/13/5/2" to (HttpStatusCode.OK to simpleResponse()),
                        "/history/add/14/6/3" to (HttpStatusCode.OK to simpleResponse()),
                        "/history/delete/15" to (HttpStatusCode.OK to simpleResponse()),
                    ),
                )

            val result = worker(queue, client, FakeClock(now), membership = membership).drain()

            assertEquals(SyncQueueWorker.DrainResult.Completed, result)
            assertEquals(
                listOf(
                    "/profile/list/add/1/10",
                    "/profile/list/delete/1/11",
                    "/favorite/add/12",
                    "/episode/watch/13/5/2",
                    "/history/add/14/6/3",
                    "/history/delete/15",
                ),
                calls,
            )
            assertTrue(queue.snapshot().isEmpty())
        }

    @Test
    fun drain_emptyQueue_returnsIdle() =
        runTest {
            val queue = FakeSyncQueueStore()
            val client = mockClient(mutableListOf(), emptyMap())

            val result = worker(queue, client, FakeClock(now)).drain()

            assertEquals(SyncQueueWorker.DrainResult.Idle, result)
        }
}
