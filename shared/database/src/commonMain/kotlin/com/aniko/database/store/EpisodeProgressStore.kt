package com.aniko.database.store

import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Локальный прогресс просмотра серий (`EpisodeRepository.markWatched`/`markUnwatched`), P4.T1/P4.T5.
 *
 * Ключ серии — тройка (releaseId, sourceId, position), как в самом API
 * (`episode/target/{releaseId}/{sourceId}/{position}`).
 */
interface EpisodeProgressStore {
    /** Отмечена ли конкретная серия просмотренной. */
    fun observeWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
    ): Flow<Boolean>

    /**
     * Весь известный локальный статус позиций релиза в рамках источника — позиция → is_watched,
     * БЕЗ фильтра по значению. Раньше отдавал только позиции с `is_watched = true` (`Set<Int>`),
     * из-за чего явный локальный "не просмотрено" был неотличим от "вообще не трогали" — карта
     * сохраняет обе стороны, чтобы явный unwatch пользователя не откатывался обратно в watched
     * после ухода с экрана/перезапуска (см. KDoc `mergeWatchedOverrides` в
     * `ReleaseDetailsContract.kt`).
     */
    fun observeWatchedPositions(
        releaseId: ReleaseId,
        sourceId: Int,
    ): Flow<Map<Int, Boolean>>

    suspend fun setWatched(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
        isWatched: Boolean,
        updatedAt: Instant,
    )
}
