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
    /**
     * Проверено вживую (R3): `POST search/releases/0` с валидным телом `SearchRequest` и БЕЗ
     * заголовка `API-Version` вернул `HTTP 200` с корректными данными (см.
     * `docs/api/samples/search_releases_page0_no_api_version_header.json`). Несмотря на то что
     * decompiled `SearchApi.java` объявляет параметр без дефолта (Android-клиент всегда его
     * шлёт), сервер его не требует. Оставлено `null` осознанно, а не потому что не проверено.
     */
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
