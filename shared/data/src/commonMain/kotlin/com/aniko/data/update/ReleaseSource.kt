package com.aniko.data.update

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Откуда приложение узнаёт о новых версиях. Интерфейс — ради тестов и запасных источников. */
fun interface ReleaseSource {
    /** Опубликованные (не черновики) релизы с версией SemVer; порядок не гарантируется. */
    suspend fun fetchReleases(): List<AppRelease>
}

/**
 * Релизы публичного репозитория Aniko на GitHub, без авторизации.
 *
 * Берётся СПИСОК релизов, а не `/releases/latest`: последний возвращает только не-pre-release, а
 * все релизы `0.x` помечены как pre-release. Лимит GitHub без токена — 60 запросов в час на IP;
 * приложение проверяет раз в сутки, так что до лимита не доходит, а 403/429 всё равно разбираются
 * ([UpdateError.RateLimited]). Пока репозиторий приватный, ответ — 404 ([UpdateError.NotFound]).
 */
class GitHubReleaseSource(
    private val client: HttpClient,
    private val releasesUrl: String = DEFAULT_RELEASES_URL,
    private val fallbackPageUrl: String = DEFAULT_PAGE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReleaseSource {
    override suspend fun fetchReleases(): List<AppRelease> {
        val dtos =
            try {
                json.decodeFromString(ListSerializer(GitHubReleaseDto.serializer()), requestBody())
            } catch (e: SerializationException) {
                throw UpdateException(UpdateError.Server, e)
            }
        return dtos.mapNotNull { it.toAppRelease(fallbackPageUrl) }
    }

    private suspend fun requestBody(): String {
        val response = send()
        if (response.status.isSuccess()) return response.bodyAsText()
        throw UpdateException(errorFor(response.status.value))
    }

    /** Запрос; сетевые сбои становятся [UpdateError.Network], отмена корутины пробрасывается как есть. */
    private suspend fun send(): HttpResponse =
        runCatching {
            client.get(releasesUrl) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", API_VERSION)
                parameter("per_page", PAGE_SIZE)
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
            }
        }.getOrElse { throw networkFailure(it) }

    private fun networkFailure(cause: Throwable): Throwable =
        when (cause) {
            is CancellationException -> cause
            is HttpRequestTimeoutException, is IOException -> UpdateException(UpdateError.Network, cause)
            else -> cause
        }

    private fun errorFor(status: Int): UpdateError =
        when {
            status == HTTP_NOT_FOUND -> UpdateError.NotFound
            status == HTTP_FORBIDDEN || status == HTTP_TOO_MANY_REQUESTS -> UpdateError.RateLimited
            status >= HTTP_SERVER_ERROR -> UpdateError.Server
            else -> UpdateError.Unknown
        }

    companion object {
        const val DEFAULT_RELEASES_URL = "https://api.github.com/repos/ArkHak/Aniko-app/releases"
        const val DEFAULT_PAGE_URL = "https://github.com/ArkHak/Aniko-app/releases"
        private const val API_VERSION = "2022-11-28"
        private const val PAGE_SIZE = 10
        private const val REQUEST_TIMEOUT_MILLIS = 20_000L
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
