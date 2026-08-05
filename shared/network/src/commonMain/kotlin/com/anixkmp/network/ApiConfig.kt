package com.anixkmp.network

/**
 * Конфигурация сетевого слоя.
 *
 * Базовый URL — константа из `ConstantNetFetcher` оригинального APK.
 * Resiliency-цепочка (`config/urls` → Firebase → GitHub Pages) в MVP не реализуется,
 * но [baseUrl] специально вынесен в параметр, чтобы её можно было добавить,
 * не трогая ни один API-сервис.
 */
data class ApiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val staticBaseUrl: String = DEFAULT_STATIC_BASE_URL,
    /** `[TODO: verify live]` — точное значение заголовка для `search/releases/{page}`. */
    val apiVersionHeader: String? = null,
    val enableLogging: Boolean = true,
    val requestTimeoutMillis: Long = 30_000,
) {
    companion object {
        const val DEFAULT_BASE_URL: String = "https://api-s.anixsekai.com/"
        const val DEFAULT_STATIC_BASE_URL: String = "https://static.anixart.tv/"

        /** Имя query-параметра, в котором Anixart ожидает токен (НЕ заголовок). */
        const val TOKEN_QUERY_PARAM: String = "token"

        const val API_VERSION_HEADER: String = "API-Version"
    }
}
