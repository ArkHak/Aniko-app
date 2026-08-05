package com.anixkmp.network

/**
 * Колбэк на «сессия больше не валидна» (HTTP 401/403 от бэкенда), см. [createAnixHttpClient].
 *
 * Живёт в `:shared:network`, а не в `:shared:data`: решение вызвать его принимается на уровне
 * HTTP-клиента (перехват ответа), а сетевой модуль ничего не знает про `SessionStore` и не
 * должен его трогать напрямую — точно так же, как [TokenProvider] развязывает клиент и
 * хранилище токена в обратную сторону. Конкретную реакцию (сброс токена, переход на экран
 * логина) подставляет вызывающий код через DI.
 */
fun interface SessionInvalidator {
    /** Вызывается один раз на невалидную сессию; сам запрос при этом НЕ ретраится. */
    suspend fun onUnauthorized()
}

/** Заглушка по умолчанию — ничего не делает (для тестов и клиентов без привязанной сессии). */
object NoOpSessionInvalidator : SessionInvalidator {
    override suspend fun onUnauthorized() = Unit
}
