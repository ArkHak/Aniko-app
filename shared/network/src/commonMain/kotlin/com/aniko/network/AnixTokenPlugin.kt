package com.aniko.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLBuilder
import io.ktor.http.Url

/** Конфигурация [AnixTokenPlugin]. */
class AnixTokenPluginConfig {
    var tokenProvider: TokenProvider = TokenProvider.Anonymous

    /**
     * Базовый URL API Anixart ([ApiConfig.baseUrl]). Токен дописывается только в запросы на этот
     * origin (схема + хост + порт); запросы на любой другой адрес — CDN картинок, GitHub API для
     * проверки обновлений, embed-плееры — уходят без токена.
     *
     * Обязателен: без него плагин падает при установке. Молча слать токен сессии «на любой хост»
     * (как было до предрелизного аудита, issue #109) нельзя — при ошибке в конфигурации это
     * должно быть видно сразу, а не утечь на чужой сервер.
     */
    var apiBaseUrl: String? = null
}

/**
 * Ktor-плагин, который дописывает `?token=<...>` в исходящие запросы **на API Anixart**.
 *
 * Anixart передаёт токен именно query-параметром, а не заголовком
 * (`@Query("token") String token` во всех `*Api.java`). Если параметр уже
 * выставлен вручную — плагин его не трогает.
 *
 * Токен — секрет сессии, поэтому он получает только origin из [AnixTokenPluginConfig.apiBaseUrl]
 * (см. [ApiOrigin]). Абсолютные URL на другие хосты (постеры на `s.anixmirai.com`, отдельный клиент
 * проверки обновлений на `api.github.com` и т.п.) токена не получают, даже если по ошибке пойдут
 * через этот клиент; тот же origin по `http://` или на другом порту — тоже.
 *
 * Относительные запросы (`release/1`) плагин видит уже после `defaultRequest` (тот дописывает
 * базовый URL раньше в конвейере запроса), поэтому для них `request.url` — это адрес API.
 * Порядок защищён тестом `AnixTokenPluginTest` на продовой конфигурации клиента.
 */
val AnixTokenPlugin =
    createClientPlugin("AnixTokenPlugin", ::AnixTokenPluginConfig) {
        val tokenProvider = pluginConfig.tokenProvider
        val apiOrigin =
            ApiOrigin.of(
                requireNotNull(pluginConfig.apiBaseUrl) {
                    "AnixTokenPlugin: apiBaseUrl is required - the token is only sent to the API origin"
                },
            )

        onRequest { request, _ ->
            if (!apiOrigin.matches(request.url)) return@onRequest
            if (request.url.parameters.contains(ApiConfig.TOKEN_QUERY_PARAM)) return@onRequest

            tokenProvider.token()?.let { token ->
                request.url.parameters.append(ApiConfig.TOKEN_QUERY_PARAM, token)
            }
        }
    }

/**
 * Origin (схема + хост + порт) API, которому разрешено получать токен сессии.
 *
 * Сравнивается именно origin, а не только хост: тот же хост по `http://` (токен ушёл бы открытым
 * текстом) или на нестандартном порту — другой сервер. Регистр хоста и явный дефолтный порт
 * (`:443` для `https`) нормализуются.
 */
internal data class ApiOrigin(
    val protocol: String,
    val host: String,
    val port: Int,
) {
    /** `true`, если [url] (уже с подставленным базовым адресом) указывает на этот origin. */
    fun matches(url: URLBuilder): Boolean =
        url.protocol.name.equals(protocol, ignoreCase = true) &&
            url.host.equals(host, ignoreCase = true) &&
            // `URLBuilder.port` у разобранного из строки абсолютного URL — 0 («порт не указан»),
            // а не 443: нормализуем до порта протокола сами (у `Url.port` нормализация есть).
            (url.port.takeIf { it > 0 } ?: url.protocol.defaultPort) == port

    companion object {
        /**
         * Разбирает [baseUrl] (`https://api-s.anixsekai.com/`) в origin; падает на URL без схемы
         * и хоста (`Url("/path")` молча даёт `http://localhost`, поэтому наличие схемы проверяем
         * по строке).
         */
        fun of(baseUrl: String): ApiOrigin {
            val url = Url(baseUrl)
            require(url.host.isNotEmpty() && baseUrl.startsWith("${url.protocol.name}://", ignoreCase = true)) {
                "AnixTokenPlugin: apiBaseUrl '$baseUrl' must be an absolute URL (scheme and host)"
            }
            return ApiOrigin(url.protocol.name.lowercase(), url.host.lowercase(), url.port)
        }
    }
}
