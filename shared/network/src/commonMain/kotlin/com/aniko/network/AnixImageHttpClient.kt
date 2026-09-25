package com.aniko.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/**
 * Koin-qualifier отдельного `HttpClient` для загрузки картинок (см. [createAnixImageHttpClient]).
 * Регистрируется в `dataModule` рядом с основным `HttpClient`.
 */
const val IMAGE_HTTP_CLIENT_QUALIFIER: String = "imageHttpClient"

/**
 * Отдельный `HttpClient` только для CDN-статики (постеры/аватары/скриншоты через Coil).
 *
 * Основной [createAnixHttpClient] для картинок не подходит, и это не косметика:
 * - `AnixTokenPlugin` дописывает `?token=` в запросы на API. С origin-ограничением (issue #109)
 *   абсолютные URL CDN токена через основной клиент уже не получают, но URL картинки, попавшей
 *   на хост API, начал бы зависеть от токена сессии: ключи дискового/edge-кэша Coil протухали бы
 *   при каждой смене токена (весь кэш перекачивался бы заново), а CDN лишался бы шанса отдать
 *   публичную статику из своего edge-кэша. Отдельный клиент исключает это структурно;
 * - `HttpResponseValidator` на 401/403 вызывает `sessionInvalidator.onUnauthorized()` —
 *   протухшая или недоступная картинка с CDN разлогинила бы пользователя;
 * - `Logging` (HEADERS) при включённом [ApiConfig.enableLogging] (debug-сборка) печатал бы
 *   заголовки на каждый постер списка/сетки;
 * - `ContentNegotiation` и `HttpCookies` нужны API, картинкам — нет;
 * - таймауты 30с на каждую обложку: на плохой сети UI «висит» полминуты на скелетоне
 *   вместо быстрого показа плейсхолдера.
 *
 * Поэтому здесь сознательно «голый» клиент: никаких плагинов, кроме [HttpTimeout]
 * с короткими лимитами — обложка либо прилетает быстро, либо Coil показывает
 * плейсхолдер и откладывает повтор через свой `RetryStrategy`. `expectSuccess` не
 * выставляем: коды ответа Coil разбирает сам. Engine — тот же платформенный
 * [createPlatformHttpClient] (OkHttp / Darwin), что и у основного клиента.
 */
fun createAnixImageHttpClient(
    connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
    requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
): HttpClient =
    createPlatformHttpClient {
        install(HttpTimeout) {
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
            this.requestTimeoutMillis = requestTimeoutMillis
        }
    }

private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000L
private const val DEFAULT_SOCKET_TIMEOUT_MILLIS = 10_000L
private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 15_000L
