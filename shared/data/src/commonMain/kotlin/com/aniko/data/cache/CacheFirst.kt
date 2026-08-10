package com.aniko.data.cache

import com.aniko.database.cache.CachePolicy
import com.aniko.model.AnixError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Cache-first обёртка над одной сущностью кэша (P4.T4).
 *
 * БД — единственный источник истины для UI (SSOT), сеть только пишет в неё через [refresh]/стор.
 * [local] уже реактивен (`Query.asFlow().mapTo...` в сторах трека A), поэтому здесь достаточно
 * читать его текущее значение до и после [refresh] — оптимистичные записи и приход сети сами
 * дадут новое значение в сторе, без ручной досылки эмита отсюда.
 *
 * Порядок эмиссий:
 * 1. Если в [local] уже есть значение — эмитить его немедленно (`origin = CACHE`), не дожидаясь
 *    сети. Свежесть проверяется через [stampAt] + [policy]`.isFresh`.
 * 2. Если это значение свежее — на этом всё, [refresh] не вызывается.
 * 3. Если оно устарело (или кэша не было) — вызвать [refresh] (сам пишет результат в [local] через
 *    стор) и прочитать [local] заново, эмитив его уже с `origin = NETWORK`.
 * 4. Ошибка [refresh] (`AnixError`) не роняет поток, если уже было что показать: следующая эмиссия
 *    будет `Cached(value = <тот же кэш>, isStale = true, refreshError = <ошибка>)`. Если кэша не
 *    было и [refresh] упал — поток падает с этой ошибкой (показывать нечего).
 *
 * @param local поток текущего значения из локального стора (`null`, пока в кэше пусто).
 * @param stampAt время последней успешной записи текущего значения в кэш (`null` — записи не было).
 * @param policy TTL-политика конкретной сущности, см. [CachePolicy.Companion].
 * @param refresh запрашивает свежие данные по сети и сохраняет их через стор (эффект — запись в
 * [local], сама функция ничего не возвращает).
 * @param clock источник текущего времени — параметр, а не `Clock.System` напрямую, чтобы тесты
 * могли подставить фиксированное время.
 */
internal fun <T> cacheFirstFlow(
    local: Flow<T?>,
    stampAt: suspend () -> Instant?,
    policy: CachePolicy,
    refresh: suspend () -> Unit,
    clock: Clock,
): Flow<Cached<T>> =
    flow {
        val cached = local.first()
        if (cached == null) {
            val error = runRefreshCatching(refresh)
            if (error != null) throw error
            val fetched = local.first()
            if (fetched != null) {
                emit(Cached(value = fetched, origin = Cached.Origin.NETWORK, isStale = false))
            }
            return@flow
        }

        val fetchedAt = stampAt()
        val isFresh = fetchedAt != null && policy.isFresh(fetchedAt, clock.now())
        if (isFresh) {
            emit(Cached(value = cached, origin = Cached.Origin.CACHE, isStale = false))
            return@flow
        }

        emit(Cached(value = cached, origin = Cached.Origin.CACHE, isStale = true))
        val error = runRefreshCatching(refresh)
        if (error != null) {
            emit(Cached(value = cached, origin = Cached.Origin.CACHE, isStale = true, refreshError = error))
            return@flow
        }
        val refreshed = local.first() ?: cached
        emit(Cached(value = refreshed, origin = Cached.Origin.NETWORK, isStale = false))
    }

/** [refresh]-исключения — только [AnixError] (контракт data-слоя), всё остальное — падение с багом. */
private suspend fun runRefreshCatching(refresh: suspend () -> Unit): AnixError? =
    try {
        refresh()
        null
    } catch (error: AnixError) {
        error
    }
