package com.aniko.data.api

import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseDto
import com.aniko.data.dto.SearchRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * `SearchApi` — поиск релизов (`search/releases/{page}`, `docs/api/ENDPOINTS.md`).
 *
 * `token` в тело/query добавлять не нужно — [com.aniko.network.AnixTokenPlugin] дописывает
 * его в каждый запрос автоматически (тот же паттерн, что и у [ReleaseApi]/[ProfileListApi]).
 *
 * Заголовок `API-Version`: живая проверка (R3, см. `ApiConfig.apiVersionHeader`) показала, что
 * сервер отвечает `200` и без него, поэтому здесь он не проставляется явно — если
 * [com.aniko.network.ApiConfig.apiVersionHeader] когда-нибудь станет не-`null`, он всё равно
 * долетит через `defaultRequest` в `createAnixHttpClient`.
 *
 * Ответ: живая проверка показала, что `search/releases/{page}` отдаёт `PageableResponse<Release>`
 * (`code`, `content`, `current_page`, `total_page_count`, `total_count`) — ТО ЖЕ САМОЕ, что и
 * путями `discover/…`. Это расходится с decompiled `ReleaseSearchResponse.java` (там форма
 * `{ related, releases }` без пагинации), но по правилам R3 живой трафик приоритетнее
 * статического анализа APK — вероятно, эндпоинт на сервере изменился после версии 9.0-beta-19.
 */
class SearchApi(
    private val client: HttpClient,
) {
    /** `POST search/releases/{page}`, body `SearchRequest`. */
    suspend fun releaseSearch(
        page: Int,
        query: String,
        searchBy: Int = 0,
    ): PageableResponseDto<ReleaseDto> =
        apiCall {
            client
                .post("search/releases/$page") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequestDto(query = query, searchBy = searchBy))
                }.body<PageableResponseDto<ReleaseDto>>()
                .requireOk()
        }
}
