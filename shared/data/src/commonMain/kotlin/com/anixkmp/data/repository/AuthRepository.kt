package com.anixkmp.data.repository

import com.anixkmp.data.api.AuthApi
import com.anixkmp.data.mapper.toDomain
import com.anixkmp.data.session.SessionStore
import com.anixkmp.model.AnixError
import com.anixkmp.model.AuthSession
import kotlinx.coroutines.flow.StateFlow

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
) {
    val tokenFlow: StateFlow<String?> = sessionStore.tokenFlow

    val isAuthorized: Boolean get() = sessionStore.isAuthorized

    /** `auth/signIn` + сохранение токена в [SessionStore]. */
    suspend fun signIn(login: String, password: String): AuthSession {
        val response = authApi.signIn(login, password)
        val profile = response.profile ?: throw AnixError.Parsing()
        val token = response.profileToken?.token?.takeIf { it.isNotBlank() }
            ?: throw AnixError.Unauthorized()

        sessionStore.save(token, profile.id)
        return AuthSession(profile = profile.toDomain(), token = token)
    }

    fun signOut() = sessionStore.clear()
}
