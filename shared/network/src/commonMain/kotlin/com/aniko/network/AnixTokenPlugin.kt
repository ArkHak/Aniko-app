package com.aniko.network

import io.ktor.client.plugins.api.createClientPlugin

/** Конфигурация [AnixTokenPlugin]. */
class AnixTokenPluginConfig {
    var tokenProvider: TokenProvider = TokenProvider.Anonymous
}

/**
 * Ktor-плагин, который дописывает `?token=<...>` в каждый исходящий запрос.
 *
 * Anixart передаёт токен именно query-параметром, а не заголовком
 * (`@Query("token") String token` во всех `*Api.java`). Если параметр уже
 * выставлен вручную — плагин его не трогает.
 */
val AnixTokenPlugin = createClientPlugin("AnixTokenPlugin", ::AnixTokenPluginConfig) {
    val tokenProvider = pluginConfig.tokenProvider

    onRequest { request, _ ->
        if (!request.url.parameters.contains(ApiConfig.TOKEN_QUERY_PARAM)) {
            tokenProvider.token()?.let { token ->
                request.url.parameters.append(ApiConfig.TOKEN_QUERY_PARAM, token)
            }
        }
    }
}
