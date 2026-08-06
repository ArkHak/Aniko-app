package com.aniko.data.api

import com.aniko.data.dto.SignInResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

/**
 * `AuthApi` — см. `docs/api/ENDPOINTS.md`, раздел «Auth».
 * Для MVP реализован только путь `auth/signIn`.
 */
class AuthApi(private val client: HttpClient) {

    /** `POST auth/signIn` (form: login, password) → токен для всех дальнейших `?token=`. */
    suspend fun signIn(login: String, password: String): SignInResponseDto = apiCall {
        client.submitForm(
            url = "auth/signIn",
            formParameters = Parameters.build {
                append("login", login)
                append("password", password)
            },
        ).body<SignInResponseDto>().requireOk()
    }
}
