package com.anixkmp.data.api

import com.anixkmp.data.dto.InterestingDto
import com.anixkmp.data.dto.PageableResponseDto
import com.anixkmp.data.dto.ReleaseDto
import com.anixkmp.data.dto.ReleaseResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

/** `ReleaseApi` + `DiscoverApi` — карточка релиза и подборки главного экрана. */
class ReleaseApi(private val client: HttpClient) {

    /** `GET release/{r_id}?extended_mode=` */
    suspend fun release(releaseId: Int, extendedMode: Boolean = false): ReleaseResponseDto = apiCall {
        client.get("release/$releaseId") {
            parameter("extended_mode", extendedMode)
        }.body<ReleaseResponseDto>().requireOk()
    }

    /** `GET release/random?extended_mode=` */
    suspend fun random(extendedMode: Boolean = false): ReleaseResponseDto = apiCall {
        client.get("release/random") {
            parameter("extended_mode", extendedMode)
        }.body<ReleaseResponseDto>().requireOk()
    }

    /** `POST discover/watching/{page}` — «продолжить смотреть». */
    suspend fun discoverWatching(page: Int): PageableResponseDto<ReleaseDto> = apiCall {
        client.post("discover/watching/$page")
            .body<PageableResponseDto<ReleaseDto>>()
            .requireOk()
    }

    /** `POST discover/recommendations/{page}?previous_page=` */
    suspend fun discoverRecommendations(page: Int, previousPage: Int = 0): PageableResponseDto<ReleaseDto> =
        apiCall {
            client.post("discover/recommendations/$page") {
                parameter("previous_page", previousPage)
            }.body<PageableResponseDto<ReleaseDto>>().requireOk()
        }

    /**
     * `POST discover/interesting` — публичный эндпоинт (без `token`, подтверждено и статически
     * в `DiscoverApi.java` — у `interesting()` нет `@Query("token")`, — и вживую: запрос без
     * токена вернул `200`, см. `docs/api/samples/discover_interesting.json`).
     */
    suspend fun discoverInteresting(): PageableResponseDto<InterestingDto> = apiCall {
        client.post("discover/interesting")
            .body<PageableResponseDto<InterestingDto>>()
            .requireOk()
    }
}
