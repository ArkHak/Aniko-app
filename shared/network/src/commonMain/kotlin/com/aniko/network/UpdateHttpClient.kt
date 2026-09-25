package com.aniko.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.UserAgent

/**
 * HTTP-клиент для проверки и загрузки обновлений приложения (GitHub Releases).
 *
 * Сознательно «голый» — БЕЗ [AnixTokenPlugin], cookie и `ContentNegotiation`: токен сессии Anixart
 * не должен уходить ни на один хост, кроме API Anixart. `expectSuccess = false` — коды ответа
 * разбирает вызывающий код (404 репозитория, лимит GitHub 403/429 и т.п. — разные причины).
 *
 * Таймаут запроса не ограничен (загрузка установщика на 170 МБ идёт минутами); короткие запросы
 * (список релизов) задают свой `timeout {}` на месте. GitHub API требует заголовок `User-Agent`.
 */
fun createUpdateHttpClient(): HttpClient =
    createPlatformHttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = UPDATE_CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = UPDATE_SOCKET_TIMEOUT_MILLIS
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
        install(UserAgent) { agent = UPDATE_USER_AGENT }
    }

private const val UPDATE_CONNECT_TIMEOUT_MILLIS = 15_000L
private const val UPDATE_SOCKET_TIMEOUT_MILLIS = 60_000L
private const val UPDATE_USER_AGENT = "Aniko-Updater"
