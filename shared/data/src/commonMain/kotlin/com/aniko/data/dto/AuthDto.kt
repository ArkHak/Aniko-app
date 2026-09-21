package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `ProfileToken{id, token}` — то, что затем уходит в `?token=`. */
@Serializable
data class ProfileTokenDto(
    val id: Long = 0,
    val token: String = "",
)

/** DTO профиля. `[TODO: verify live]` — полный набор полей. */
@Serializable
data class ProfileDto(
    val id: Long = 0,
    val login: String = "",
    val avatar: String? = null,
    @SerialName("is_sponsor") val isSponsor: Boolean = false,
)

/** `SignInResponse` — `POST auth/signIn` (form: login, password). */
@Serializable
data class SignInResponseDto(
    override val code: Int = 0,
    val profile: ProfileDto? = null,
    @SerialName("profileToken") val profileToken: ProfileTokenDto? = null,
) : ApiCodeAware

/**
 * `SignUpResponse` — `POST auth/signUp` (form: login, email, password).
 * Коды: 2 INVALID_LOGIN, 3 INVALID_EMAIL, 4 INVALID_PASSWORD, 5 LOGIN_ALREADY_TAKEN,
 * 6 EMAIL_ALREADY_TAKEN, 7 CODE_ALREADY_SEND, 8 CODE_CANNOT_SEND, 9 EMAIL_SERVICE_DISALLOWED,
 * 10 TOO_MANY_REGISTRATIONS (см. `docs/api/jadx-out-21/.../response/auth/SignUpResponse.java`).
 */
@Serializable
data class SignUpResponseDto(
    override val code: Int = 0,
    val hash: String = "",
    val codeTimestampExpires: Long = 0,
    @SerialName("suggested_logins") val suggestedLogins: List<String> = emptyList(),
) : ApiCodeAware

/**
 * `VerifyResponse` — `POST auth/verify` (form: login, email, password, hash, code).
 * При успехе (code=0) содержит профиль и токен — то есть verify сам по себе завершает вход,
 * отдельный `auth/signIn` после него не нужен.
 * Коды: 2 INVALID_LOGIN, 3 INVALID_EMAIL, 4 INVALID_PASSWORD, 5 LOGIN_ALREADY_TAKEN,
 * 6 EMAIL_ALREADY_TAKEN, 7 CODE_INVALID, 8 CODE_EXPIRED, 9 INVALID_HASH,
 * 10 EMAIL_SERVICE_DISALLOWED, 11 TOO_MANY_REGISTRATIONS (см. jadx `VerifyResponse.java`).
 */
@Serializable
data class VerifyResponseDto(
    override val code: Int = 0,
    val profile: ProfileDto? = null,
    @SerialName("profileToken") val profileToken: ProfileTokenDto? = null,
    @SerialName("suggested_logins") val suggestedLogins: List<String> = emptyList(),
) : ApiCodeAware

/**
 * `ResendResponse` — `POST auth/resend` (form: login, email, password, hash).
 * Коды: 2 INVALID_LOGIN, 3 INVALID_EMAIL, 4 INVALID_PASSWORD, 5 INVALID_HASH,
 * 6 CODE_CANNOT_SEND (см. jadx `ResendResponse.java`).
 */
@Serializable
data class ResendResponseDto(
    override val code: Int = 0,
    val timestampExpires: Long = 0,
) : ApiCodeAware
