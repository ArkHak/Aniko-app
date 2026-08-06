package com.aniko.data.dto

import kotlinx.serialization.Serializable

/**
 * `SearchRequest` — тело `POST search/releases/{page}` (и остальных путей `search/…`).
 *
 * Сверено: decompiled `network/request/search/SearchRequest.java` объявляет `query: String` и
 * `searchBy: Int` БЕЗ `@JsonProperty` — конвертер там Jackson (см. `network/Response.java`,
 * `@JsonIgnoreProperties`), а значит имя JSON-поля равно имени Kotlin-свойства as-is, т.е.
 * `query`/`searchBy`, camelCase, НЕ `search_by`. Живая проверка подтвердила: тело
 * `{"query":"наруто","searchBy":0}` вернуло релевантную выдачу (см.
 * `docs/api/samples/search_releases_page0_no_api_version_header.json`).
 *
 * `searchBy` — режим поиска (по названию/жанру/студии/...), в APK его значения не расшифрованы
 * статически в этой сессии; `0` (по умолчанию в `SearchRequest(String, int)`, второй параметр
 * конструктора с default `0`) достаточен для MVP-поиска по названию.
 */
@Serializable
data class SearchRequestDto(
    val query: String,
    val searchBy: Int = 0,
)
