package com.aniko.database.store

import com.aniko.model.Release
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Кэш отдельных релизов (`ReleaseRepository.release`), P4.T1/P4.T4.
 *
 * Одна строка на релиз, ключ — [ReleaseId]. Листинги (`watching`/`recommendations`/`myList`/...)
 * кэшируются отдельно в [ReleaseListStore] как упорядоченные списки [ReleaseId] — сами релизы,
 * на которые они ссылаются, живут здесь же, без дублирования данных между двумя таблицами.
 *
 * Реализация — трек A (следующий агент), сигнатуры зафиксированы, чтобы трек B (`cacheFirstFlow`)
 * и трек C (воркер синхронизации) могли компилироваться против интерфейса без ожидания.
 */
interface ReleaseCacheStore {
    /** Текущее значение релиза из кэша, `null` — ещё не закэширован. Обновляется через [upsert]. */
    fun observe(releaseId: ReleaseId): Flow<Release?>

    /** Момент последней успешной записи [releaseId] в кэш, `null` — записи ещё не было. */
    suspend fun fetchedAt(releaseId: ReleaseId): Instant?

    /** Перезаписывает релиз и штамп времени (одной транзакцией на стороне реализации). */
    suspend fun upsert(
        release: Release,
        fetchedAt: Instant,
    )

    suspend fun delete(releaseId: ReleaseId)
}
