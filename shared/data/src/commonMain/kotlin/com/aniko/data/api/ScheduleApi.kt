package com.aniko.data.api

import com.aniko.data.dto.ScheduleResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** `ScheduleApi` — расписание выхода эпизодов по дням недели. */
class ScheduleApi(
    private val client: HttpClient,
) {
    /** `GET schedule` — без токена и параметров, см. KDoc `ScheduleResponseDto`. */
    suspend fun schedule(): ScheduleResponseDto =
        apiCall {
            client
                .get("schedule")
                .body<ScheduleResponseDto>()
                .requireOk()
        }
}
