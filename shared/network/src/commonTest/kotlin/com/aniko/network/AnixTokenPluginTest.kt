package com.aniko.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Предрелизный аудит безопасности (issue #109, п. 3): токен сессии получают только запросы на
 * origin API, а не любой хост. Клиент собирается продовой [configureAnixClient] поверх
 * `MockEngine`, так что заодно проверяется порядок `defaultRequest` → `AnixTokenPlugin`
 * для относительных путей.
 */
class AnixTokenPluginTest {
    private val secret = "b3f1c9d2e8a74f60"

    private val silentLogger =
        object : Logger {
            override fun log(message: String) = Unit
        }

    /** Выполняет [path] на продовом клиенте и возвращает URL, который реально ушёл в engine. */
    private suspend fun sentUrl(
        path: String,
        apiConfig: ApiConfig = ApiConfig(),
        token: String? = secret,
    ): Url {
        var sent: Url? = null
        val httpClient =
            HttpClient(
                MockEngine { request ->
                    sent = request.url
                    respond("{}", HttpStatusCode.OK)
                },
            ) {
                configureAnixClient(apiConfig, TokenProvider { token }, NoOpSessionInvalidator, AnixJson, silentLogger)
            }
        try {
            httpClient.get(path)
        } finally {
            httpClient.close()
        }
        return checkNotNull(sent) { "Запрос не дошёл до engine: $path" }
    }

    private suspend fun assertGetsToken(
        path: String,
        apiConfig: ApiConfig = ApiConfig(),
    ) {
        val url = sentUrl(path, apiConfig)
        assertEquals(secret, url.parameters[ApiConfig.TOKEN_QUERY_PARAM], "На $url должен быть токен")
    }

    private suspend fun assertNoToken(
        path: String,
        apiConfig: ApiConfig = ApiConfig(),
    ) {
        val url = sentUrl(path, apiConfig)
        assertNull(url.parameters[ApiConfig.TOKEN_QUERY_PARAM], "Токен сессии утёк на $url")
    }

    @Test
    fun relativeRequest_toApi_getsToken() =
        runTest {
            val url = sentUrl("release/1")

            assertEquals("api-s.anixsekai.com", url.host)
            assertEquals(secret, url.parameters[ApiConfig.TOKEN_QUERY_PARAM])
        }

    @Test
    fun absoluteRequest_toApiHost_getsToken() =
        runTest {
            assertGetsToken("https://api-s.anixsekai.com/release/1")
        }

    @Test
    fun apiHost_isMatchedCaseInsensitively_andWithExplicitDefaultPort() =
        runTest {
            assertGetsToken("https://API-S.AnixSekai.com/release/1")
            assertGetsToken("https://api-s.anixsekai.com:443/release/1")
        }

    @Test
    fun cdnImageRequest_getsNoToken() =
        runTest {
            assertNoToken("https://s.anixmirai.com/posters/Y2LUIz7fEG66wTZCl5kEzWQw9MGFDl.jpg")
            assertNoToken("https://s3.anixmirai.com/voiceovers/x.mp3")
        }

    @Test
    fun githubApiRequest_getsNoToken() =
        runTest {
            // Отдельный клиент проверки обновлений ходит на api.github.com и не должен получить
            // токен Anixart, даже если его случайно сделают через этот HttpClient.
            assertNoToken("https://api.github.com/repos/ArkHak/Aniko-app/releases/latest")
        }

    @Test
    fun lookalikeHosts_getNoToken() =
        runTest {
            listOf(
                "https://api-s.anixsekai.com.evil.example/release/1",
                "https://evil-api-s.anixsekai.com/release/1",
                "https://sub.api-s.anixsekai.com/release/1",
                "https://evil.example/release/1?h=api-s.anixsekai.com",
                // userinfo-трюк: настоящий хост здесь evil.example
                "https://api-s.anixsekai.com@evil.example/release/1",
            ).forEach { assertNoToken(it) }
        }

    @Test
    fun sameHost_overHttp_getsNoToken() =
        runTest {
            // Токен не должен уходить открытым текстом, даже если хост совпал.
            assertNoToken("http://api-s.anixsekai.com/release/1")
        }

    @Test
    fun sameHost_onOtherPort_getsNoToken() =
        runTest {
            assertNoToken("https://api-s.anixsekai.com:8443/release/1")
        }

    @Test
    fun manuallySetToken_isKeptUntouched_onApi() =
        runTest {
            val url = sentUrl("release/1?token=manual")

            assertEquals(listOf("manual"), url.parameters.getAll(ApiConfig.TOKEN_QUERY_PARAM))
        }

    @Test
    fun anonymousSession_getsNoToken_onApi() =
        runTest {
            val url = sentUrl("release/1", token = null)

            assertNull(url.parameters[ApiConfig.TOKEN_QUERY_PARAM])
        }

    @Test
    fun customBaseUrl_definesTheTokenOrigin() =
        runTest {
            val staging = ApiConfig(baseUrl = "https://staging.example.test/")

            assertGetsToken("release/1", staging)
            assertNoToken("https://api-s.anixsekai.com/release/1", staging)
        }

    @Test
    fun pluginWithoutApiBaseUrl_failsFast() {
        assertFailsWith<IllegalArgumentException> {
            HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) {
                install(AnixTokenPlugin)
            }
        }
    }

    @Test
    fun apiOrigin_isParsedFromBaseUrl() {
        assertEquals(ApiOrigin("https", "api-s.anixsekai.com", 443), ApiOrigin.of(ApiConfig.DEFAULT_BASE_URL))
        assertEquals(ApiOrigin("http", "localhost", 8080), ApiOrigin.of("http://LocalHost:8080/api/"))
        assertFailsWith<IllegalArgumentException> { ApiOrigin.of("/relative/only") }
    }
}
