package com.aniko.network

/**
 * Источник токена для query-параметра `?token=`.
 *
 * Реализация живёт в `:shared:data` (поверх multiplatform-settings),
 * сетевой модуль о хранилище ничего не знает.
 */
fun interface TokenProvider {
    /** Текущий токен либо `null`, если пользователь не авторизован. */
    suspend fun token(): String?

    companion object {
        /** Заглушка для анонимных запросов и тестов. */
        val Anonymous: TokenProvider = TokenProvider { null }
    }
}
