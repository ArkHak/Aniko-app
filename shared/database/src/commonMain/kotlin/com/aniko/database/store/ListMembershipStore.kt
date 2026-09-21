package com.aniko.database.store

import com.aniko.model.ListMembership
import com.aniko.model.ListStatus
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Локальное зеркало членства релиза в списках пользователя (`LibraryRepository`: статус списка +
 * избранное), P4.T1/P4.T5.
 *
 * Отдельно от [ReleaseCacheStore] (а не как поля `Release.myListStatus`/`isFavorite` в кэше
 * релиза) намеренно: эти два поля меняются оптимистично из UI даже офлайн (через
 * `SyncQueueStore`), с ротацией `updatedAt` для last-write-wins (P4.T6) — смешивать этот путь
 * записи с TTL-кэшем самого релиза усложнило бы инвалидацию обоих по разным правилам.
 */
interface ListMembershipStore {
    /** `null` — релиз не числится ни в одном списке. */
    fun observeStatus(releaseId: ReleaseId): Flow<ListStatus?>

    /**
     * Вся известная локально таблица членства, `releaseId → `[ListMembership] — реактивный
     * источник правды вкладок «Мои списки» (`LibraryRepository.observeListMemberships`).
     *
     * Один поток на весь экран вместо [observeStatus]/[observeFavorite] на каждый видимый
     * релиз: вкладка сверяет со членством всю загруженную страницу целиком (см. KDoc
     * запроса `observeAll` в `ListMembership.sq`). Релизы, которых в таблице нет, в карте
     * просто отсутствуют — это НЕ то же самое, что [ListMembership] с `status = null`
     * («сервер/пользователь сказал: ни в одном списке»), и вызывающая сторона обязана
     * различать эти два случая.
     */
    fun observeAll(): Flow<Map<ReleaseId, ListMembership>>

    suspend fun setStatus(
        releaseId: ReleaseId,
        status: ListStatus?,
        updatedAt: Instant,
    )

    fun observeFavorite(releaseId: ReleaseId): Flow<Boolean>

    suspend fun setFavorite(
        releaseId: ReleaseId,
        isFavorite: Boolean,
        updatedAt: Instant,
    )

    /**
     * Первичная синхронизация "сервер → локальная БД" (найдено ревью S3, P4.T4): без этого вызова
     * `Release.myListStatus`/`isFavorite`, пришедшие с сервера, никогда не попадали в эту таблицу
     * — `ReleaseCacheStore.upsert` намеренно её не трогает (см. его KDoc), поэтому строка иначе
     * заводится только явной локальной мутацией [setStatus]/[setFavorite], а до неё UI видел бы
     * пустое членство даже для релиза, уже состоящего в списке на сервере.
     *
     * Не перезаписывает уже существующую строку (см. реализацию/KDoc `.sq`) — вызывать безопасно
     * после каждого сетевого фетча релиза, в т.ч. повторно для уже известных релизов.
     */
    suspend fun initFromServer(
        releaseId: ReleaseId,
        status: ListStatus?,
        isFavorite: Boolean,
        fetchedAt: Instant,
    )
}
