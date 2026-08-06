package com.aniko.data.api

import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseDto
import com.aniko.data.dto.SimpleResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** `FavoriteApi` — избранные релизы пользователя. */
class FavoriteApi(private val client: HttpClient) {

    /**
     * `GET favorite/all/{page}?sort=&filter_announce=`
     *
     * [sort] по умолчанию `1` — «сначала недавно добавленные». См. комментарий у
     * `ProfileListApi.myList` — та же (неочевидная, проверенная вживую только для списков по
     * статусу) семантика значения `sort` предполагается и здесь, так как это тот же query-параметр
     * и тот же REST-стиль API. `[TODO: verify live]` конкретно для избранного отдельно не
     * проверялось.
     */
    suspend fun favorites(page: Int, sort: Int = 1, filterAnnounce: Int = 0): PageableResponseDto<ReleaseDto> =
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
