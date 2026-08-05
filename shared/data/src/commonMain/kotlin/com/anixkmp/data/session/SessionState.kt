package com.anixkmp.data.session

/**
 * Состояние сессии пользователя, как его видит [SessionStore].
 *
 * `Loading` — переходное состояние на время [SessionStore.bootstrap]: токен ещё не прочитан
 * из [SecureTokenStorage] (I/O), поэтому неизвестно, авторизован пользователь или нет.
 * UI обязан дождаться выхода из `Loading`, прежде чем решать, какой экран показывать
 * (сплэш/лоадер, затем либо основной граф, либо экран логина).
 */
sealed interface SessionState {

    /** Bootstrap ещё не завершён — токен читается из [SecureTokenStorage]. */
    data object Loading : SessionState

    /** Пользователь авторизован, [token] — текущий валидный (насколько известно клиенту) токен. */
    data class Authorized(val token: String) : SessionState

    /** Пользователь не авторизован (токена нет, либо сессия была инвалидирована/разлогинена). */
    data object Unauthorized : SessionState
}
