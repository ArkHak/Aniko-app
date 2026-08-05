package com.anixkmp.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.HttpHeaders
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
    json: Json = AnixJson,
): HttpClient = createPlatformHttpClient {
    expectSuccess = true

    install(ContentNegotiation) {
        json(json)
    }

    install(AnixTokenPlugin) {
        this.tokenProvider = tokenProvider
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
