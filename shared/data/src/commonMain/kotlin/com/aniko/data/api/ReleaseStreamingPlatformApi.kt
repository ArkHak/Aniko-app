package com.aniko.data.api

import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseStreamingPlatformDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * `ReleaseStreamingPlatformApi` — легальные стриминг-площадки конкретного релиза (карточка
 * Title Detail, не завязано на гео — см. KDoc [ReleaseStreamingPlatformDto]).
 */
class ReleaseStreamingPlatformApi(
    private val client: HttpClient,
) {
    /**
     * `GET release/streaming/platform/{releaseId}` — без токена, обычная пагинируемая обёртка.
     * Сверено вживую 2026-09-23: пустой релиз без легальных площадок отдаёт `content: []`,
     * не ошибку (см. сэмпл `release/streaming/platform/20223` в KDoc [ReleaseStreamingPlatformDto]).
     */
    suspend fun streamingPlatforms(releaseId: Int): PageableResponseDto<ReleaseStreamingPlatformDto> =
        apiCall {
            client
                .get("release/streaming/platform/$releaseId")
                .body<PageableResponseDto<ReleaseStreamingPlatformDto>>()
                .requireOk()
        }
}
