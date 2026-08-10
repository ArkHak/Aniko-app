package com.aniko.data.cache

import com.aniko.model.AnixError

/**
 * Обёртка над значением, отданным `cacheFirstFlow` (P4.T4).
 *
 * @param value сами данные — из локального кэша либо со свежего сетевого ответа.
 * @param origin откуда взят именно этот эмит: [Origin.CACHE] (первый эмит, пока сеть не ответила
 * или недоступна) или [Origin.NETWORK] (сеть успешно обновила и переписала кэш).
 * @param isStale `true`, если [value] — кэш старше TTL ([com.aniko.database.cache.CachePolicy]).
 * UI использует это, чтобы показать индикатор «данные могут быть устаревшими», не блокируя показ.
 * @param refreshError фоновое сетевое обновление кэша упало — само по себе не критично (данные из
 * кэша уже показаны), но UI может показать ненавязчивое предупреждение. `null`, если обновление
 * либо ещё не завершилось, либо завершилось успешно.
 */
data class Cached<out T>(
    val value: T,
    val origin: Origin,
    val isStale: Boolean,
    val refreshError: AnixError? = null,
) {
    enum class Origin {
        CACHE,
        NETWORK,
    }
}
