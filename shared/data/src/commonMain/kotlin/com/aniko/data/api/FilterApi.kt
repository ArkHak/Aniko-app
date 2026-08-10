package com.aniko.data.api

import com.aniko.data.dto.FilterRequestDto
import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * `FilterApi` — расширенный фильтр каталога (`filter/{page}`, `docs/api/ENDPOINTS.md`).
 *
 * `token` в тело/query добавлять не нужно — [com.aniko.network.AnixTokenPlugin] дописывает
 * его в каждый запрос автоматически (тот же паттерн, что и у [SearchApi]/[ReleaseApi]).
 *
 * Живая проверка (2026-08-10): `POST filter/0` с телом `{"sort":0,"genres":[],"types":[],
 * "age_ratings":[],"profile_list_exclusions":[]}` → HTTP 200, обычный
 * `PageableResponse<Release>` — см. KDoc [FilterRequestDto].
 */
class FilterApi(
    private val client: HttpClient,
) {
    /** `POST filter/{page}?extended_mode=`, body `FilterRequest`. См. KDoc [FilterRequestDto]. */
    suspend fun filter(
        page: Int,
        request: FilterRequestDto,
        extendedMode: Boolean = false,
    ): PageableResponseDto<ReleaseDto> =
        apiCall {
            client
                .post("filter/$page") {
                    contentType(ContentType.Application.Json)
                    parameter("extended_mode", extendedMode)
                    setBody(request)
                }.body<PageableResponseDto<ReleaseDto>>()
                .requireOk()
        }
}
