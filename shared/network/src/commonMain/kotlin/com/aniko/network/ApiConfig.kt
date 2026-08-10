package com.aniko.network

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

        /**
         * Живые JSON-сэмплы (`docs/api/samples/`, проверено 2026-08-10) показывают, что
         * реальный CDN — `s.anixmirai.com` (постеры/аватары/скриншоты), а не `static.anixart.tv`
         * (был неверный дефолт, см. P3.T10 в `docs/REELWAVE_PLAN.md`). Отдельный хост
         * `s3.anixmirai.com` используется только для озвучек (`voiceovers/...`) и здесь не
         * задействован. На практике API всегда отдаёт `image`/`avatar`/`poster` уже абсолютным
         * URL (см. KDoc `ReleaseDto.kt`), поэтому этот дефолт — чисто defensive fallback на
         * случай относительного пути от сервера, а не то, что реально используется в проде.
         */
        const val DEFAULT_STATIC_BASE_URL: String = "https://s.anixmirai.com/"

        /** Имя query-параметра, в котором Anixart ожидает токен (НЕ заголовок). */
        const val TOKEN_QUERY_PARAM: String = "token"

        const val API_VERSION_HEADER: String = "API-Version"
    }
}
