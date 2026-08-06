package com.aniko.data.api

import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseDto
import com.aniko.data.dto.SimpleResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * `HistoryApi` — история просмотра.
 *
 * По аналогии с `FavoriteApi`/`ProfileListApi` (см. `HistoryApi.java` в
 * `docs/api/jadx-out-21`): все три метода — `GET`, а не `POST`. Не путать с
 * `EpisodeApi.markWatched`/`markUnwatched` (`episode/watch|unwatch/...`), которые
 * действительно `POST` и отмечают конкретную серию просмотренной — `HistoryApi.add`
 * пишет запись в общую историю позиций (`r_id`/`s_id`/`position`), это отдельная сущность.
 */
class HistoryApi(private val client: HttpClient) {

    /** `GET history/{page}` */
    suspend fun history(page: Int): PageableResponseDto<ReleaseDto> = apiCall {
        client.get("history/$page").body<PageableResponseDto<ReleaseDto>>().requireOk()
    }

    /** `GET history/add/{r_id}/{s_id}/{position}` */
    suspend fun add(releaseId: Int, sourceId: Int, position: Int): SimpleResponseDto = apiCall {
        client.get("history/add/$releaseId/$sourceId/$position")
            .body<SimpleResponseDto>()
            .requireOk()
    }

    /** `GET history/delete/{r_id}` */
    suspend fun delete(releaseId: Int): SimpleResponseDto = apiCall {
        client.get("history/delete/$releaseId").body<SimpleResponseDto>().requireOk()
    }
}
