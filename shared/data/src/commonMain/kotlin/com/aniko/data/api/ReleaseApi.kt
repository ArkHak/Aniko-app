package com.aniko.data.api

import com.aniko.data.dto.InterestingDto
import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseDto
import com.aniko.data.dto.ReleaseResponseDto
import com.aniko.data.dto.SimpleResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

/** `ReleaseApi` + `DiscoverApi` — карточка релиза и подборки главного экрана. */
class ReleaseApi(
    private val client: HttpClient,
) {
    /** `GET release/{r_id}?extended_mode=` */
    suspend fun release(
        releaseId: Int,
        extendedMode: Boolean = false,
    ): ReleaseResponseDto =
        apiCall {
            client
                .get("release/$releaseId") {
                    parameter("extended_mode", extendedMode)
                }.body<ReleaseResponseDto>()
                .requireOk()
        }

    /** `GET release/random?extended_mode=` */
    suspend fun random(extendedMode: Boolean = false): ReleaseResponseDto =
        apiCall {
            client
                .get("release/random") {
                    parameter("extended_mode", extendedMode)
                }.body<ReleaseResponseDto>()
                .requireOk()
        }

    /** `POST discover/watching/{page}` — «продолжить смотреть». */
    suspend fun discoverWatching(page: Int): PageableResponseDto<ReleaseDto> =
        apiCall {
            client
                .post("discover/watching/$page")
                .body<PageableResponseDto<ReleaseDto>>()
                .requireOk()
        }

    /** `POST discover/recommendations/{page}?previous_page=` */
    suspend fun discoverRecommendations(
        page: Int,
        previousPage: Int = 0,
    ): PageableResponseDto<ReleaseDto> =
        apiCall {
            client
                .post("discover/recommendations/$page") {
                    parameter("previous_page", previousPage)
                }.body<PageableResponseDto<ReleaseDto>>()
                .requireOk()
        }

    /**
     * `POST discover/interesting` — публичный эндпоинт (без `token`, подтверждено и статически
     * в `DiscoverApi.java` — у `interesting()` нет `@Query("token")`, — и вживую: запрос без
     * токена вернул `200`, см. `docs/api/samples/discover_interesting.json`).
     */
    suspend fun discoverInteresting(): PageableResponseDto<InterestingDto> =
        apiCall {
            client
                .post("discover/interesting")
                .body<PageableResponseDto<InterestingDto>>()
                .requireOk()
        }

    /**
     * `POST discover/discussing` — «обсуждаемое», замена вырезанного из v1 «Top This Week»
     * (см. P0.T3 в плане, секция Home). **Без номера страницы в пути** — живая проверка
     * (2026-08-12) показала, что `discover/discussing/1` возвращает `404`, а сам эндпоинт без
     * страницы отдаёт фиксированный набор ~5 элементов (не пагинируется).
     */
    suspend fun discoverDiscussing(): PageableResponseDto<ReleaseDto> =
        apiCall {
            client
                .post("discover/discussing")
                .body<PageableResponseDto<ReleaseDto>>()
                .requireOk()
        }

    /** `GET release/vote/add/{r_id}/{vote}?token=` — поставить/изменить свою оценку релизу (1..5). */
    suspend fun voteAdd(
        releaseId: Int,
        vote: Int,
    ): SimpleResponseDto =
        apiCall {
            client
                .get("release/vote/add/$releaseId/$vote")
                .body<SimpleResponseDto>()
                .requireOk()
        }

    /** `GET release/vote/delete/{r_id}?token=` — убрать свою оценку релизу. */
    suspend fun voteDelete(releaseId: Int): SimpleResponseDto =
        apiCall {
            client
                .get("release/vote/delete/$releaseId")
                .body<SimpleResponseDto>()
                .requireOk()
        }
}
