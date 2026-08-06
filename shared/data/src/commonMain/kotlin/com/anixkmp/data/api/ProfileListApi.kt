package com.anixkmp.data.api

import com.anixkmp.data.dto.PageableResponseDto
import com.anixkmp.data.dto.ReleaseDto
import com.anixkmp.data.dto.SimpleResponseDto
import com.anixkmp.model.ListStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * `ProfileListApi` — списки пользователя по статусу (смотрю/в планах/просмотрено/...).
 *
 * Избранное переехало в `FavoriteApi`, история — в `HistoryApi` (Фаза 6): изначально все три
 * были вперемешку в этом классе, но у Anixart это разные REST-неймспейсы (пути вида
 * `profile/list/...`, `favorite/...`, `history/...`), так что разделение точнее отражает
 * реальный API.
 */
class ProfileListApi(private val client: HttpClient) {

    /** `GET profile/list/all/{status}/{page}?sort=&filter_announce=` */
    suspend fun myList(
        status: ListStatus,
        page: Int,
        sort: Int = 0,
        filterAnnounce: Int = 0,
    ): PageableResponseDto<ReleaseDto> = apiCall {
        client.get("profile/list/all/${status.apiValue}/$page") {
            parameter("sort", sort)
            parameter("filter_announce", filterAnnounce)
        }.body<PageableResponseDto<ReleaseDto>>().requireOk()
    }

    /** `GET profile/list/add/{status}/{r_id}` */
    suspend fun addToList(status: ListStatus, releaseId: Int): SimpleResponseDto = apiCall {
        client.get("profile/list/add/${status.apiValue}/$releaseId")
            .body<SimpleResponseDto>()
            .requireOk()
    }

    /** `GET profile/list/delete/{status}/{r_id}` */
    suspend fun removeFromList(status: ListStatus, releaseId: Int): SimpleResponseDto = apiCall {
        client.get("profile/list/delete/${status.apiValue}/$releaseId")
            .body<SimpleResponseDto>()
            .requireOk()
    }
}
