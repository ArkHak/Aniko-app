package com.aniko.database.store

import com.aniko.model.Paged
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Кэш страниц листингов (`watching`/`recommendations`/`myList`/`favorites`/`history`/`schedule`),
 * P4.T1/P4.T4. Ключ страницы — см. `com.aniko.database.cache.CacheKeys`.
 *
 * Хранит только упорядоченный список [ReleaseId] и метаданные страницы — сами релизы читаются
 * через [ReleaseCacheStore] по этим id, чтобы не дублировать данные релиза в каждой странице
 * каждого листинга, в котором он встречается.
 */
interface ReleaseListStore {
    /** Текущее содержимое страницы [key], `null` — страница ещё не закэширована. */
    fun observePage(key: String): Flow<Paged<ReleaseId>?>

    /** Момент последней успешной записи страницы [key], `null` — записи ещё не было. */
    suspend fun fetchedAt(key: String): Instant?

    /** Перезаписывает страницу [key] и штамп времени. */
    suspend fun replacePage(
        key: String,
        page: Paged<ReleaseId>,
        fetchedAt: Instant,
    )

    /**
     * Удаляет все закэшированные страницы, чей ключ начинается с [prefix]
     * (см. `CacheKeys.listPrefix`) — используется для инвалидации листинга целиком, например
     * после смены статуса релиза в списке.
     */
    suspend fun invalidate(prefix: String)
}
