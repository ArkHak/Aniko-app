package com.aniko.data.api

import com.aniko.data.dto.ProfileResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** `ProfileApi` — карточка профиля (см. `profile/{id}` в `ProfileApi.java` decompiled). */
class ProfileApi(private val client: HttpClient) {

    /** `GET profile/{id}` */
    suspend fun profile(id: Long): ProfileResponseDto = apiCall {
        client.get("profile/$id").body<ProfileResponseDto>().requireOk()
    }
}
