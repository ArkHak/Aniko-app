package com.anixkmp.data.api

import com.anixkmp.data.dto.PrivacyEditRequestDto
import com.anixkmp.data.dto.ProfilePreferenceResponseDto
import com.anixkmp.data.dto.SimpleResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * `ProfilePreferenceApi` — MVP-подмножество (только privacy) decompiled
 * `network/api/ProfilePreferenceApi.java`. Пути сверены построчно с decompiled-интерфейсом;
 * остальные методы того файла (email/пароль/логин/соцсети/аватар/темы) вне объёма Фазы 7.
 */
class ProfilePreferenceApi(private val client: HttpClient) {

    /** `GET profile/preference/my` */
    suspend fun my(): ProfilePreferenceResponseDto = apiCall {
        client.get("profile/preference/my").body<ProfilePreferenceResponseDto>().requireOk()
    }

    /** `POST profile/preference/privacy/stats/edit`, body `{ "permission": ... }`. */
    suspend fun privacyStatsEdit(permission: Int): SimpleResponseDto = apiCall {
        client.post("profile/preference/privacy/stats/edit") {
            contentType(ContentType.Application.Json)
            setBody(PrivacyEditRequestDto(permission = permission))
        }.body<SimpleResponseDto>().requireOk()
    }

    /** `POST profile/preference/privacy/counts/edit`, body `{ "permission": ... }`. */
    suspend fun privacyCountsEdit(permission: Int): SimpleResponseDto = apiCall {
        client.post("profile/preference/privacy/counts/edit") {
            contentType(ContentType.Application.Json)
            setBody(PrivacyEditRequestDto(permission = permission))
        }.body<SimpleResponseDto>().requireOk()
    }

    /** `POST profile/preference/privacy/social/edit`, body `{ "permission": ... }`. */
    suspend fun privacySocialEdit(permission: Int): SimpleResponseDto = apiCall {
        client.post("profile/preference/privacy/social/edit") {
            contentType(ContentType.Application.Json)
            setBody(PrivacyEditRequestDto(permission = permission))
        }.body<SimpleResponseDto>().requireOk()
    }

    /** `POST profile/preference/privacy/friendRequests/edit`, body `{ "permission": ... }`. */
    suspend fun privacyFriendRequestsEdit(permission: Int): SimpleResponseDto = apiCall {
        client.post("profile/preference/privacy/friendRequests/edit") {
            contentType(ContentType.Application.Json)
            setBody(PrivacyEditRequestDto(permission = permission))
        }.body<SimpleResponseDto>().requireOk()
    }

    /** `GET profile/preference/privacy/incognito/edit` — без тела, сервер сам инвертирует флаг. */
    suspend fun privacyIncognitoEdit(): SimpleResponseDto = apiCall {
        client.get("profile/preference/privacy/incognito/edit").body<SimpleResponseDto>().requireOk()
    }
}
