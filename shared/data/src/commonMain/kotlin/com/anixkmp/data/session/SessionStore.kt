package com.anixkmp.data.session

import com.anixkmp.network.SessionInvalidator
import com.anixkmp.network.TokenProvider
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
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
            val token = readTokenOrNull()
            _sessionState.value = token?.let { SessionState.Authorized(it) } ?: SessionState.Unauthorized
            bootstrapped = true
        }
    }

    /**
     * [secureStorage] на практике может кинуть исключение не только на "нет записи"
     * (это уже `null` внутри самой реализации), а на полный отказ хранилища — например,
     * iOS Keychain с `OSStatus=-34018` (`errSecMissingEntitlement`) на несигнированной сборке.
     * `bootstrap()` вызывается из голого `LaunchedEffect` на старте приложения без внешнего
     * try/catch, а необработанное исключение в корутине на Kotlin/Native валит весь процесс
     * (нет JVM-подобного дефолтного обработчика) — поэтому деградация тут обязательна.
     */
    private suspend fun readTokenOrNull(): String? = try {
        secureStorage.get()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
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
        // Тот же риск, что и в readTokenOrNull(): SettingsViewModel.signOut() тоже вызывает
        // это без внешнего try/catch. Физически стереть secure storage не удалось — не беда,
        // локальную сессию сбрасываем в любом случае, пользователь не должен застрять
        // залогиненным из-за отказа хранилища.
        try {
            secureStorage.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // no-op — состояние всё равно сбрасывается ниже
        }
        settings.remove(KEY_PROFILE_ID)
        _sessionState.value = SessionState.Unauthorized
        bootstrapped = true
    }

    private companion object {
        const val KEY_PROFILE_ID = "auth.profile_id"
    }
}
