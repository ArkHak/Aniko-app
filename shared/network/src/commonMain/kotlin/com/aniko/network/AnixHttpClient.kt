package com.aniko.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
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
val AnixJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

/**
 * Собирает готовый к работе [HttpClient]: base URL, JSON, таймауты, логирование
 * и автоматическая подстановка `?token=`.
 *
 * Логирование HTTP включается только через [ApiConfig.enableLogging] (приложение выставляет его
 * лишь для debug-сборок, см. `isDebugBuild()` в `composeApp`); в release запросы не логируются.
 */
fun createAnixHttpClient(
    apiConfig: ApiConfig = ApiConfig(),
    tokenProvider: TokenProvider = TokenProvider.Anonymous,
    sessionInvalidator: SessionInvalidator = NoOpSessionInvalidator,
    json: Json = AnixJson,
): HttpClient =
    createPlatformHttpClient {
        configureAnixClient(apiConfig, tokenProvider, sessionInvalidator, json, Logger.SIMPLE)
    }

/**
 * Вся конфигурация [createAnixHttpClient], вынесенная из лямбды, чтобы тесты могли прогнать
 * ровно продовый набор плагинов (порядок, логирование, токен) поверх `MockEngine`, а не копию
 * этой конфигурации. [logger] — приёмник строк `Logging`; в проде это [Logger.SIMPLE]
 * (stdout / logcat), в тестах — накопитель. Токен из query маскирует [RedactingLogger].
 */
internal fun HttpClientConfig<*>.configureAnixClient(
    apiConfig: ApiConfig,
    tokenProvider: TokenProvider,
    sessionInvalidator: SessionInvalidator,
    json: Json,
    logger: Logger,
) {
    expectSuccess = true

    install(ContentNegotiation) {
        json(json)
    }

    // Без этого клиент не хранит и не переотправляет cookie, которые ставит ddos-guard
    // перед api-s.anixsekai.com (`__ddg*_`) — под антибот-защитой это может выглядеть как
    // подозрительный трафик и приводить к отказам даже на правильных запросах (например,
    // `auth/signIn` с верным логином/паролем). In-memory хранилище достаточно: cookie
    // ddos-guard живут в рамках сессии приложения, персистентность между запусками не нужна.
    install(HttpCookies)

    // Токен получают только запросы на origin `apiConfig.baseUrl` (issue #109): картинки CDN,
    // GitHub API и прочие абсолютные URL идут без него, см. KDoc [AnixTokenPlugin].
    install(AnixTokenPlugin) {
        this.tokenProvider = tokenProvider
        this.apiBaseUrl = apiConfig.baseUrl
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

    // Предрелизный аудит безопасности (issue #109): без флага плагин не ставится вовсе — это
    // строже, чем `LogLevel.NONE` (нет ни одного пути кода, который мог бы напечатать запрос).
    // Иначе полные URL/заголовки всех запросов уходили бы в stdout/logcat и в release-сборках.
    if (apiConfig.enableLogging) {
        install(Logging) {
            // HEADERS, а не INFO/ALL: тело ответа `auth/signIn` содержит токен.
            level = LogLevel.HEADERS
            // URL печатается на любом уровне, а токен живёт в query — поэтому санитайзер обязателен.
            this.logger = RedactingLogger(logger)
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
