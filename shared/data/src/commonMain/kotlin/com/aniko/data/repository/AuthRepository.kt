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
    suspend fun signIn(
        login: String,
        password: String,
    ): AuthSession {
        val response = authApi.signIn(login, password)
        val profile = response.profile ?: throw AnixError.Parsing()
        val token =
            response.profileToken?.token?.takeIf { it.isNotBlank() }
                ?: throw AnixError.Unauthorized()

        sessionStore.save(token, profile.id)
        return AuthSession(profile = profile.toDomain(), token = token)
    }

    /**
     * `auth/signUp` — создаёт неподтверждённую регистрацию и инициирует отправку кода на email.
     * Возвращает [PendingRegistration] с `hash`, который затем уходит в [verifyEmail]/
     * [resendCode].
     */
    suspend fun signUp(
        login: String,
        email: String,
        password: String,
    ): PendingRegistration {
        val response = authApi.signUp(login, email, password)
        return PendingRegistration(
            hash = response.hash,
            codeTimestampExpires = response.codeTimestampExpires,
            suggestedLogins = response.suggestedLogins,
        )
    }

    /**
     * `auth/verify` + сохранение токена в [SessionStore] — подтверждение кода из email само
     * завершает вход (Anixart возвращает профиль и токен прямо в `VerifyResponse`, отдельный
     * `auth/signIn` после verify не нужен; навигация переключится через `sessionState`).
     */
    suspend fun verifyEmail(
        login: String,
        email: String,
        password: String,
        hash: String,
        code: String,
    ): AuthSession {
        val response = authApi.verifyEmail(login, email, password, hash, code)
        val profile = response.profile ?: throw AnixError.Parsing()
        val token =
            response.profileToken?.token?.takeIf { it.isNotBlank() }
                ?: throw AnixError.Unauthorized()

        sessionStore.save(token, profile.id)
        return AuthSession(profile = profile.toDomain(), token = token)
    }

    /**
     * `auth/resend` — повторная отправка кода на email для [PendingRegistration].
     * Возвращает `timestampExpires` нового кода.
     */
    suspend fun resendCode(
        login: String,
        email: String,
        password: String,
        hash: String,
    ): Long = authApi.resendCode(login, email, password, hash).timestampExpires

    suspend fun signOut() = sessionStore.clear()
}

/**
 * Промежуточное состояние регистрации после успешного `auth/signUp`: `hash` связывает
 * последующие `auth/verify`/`auth/resend` с серверной регистрацией. Держится в UI-state
 * [com.aniko.app.feature.auth.RegisterViewModel], в персистентность не попадает.
 */
data class PendingRegistration(
    val hash: String,
    val codeTimestampExpires: Long,
    val suggestedLogins: List<String>,
)
