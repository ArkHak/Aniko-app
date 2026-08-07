package com.aniko.data.repository

import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.ProfileListApi
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Happy-path тесты `LibraryRepository` (Фаза 6) поверх `MockEngine` — по образцу
 * `EpisodeRepositoryTest`. Каждый тест проверяет: (а) репозиторий доходит до правильного
 * пути `Api`-класса и маппит DTO в домен, (б) `code != 0` в ответе превращается в
 * `AnixError.Api` через общий `requireOk()` из `ApiCall.kt`.
 */
class LibraryRepositoryTest {
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

    private fun repository(
        expectedPath: String,
        responseBody: String,
    ): LibraryRepository {
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
        return LibraryRepository(
            profileListApi = ProfileListApi(client = httpClient),
            favoriteApi = FavoriteApi(client = httpClient),
            historyApi = HistoryApi(client = httpClient),
        )
    }

    // ---- Списки по статусу ------------------------------------------------------------

    @Test
    fun myList_happyPath_returnsMappedPage() =
        runTest {
            val repository = repository("/profile/list/all/1/0", pageableResponse())

            val page = repository.myList(ListStatus.WATCHING, page = 0)

            assertEquals(1, page.items.size)
            assertEquals(186, page.items.first().id)
            assertEquals(0, page.currentPage)
            assertEquals(3, page.totalPages)
        }

    @Test
    fun myList_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/profile/list/all/1/0", pageableResponse(code = 7))

            assertFailsWith<AnixError.Api> {
                repository.myList(ListStatus.WATCHING, page = 0)
            }
        }

    @Test
    fun addToList_happyPath_callsAddEndpoint() =
        runTest {
            val repository = repository("/profile/list/add/1/186", simpleResponse())

            repository.addToList(ListStatus.WATCHING, releaseId = 186)
        }

    @Test
    fun addToList_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/profile/list/add/1/186", simpleResponse(code = 5))

            assertFailsWith<AnixError.Api> {
                repository.addToList(ListStatus.WATCHING, releaseId = 186)
            }
        }

    @Test
    fun removeFromList_happyPath_callsDeleteEndpoint() =
        runTest {
            val repository = repository("/profile/list/delete/1/186", simpleResponse())

            repository.removeFromList(ListStatus.WATCHING, releaseId = 186)
        }

    @Test
    fun removeFromList_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/profile/list/delete/1/186", simpleResponse(code = 5))

            assertFailsWith<AnixError.Api> {
                repository.removeFromList(ListStatus.WATCHING, releaseId = 186)
            }
        }

    // ---- Избранное ----------------------------------------------------------------------

    @Test
    fun favorites_happyPath_returnsMappedPage() =
        runTest {
            val repository = repository("/favorite/all/0", pageableResponse())

            val page = repository.favorites(page = 0)

            assertEquals(1, page.items.size)
            assertEquals(true, page.items.first().isFavorite)
        }

    @Test
    fun favorites_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/favorite/all/0", pageableResponse(code = 3))

            assertFailsWith<AnixError.Api> {
                repository.favorites(page = 0)
            }
        }

    @Test
    fun addFavorite_happyPath_callsAddEndpoint() =
        runTest {
            val repository = repository("/favorite/add/186", simpleResponse())

            repository.addFavorite(releaseId = 186)
        }

    @Test
    fun addFavorite_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/favorite/add/186", simpleResponse(code = 4))

            assertFailsWith<AnixError.Api> {
                repository.addFavorite(releaseId = 186)
            }
        }

    @Test
    fun removeFavorite_happyPath_callsDeleteEndpoint() =
        runTest {
            val repository = repository("/favorite/delete/186", simpleResponse())

            repository.removeFavorite(releaseId = 186)
        }

    @Test
    fun removeFavorite_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/favorite/delete/186", simpleResponse(code = 4))

            assertFailsWith<AnixError.Api> {
                repository.removeFavorite(releaseId = 186)
            }
        }

    // ---- История просмотра ---------------------------------------------------------------

    @Test
    fun history_happyPath_returnsMappedPage() =
        runTest {
            val repository = repository("/history/0", pageableResponse())

            val page = repository.history(page = 0)

            assertEquals(1, page.items.size)
            assertEquals(186, page.items.first().id)
        }

    @Test
    fun history_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/history/0", pageableResponse(code = 2))

            assertFailsWith<AnixError.Api> {
                repository.history(page = 0)
            }
        }

    @Test
    fun addHistory_happyPath_callsAddEndpoint() =
        runTest {
            val repository = repository("/history/add/186/8/1", simpleResponse())

            repository.addHistory(releaseId = 186, sourceId = 8, position = 1)
        }

    @Test
    fun addHistory_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/history/add/186/8/1", simpleResponse(code = 1))

            assertFailsWith<AnixError.Api> {
                repository.addHistory(releaseId = 186, sourceId = 8, position = 1)
            }
        }

    @Test
    fun removeFromHistory_happyPath_callsDeleteEndpoint() =
        runTest {
            val repository = repository("/history/delete/186", simpleResponse())

            repository.removeFromHistory(releaseId = 186)
        }

    @Test
    fun removeFromHistory_nonZeroCode_throwsAnixErrorApi() =
        runTest {
            val repository = repository("/history/delete/186", simpleResponse(code = 1))

            assertFailsWith<AnixError.Api> {
                repository.removeFromHistory(releaseId = 186)
            }
        }
}
