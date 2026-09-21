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
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
import com.aniko.model.ListMembership
import com.aniko.model.ListStatus
import com.aniko.model.ReleaseId
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Членство в списках как источник правды для экрана «Мои списки»
 * (`LibraryRepository.observeListMemberships`/`locallyEditedReleases`).
 *
 * Симптом, ради которого это заведено: смена списка у карточки нигде не отражалась — релиз
 * добавлялся в новый список, но не исчезал из старого, потому что вкладки жили на уже загруженных
 * серверных страницах и про локальную мутацию не знали. Здесь проверяется data-часть контракта:
 * (а) любая мутация сразу видна в реактивном членстве, (б) репозиторий помечает релиз как
 * «переложен пользователем» (по этому списку вкладка показывает его сверху, ещё до того как его
 * вернёт сервер), (в) серверная страница наполняет локальную БД и при этом НЕ перетирает ещё не
 * отправленную правку. UI-часть того же сценария — `LibraryAddSmokeTest` в `composeApp`.
 */
class LibraryMembershipFlowTest {
    private val now = Instant.fromEpochMilliseconds(0)
    private val releaseId = 186

    private class Fixture(
        val repository: LibraryRepository,
        val membership: FakeListMembershipStore,
        val queue: FakeSyncQueueStore,
    )

    /** Ответ `profile/list`/`favorite`/`history` из одного элемента; `profileListStatus` — поле DTO. */
    private fun pageJson(
        profileListStatus: Int? = null,
        isFavorite: Boolean = false,
    ): String =
        """
        {
            "code": 0,
            "content": [
                {
                    "id": $releaseId,
                    "title_ru": "Тестовый релиз",
                    "episodes_total": 12,
                    "profile_list_status": ${profileListStatus ?: "null"},
                    "is_favorite": $isFavorite
                }
            ],
            "current_page": 0,
            "total_page_count": 1
        }
        """.trimIndent()

    /**
     * [status] `null` отвечает `{"code":0}` на любой путь (мутациям больше ничего и не нужно);
     * [routes] переопределяют ответ для конкретных путей.
     */
    private fun fixture(
        status: HttpStatusCode = HttpStatusCode.OK,
        routes: Map<String, String> = emptyMap(),
    ): Fixture {
        val mockEngine =
            MockEngine { request ->
                respond(
                    content = routes[request.url.encodedPath] ?: """{"code":0}""",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        // `expectSuccess = true` — как в продовом `createAnixHttpClient`: без него Ktor не
        // бросает на 4xx/5xx, и «сервер недоступен» в тесте молча выглядел бы как успех.
        val httpClient =
            HttpClient(mockEngine) {
                expectSuccess = true
                install(ContentNegotiation) { json(AnixJson) }
            }
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
        return Fixture(repository, membership, queue)
    }

    // ---- Перенос между списками -----------------------------------------------------------

    @Test
    fun addToList_movesReleaseBetweenLists_inObservedMembership() =
        runTest {
            val fixture = fixture()
            fixture.membership.seedStatus(releaseId, ListStatus.WATCHING)

            fixture.repository.addToList(ListStatus.PLANNED, releaseId)

            // Одно и то же значение видят и вкладка «Смотрю» (релиз уходит), и вкладка «В планах»
            // (релиз приходит) — они читают один и тот же поток.
            assertEquals(ListStatus.PLANNED, fixture.repository.membershipOf(releaseId)?.status)
            assertEquals(listOf(releaseId), fixture.repository.locallyEditedReleases.value)
        }

    @Test
    fun removeFromList_clearsStatus_andStillCountsAsUserEdit() =
        runTest {
            val fixture = fixture()
            fixture.membership.seedStatus(releaseId, ListStatus.WATCHING)

            fixture.repository.removeFromList(releaseId)

            assertNull(fixture.repository.membershipOf(releaseId)?.status)
            assertEquals(listOf(releaseId), fixture.repository.locallyEditedReleases.value)
        }

    @Test
    fun favoriteToggle_isVisibleInObservedMembership() =
        runTest {
            val fixture = fixture()

            fixture.repository.addFavorite(releaseId)
            assertTrue(fixture.repository.membershipOf(releaseId)?.isFavorite == true)

            fixture.repository.removeFavorite(releaseId)
            assertFalse(fixture.repository.membershipOf(releaseId)?.isFavorite == true)
            assertEquals(listOf(releaseId), fixture.repository.locallyEditedReleases.value)
        }

    @Test
    fun rapidStatusChanges_lastOneWins_andReleaseIsListedOnce() =
        runTest {
            // Быстрые последовательные смены не должны ни «застревать» на промежуточном статусе,
            // ни размножать релиз в списке правок (иначе вкладка показала бы дубли карточек).
            val fixture = fixture()

            fixture.repository.addToList(ListStatus.PLANNED, releaseId)
            fixture.repository.addToList(ListStatus.COMPLETED, releaseId)
            fixture.repository.addToList(ListStatus.DROPPED, releaseId)

            assertEquals(ListStatus.DROPPED, fixture.repository.membershipOf(releaseId)?.status)
            assertEquals(listOf(releaseId), fixture.repository.locallyEditedReleases.value)
        }

    @Test
    fun editedReleases_areOrderedMostRecentFirst() =
        runTest {
            val fixture = fixture()

            fixture.repository.addToList(ListStatus.PLANNED, releaseId)
            fixture.repository.addToList(ListStatus.PLANNED, releaseId = 1)
            // Повторная правка первого релиза поднимает его наверх, а не добавляет второй раз.
            fixture.repository.addToList(ListStatus.COMPLETED, releaseId)

            assertEquals(listOf(releaseId, 1), fixture.repository.locallyEditedReleases.value)
        }

    // ---- Гость и офлайн ---------------------------------------------------------------------

    @Test
    fun offline_serverUnavailable_stillMovesReleaseLocally_andKeepsOperationQueued() =
        runTest {
            // 503 — retryable (см. SyncQueueWorker.classify): операция остаётся в очереди до
            // следующего дрейна, но вкладки обязаны переложить карточку уже сейчас.
            val fixture = fixture(status = HttpStatusCode.ServiceUnavailable)
            fixture.membership.seedStatus(releaseId, ListStatus.WATCHING)

            fixture.repository.addToList(ListStatus.PLANNED, releaseId)

            assertEquals(ListStatus.PLANNED, fixture.repository.membershipOf(releaseId)?.status)
            assertEquals(listOf(releaseId), fixture.repository.locallyEditedReleases.value)
            assertFalse(fixture.queue.snapshot().isEmpty())
        }

    @Test
    fun guest_unauthorized_doesNotThrow_andKeepsLocalMembership() =
        runTest {
            // У гостя (нет сессии) сервер отвечает 401 — дрейн останавливается, но локальная
            // запись остаётся: экран не должен ни падать, ни откатывать выбор пользователя.
            val fixture = fixture(status = HttpStatusCode.Unauthorized)

            fixture.repository.addToList(ListStatus.WATCHING, releaseId)

            assertEquals(ListStatus.WATCHING, fixture.repository.membershipOf(releaseId)?.status)
        }

    // ---- Серверные страницы наполняют БД ----------------------------------------------------

    @Test
    fun listPaginator_seedsMembershipWithTabStatus_evenWhenDtoOmitsIt() =
        runTest {
            // `profile/list/all/{status}` по определению отдаёт релизы ИМЕННО этого списка,
            // поэтому статус берётся из вкладки, а не из (возможно отсутствующего) поля DTO —
            // иначе вкладка сочла бы свои же элементы «не в списке» и показала бы пустоту.
            val fixture = fixture(routes = mapOf("/profile/list/all/2/0" to pageJson(profileListStatus = null)))

            fixture.repository.listPaginator(ListStatus.PLANNED).loadNext()

            assertEquals(ListStatus.PLANNED, fixture.repository.membershipOf(releaseId)?.status)
        }

    @Test
    fun listPaginator_cachesReleases_soMovedCardCanBeRenderedWithoutRefetch() =
        runTest {
            val fixture = fixture(routes = mapOf("/profile/list/all/1/0" to pageJson(profileListStatus = 1)))

            fixture.repository.listPaginator(ListStatus.WATCHING).loadNext()

            val cached = fixture.repository.cachedReleases(listOf(releaseId))
            assertEquals("Тестовый релиз", cached[releaseId]?.title)
        }

    @Test
    fun favoritesPaginator_seedsFavoriteFlag() =
        runTest {
            val fixture = fixture(routes = mapOf("/favorite/all/0" to pageJson(isFavorite = true)))

            fixture.repository.favoritesPaginator().loadNext()

            assertTrue(fixture.repository.membershipOf(releaseId)?.isFavorite == true)
        }

    @Test
    fun serverPage_doesNotOverrideOptimisticStatus() =
        runTest {
            // Гонка «оптимистичная запись ↔ ответ сервера»: страница «Смотрю» всё ещё содержит
            // релиз, который пользователь только что переложил в «В планах» (запрос на сервер мог
            // не уйти — офлайн). Локальная правда обязана победить, иначе карточка прыгала бы
            // обратно в старую вкладку на каждую подгрузку страницы.
            val fixture = fixture(routes = mapOf("/profile/list/all/1/0" to pageJson(profileListStatus = 1)))
            fixture.repository.addToList(ListStatus.PLANNED, releaseId)

            fixture.repository.listPaginator(ListStatus.WATCHING).loadNext()

            assertEquals(ListStatus.PLANNED, fixture.repository.membershipOf(releaseId)?.status)
        }

    @Test
    fun listPaginator_repairsStaleMembershipRow_writtenByAnotherListing() =
        runTest {
            // Строку могло завести какое-то другое место (листинг каталога/расписание) в момент,
            // когда релиз ещё не был в списке, либо список поменяли на ДРУГОМ устройстве.
            // `INSERT OR IGNORE` такую строку не чинит, поэтому вкладка «Смотрю» показала бы
            // пустоту вместо собственного же элемента — страница обязана её поправить.
            val fixture = fixture(routes = mapOf("/profile/list/all/1/0" to pageJson(profileListStatus = null)))
            fixture.membership.seedStatus(releaseId, null)

            fixture.repository.listPaginator(ListStatus.WATCHING).loadNext()

            assertEquals(ListStatus.WATCHING, fixture.repository.membershipOf(releaseId)?.status)
        }

    @Test
    fun listPaginator_doesNotRepairReleaseWithQueuedLocalEdit() =
        runTest {
            // Правка пережила перезапуск приложения: в памяти про неё уже никто не помнит, но она
            // лежит в офлайн-очереди — сервер о ней ещё не знает, и его ответ заведомо устарел.
            val fixture = fixture(routes = mapOf("/profile/list/all/1/0" to pageJson(profileListStatus = 1)))
            fixture.membership.seedStatus(releaseId, ListStatus.PLANNED)
            fixture.queue.enqueue(
                SyncOperation(
                    id = 0,
                    kind = SyncOperationKind.LIST_SET_STATUS,
                    entityKey = "list:$releaseId",
                    releaseId = releaseId,
                    sourceId = null,
                    position = null,
                    statusApiValue = ListStatus.PLANNED.apiValue,
                    boolArg = null,
                    createdAt = now,
                    updatedAt = now,
                    attemptCount = 0,
                    // Далёкая попытка — операция не «созрела», дрейн внутри страницы её не тронет.
                    nextAttemptAt = Instant.fromEpochMilliseconds(Long.MAX_VALUE / 2),
                    lastError = null,
                ),
            )

            fixture.repository.listPaginator(ListStatus.WATCHING).loadNext()

            assertEquals(ListStatus.PLANNED, fixture.repository.membershipOf(releaseId)?.status)
        }

    @Test
    fun observeCachedReleases_emptyIds_emitsEmptyMap() =
        runTest {
            assertTrue(fixture().repository.cachedReleases(emptyList()).isEmpty())
        }
}

/** Локальное членство релиза «прямо сейчас» ([ListMembership], `null` — БД про него не знает). */
private suspend fun LibraryRepository.membershipOf(releaseId: ReleaseId) = observeListMemberships().first()[releaseId]

/** Снимок закэшированных релизов по [ids] — то, чем экран рисует переложенную карточку. */
private suspend fun LibraryRepository.cachedReleases(ids: List<ReleaseId>) = observeCachedReleases(ids).first()
