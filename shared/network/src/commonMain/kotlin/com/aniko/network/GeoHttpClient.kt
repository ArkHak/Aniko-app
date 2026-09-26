package com.aniko.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent

/**
 * Koin-qualifier отдельного `HttpClient` для geo-IP запросов (см. [createGeoHttpClient]).
 * Регистрируется в `dataModule` рядом с основным `HttpClient` и image-клиентом.
 */
const val GEO_HTTP_CLIENT_QUALIFIER: String = "geoHttpClient"

/**
 * HTTP-клиент для определения сетевого региона пользователя (geo-IP по egress-IP).
 *
 * Сознательно «голый» — БЕЗ [AnixTokenPlugin], cookie и `ContentNegotiation`, по той же причине,
 * что и [createUpdateHttpClient]: токен сессии Anixart не должен уходить ни на один хост, кроме
 * API Anixart, а запросы идут на сторонние geo-сервисы (ipwho.is / cloudflare trace). Ответы там
 * маленькие и разбираются вручную (`bodyAsText()` + точечный парсинг), поэтому
 * `ContentNegotiation` не нужен.
 *
 * `expectSuccess = true` — оба эндпоинта либо отдают 2xx с телом, либо запрос считается
 * провалившимся: не-2xx бросает исключение, которое `GeoRegionApi` превращает в `null`
 * (fail-open: сбой определения региона НЕ должен блокировать воспроизведение). Таймауты короткие
 * (~5 с): регион — гейтинг для UI-решения «показать кнопку "Смотреть"», держать экран в ожидании
 * на плохой сети недопустимо — при таймауте сработает закэшированное значение/UNKNOWN.
 */
fun createGeoHttpClient(): HttpClient =
    createPlatformHttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = GEO_CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = GEO_SOCKET_TIMEOUT_MILLIS
            requestTimeoutMillis = GEO_REQUEST_TIMEOUT_MILLIS
        }
        install(UserAgent) { agent = GEO_USER_AGENT }
    }

private const val GEO_CONNECT_TIMEOUT_MILLIS = 5_000L
private const val GEO_SOCKET_TIMEOUT_MILLIS = 5_000L
private const val GEO_REQUEST_TIMEOUT_MILLIS = 5_000L
private const val GEO_USER_AGENT = "Aniko-Geo"
