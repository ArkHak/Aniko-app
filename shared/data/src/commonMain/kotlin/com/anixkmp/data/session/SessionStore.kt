package com.anixkmp.data.session

import com.anixkmp.network.SessionInvalidator
import com.anixkmp.network.TokenProvider
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Единый источник правды о сессии пользователя.
 *
 * Токен — секрет, хранится в [SecureTokenStorage] (Android Keystore / Keychain, см. реализации
 * в androidMain/iosMain/desktopMain). `profileId` секретом не является, поэтому по-прежнему
 * живёт в multiplatform-settings [Settings] как plaintext.
 *
 * Чтение секрета — блокирующий I/O у платформенных реализаций [SecureTokenStorage], поэтому
 * состояние стартует с [SessionState.Loading] и обновляется асинхронно через [bootstrap].
 * Реализует оба контракта `:shared:network`:
 * - [TokenProvider] — [token] дожидается выхода из `Loading`, чтобы запросы, стартовавшие до
 *   завершения bootstrap, не гонялись с ним, а просто подождали результат;
 * - [SessionInvalidator] — [onUnauthorized] реагирует на 401/403 от бэкенда сбросом сессии.
 */
class SessionStore(
    private val settings: Settings,
    private val secureStorage: SecureTokenStorage,
) : TokenProvider, SessionInvalidator {

    private val stateMutex = Mutex()
    private var bootstrapped = false

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)

    /** Текущее состояние сессии — UI подписывается, чтобы решить, какой граф экранов показать. */
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /**
     * Одноразовый сигнал «сессия протухла не по воле пользователя» (сработал [onUnauthorized]).
     * Только для одноразового UI-эффекта (снекбар) — навигацию на логин обеспечивает
     * [sessionState] сам по себе, значение из этого потока для неё не нужно.
     */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /**
     * Читает токен из [secureStorage] и переводит [sessionState] в [SessionState.Authorized]
     * либо [SessionState.Unauthorized]. Идемпотентен и безопасен при параллельном вызове —
     * повторные вызовы (в том числе конкурентные) после первого завершения — no-op.
     */
    suspend fun bootstrap() {
        stateMutex.withLock {
            if (bootstrapped) return@withLock
            val token = secureStorage.get()
            _sessionState.value = token?.let { SessionState.Authorized(it) } ?: SessionState.Unauthorized
            bootstrapped = true
        }
    }

    /** Токен для `TokenProvider` — ждёт завершения [bootstrap], если он ещё не закончился. */
    override suspend fun token(): String? {
        val state = sessionState.first { it !is SessionState.Loading }
        return (state as? SessionState.Authorized)?.token
    }

    /** Сохраняет токен успешного `auth/signIn` и переводит сессию в [SessionState.Authorized]. */
    suspend fun save(token: String, profileId: Long) {
        stateMutex.withLock {
            secureStorage.set(token)
            settings.putLong(KEY_PROFILE_ID, profileId)
            _sessionState.value = SessionState.Authorized(token)
            bootstrapped = true
        }
    }

    fun profileId(): Long? =
        if (settings.hasKey(KEY_PROFILE_ID)) settings.getLong(KEY_PROFILE_ID, 0L) else null

    /** Обычный logout по воле пользователя — БЕЗ сигнала [sessionExpired]. */
    suspend fun clear() {
        stateMutex.withLock { clearLocked() }
    }

    /** 401/403 от бэкенда — сбрасываем сессию и шлём одноразовый сигнал [sessionExpired]. */
    override suspend fun onUnauthorized() {
        stateMutex.withLock { clearLocked() }
        _sessionExpired.tryEmit(Unit)
    }

    private suspend fun clearLocked() {
        secureStorage.clear()
        settings.remove(KEY_PROFILE_ID)
        _sessionState.value = SessionState.Unauthorized
        bootstrapped = true
    }

    private companion object {
        const val KEY_PROFILE_ID = "auth.profile_id"
    }
}
