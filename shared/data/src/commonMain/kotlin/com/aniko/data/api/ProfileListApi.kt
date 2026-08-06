package com.aniko.data.api

import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseDto
import com.aniko.data.dto.SimpleResponseDto
import com.aniko.model.ListStatus
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

    /**
     * `GET profile/list/all/{status}/{page}?sort=&filter_announce=`
     *
     * [sort] по умолчанию `1` — «сначала недавно добавленные». Decompiled `res/values/arrays.xml`
     * (`R.array.sort`) подписывает индекс `0` как «По дате добавления ▼» и `1` как «По дате
     * добавления ▲», что наивно читается как «`0` = сначала новые», но это неверно — проверено
     * вживую (см. историю коммитов): при `sort=0` только что изменённый релиз не поднимается
     * наверх списка, а при `sort=1` — поднимается. Подписи в decompiled-ресурсах, видимо, относятся
     * к направлению сортировки по значению даты, а не к тому, что видит пользователь сверху экрана.
     */
    suspend fun myList(
        status: ListStatus,
        page: Int,
        sort: Int = 1,
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
