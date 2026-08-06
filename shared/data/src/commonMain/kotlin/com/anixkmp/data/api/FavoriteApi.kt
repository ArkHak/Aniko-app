package com.anixkmp.data.api

import com.anixkmp.data.dto.PageableResponseDto
import com.anixkmp.data.dto.ReleaseDto
import com.anixkmp.data.dto.SimpleResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** `FavoriteApi` — избранные релизы пользователя. */
class FavoriteApi(private val client: HttpClient) {

    /** `GET favorite/all/{page}?sort=&filter_announce=` */
    suspend fun favorites(page: Int, sort: Int = 0, filterAnnounce: Int = 0): PageableResponseDto<ReleaseDto> =
        apiCall {
            client.get("favorite/all/$page") {
                parameter("sort", sort)
                parameter("filter_announce", filterAnnounce)
            }.body<PageableResponseDto<ReleaseDto>>().requireOk()
        }

    /** `GET favorite/add/{r_id}` */
    suspend fun addFavorite(releaseId: Int): SimpleResponseDto = apiCall {
        client.get("favorite/add/$releaseId").body<SimpleResponseDto>().requireOk()
    }

    /** `GET favorite/delete/{r_id}` */
    suspend fun removeFavorite(releaseId: Int): SimpleResponseDto = apiCall {
        client.get("favorite/delete/$releaseId").body<SimpleResponseDto>().requireOk()
    }
}
