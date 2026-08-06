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
