package com.anixkmp.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Единый `Json` сетевого слоя.
 *
 * `ignoreUnknownKeys = true` — обязательное требование: API Anixart недокументирован
 * и меняется без предупреждения, любое новое поле не должно ронять клиент.
 */
val AnixJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Собирает готовый к работе [HttpClient]: base URL, JSON, таймауты, логирование
 * и автоматическая подстановка `?token=`.
 */
fun createAnixHttpClient(
    apiConfig: ApiConfig = ApiConfig(),
    tokenProvider: TokenProvider = TokenProvider.Anonymous,
    sessionInvalidator: SessionInvalidator = NoOpSessionInvalidator,
    json: Json = AnixJson,
): HttpClient = createPlatformHttpClient {
    expectSuccess = true

    install(ContentNegotiation) {
        json(json)
    }

    install(AnixTokenPlugin) {
        this.tokenProvider = tokenProvider
    }

    // 401/403 от бэкенда — токен протух/отозван. Кроме самого `auth/*`: там 401/403 значит
    // «неверный логин/пароль», а не «сессия умерла», и рушить сессию из-за него нельзя.
    // Обработчик ничего не бросает и не ретраит запрос — оригинальное исключение от
    // `expectSuccess = true` пробрасывается дальше как обычно.
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, request ->
            val responseException = cause as? ResponseException ?: return@handleResponseExceptionWithRequest
            val status = responseException.response.status
            if (status != HttpStatusCode.Unauthorized && status != HttpStatusCode.Forbidden) {
                return@handleResponseExceptionWithRequest
            }

            val path = request.url.encodedPath.removePrefix("/")
            if (path.startsWith(AUTH_PATH_PREFIX)) {
                return@handleResponseExceptionWithRequest
            }

            sessionInvalidator.onUnauthorized()
        }
    }

    install(HttpTimeout) {
        requestTimeoutMillis = apiConfig.requestTimeoutMillis
        connectTimeoutMillis = apiConfig.requestTimeoutMillis
        socketTimeoutMillis = apiConfig.requestTimeoutMillis
    }

    if (apiConfig.enableLogging) {
        install(Logging) {
            // HEADERS, а не INFO/ALL: тело ответа `auth/signIn` содержит токен.
            level = LogLevel.HEADERS
            // URL печатается на любом уровне, а токен живёт в query — поэтому санитайзер обязателен.
            logger = RedactingLogger(Logger.SIMPLE)
            sanitizeHeader { header ->
                header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                    header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                    header.equals(HttpHeaders.SetCookie, ignoreCase = true)
            }
        }
    }

    defaultRequest {
        url(apiConfig.baseUrl)
        apiConfig.apiVersionHeader?.let { headers.append(ApiConfig.API_VERSION_HEADER, it) }
    }
}

/** Путь `auth/signIn` и всё, что под ним, — 401/403 там не значит «сессия умерла». */
private const val AUTH_PATH_PREFIX = "auth/"
