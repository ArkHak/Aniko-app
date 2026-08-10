package com.aniko.database.cache

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * TTL-политика для одной сущности кэша (P4.T3).
 *
 * `kotlin.time.Instant`/`kotlin.time.Clock` из stdlib (стабилизированы в Kotlin 2.3+) — намеренно
 * НЕ `kotlinx-datetime`: проекту не нужны часовые пояса/календарные операции, только точка на
 * шкале времени и разница между двумя точками, что `kotlin.time` уже даёт без лишней зависимости.
 */
@JvmInline
value class CachePolicy(
    val ttl: Duration,
) {
    /** `true`, если запись, полученная в [fetchedAt], всё ещё свежа относительно [now]. */
    fun isFresh(
        fetchedAt: Instant,
        now: Instant,
    ): Boolean = now - fetchedAt < ttl

    companion object {
        /** Листинги каталога (`discover/watching`, `discover/interesting`, поиск, расписание). */
        val CatalogListing = CachePolicy(24.hours)

        /** Статичные метаданные тайтла (`release/{id}`) — меняются редко. */
        val TitleMetadata = CachePolicy(7.days)

        /** Кэш никогда не считается устаревшим сам по себе (инвалидация только вручную). */
        val Never = CachePolicy(Duration.INFINITE)
    }
}
