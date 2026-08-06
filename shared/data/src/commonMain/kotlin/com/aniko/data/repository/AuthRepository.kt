package com.aniko.data.repository

import com.aniko.data.api.AuthApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.session.SessionState
import com.aniko.data.session.SessionStore
import com.aniko.model.AnixError
import com.aniko.model.AuthSession
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
) {
    /** Текущее состояние сессии, см. [SessionState] — стартует с `Loading` до [bootstrap]. */
    val sessionState: StateFlow<SessionState> = sessionStore.sessionState

    /** Одноразовый сигнал «сессию сбросило принудительно» (401/403), для снекбара в UI. */
    val sessionExpired: SharedFlow<Unit> = sessionStore.sessionExpired

    /** Читает сохранённый токен и инициализирует [sessionState]. Идемпотентен. */
    suspend fun bootstrap() = sessionStore.bootstrap()

    /** `auth/signIn` + сохранение токена в [SessionStore]. */
    suspend fun signIn(login: String, password: String): AuthSession {
        val response = authApi.signIn(login, password)
        val profile = response.profile ?: throw AnixError.Parsing()
        val token = response.profileToken?.token?.takeIf { it.isNotBlank() }
            ?: throw AnixError.Unauthorized()

        sessionStore.save(token, profile.id)
        return AuthSession(profile = profile.toDomain(), token = token)
    }

    suspend fun signOut() = sessionStore.clear()
}
