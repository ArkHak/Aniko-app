package com.aniko.model

/** Профиль пользователя Anixart. */
data class Profile(
    val id: Long,
    val login: String,
    val avatarUrl: String? = null,
    val isSponsor: Boolean = false,
)

/**
 * Пара «профиль + токен», результат `auth/signIn`.
 * [token] далее подставляется во все запросы как query-параметр `?token=`.
 */
data class AuthSession(
    val profile: Profile,
    val token: String,
)
