package com.aniko.data.api

import com.aniko.data.dto.ResendResponseDto
import com.aniko.data.dto.SignInResponseDto
import com.aniko.data.dto.SignUpResponseDto
import com.aniko.data.dto.VerifyResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

/**
 * `AuthApi` — см. `docs/api/ENDPOINTS.md`, раздел «Auth».
 * Реализованы пути входа (`auth/signIn`) и регистрации (`auth/signUp` + `auth/verify` +
 * `auth/resend`).
 */
class AuthApi(
    private val client: HttpClient,
) {
    /** `POST auth/signIn` (form: login, password) → токен для всех дальнейших `?token=`. */
    suspend fun signIn(
        login: String,
        password: String,
    ): SignInResponseDto =
        apiCall {
            client
                .submitForm(
                    url = "auth/signIn",
                    formParameters =
                        Parameters.build {
                            append("login", login)
                            append("password", password)
                        },
                ).body<SignInResponseDto>()
                .requireOk()
        }

    /**
     * `POST auth/signUp` (form: login, email, password) → `hash` для `auth/verify`/`auth/resend`.
     * На указанный email уходит код подтверждения.
     */
    suspend fun signUp(
        login: String,
        email: String,
        password: String,
    ): SignUpResponseDto =
        apiCall {
            client
                .submitForm(
                    url = "auth/signUp",
                    formParameters =
                        Parameters.build {
                            append("login", login)
                            append("email", email)
                            append("password", password)
                        },
                ).body<SignUpResponseDto>()
                .requireOk()
        }

    /**
     * `POST auth/verify` (form: login, email, password, hash, code) → профиль + токен,
     * то есть при code=0 это сразу завершённый вход (сессия сохраняется на уровне репозитория).
     */
    suspend fun verifyEmail(
        login: String,
        email: String,
        password: String,
        hash: String,
        code: String,
    ): VerifyResponseDto =
        apiCall {
            client
                .submitForm(
                    url = "auth/verify",
                    formParameters =
                        Parameters.build {
                            append("login", login)
                            append("email", email)
                            append("password", password)
                            append("hash", hash)
                            append("code", code)
                        },
                ).body<VerifyResponseDto>()
                .requireOk()
        }

    /**
     * `POST auth/resend` (form: login, email, password, hash) — повторная отправка кода
     * на email, возвращает `timestampExpires` нового кода.
     */
    suspend fun resendCode(
        login: String,
        email: String,
        password: String,
        hash: String,
    ): ResendResponseDto =
        apiCall {
            client
                .submitForm(
                    url = "auth/resend",
                    formParameters =
                        Parameters.build {
                            append("login", login)
                            append("email", email)
                            append("password", password)
                            append("hash", hash)
                        },
                ).body<ResendResponseDto>()
                .requireOk()
        }
}
