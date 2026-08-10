package com.aniko.data.repository

import com.aniko.data.api.ReleaseApi
import com.aniko.data.api.SearchApi
import com.aniko.data.cache.Cached
import com.aniko.data.cache.ReleaseCacheStores
import com.aniko.data.cache.cacheFirstFlow
import com.aniko.data.cache.hydratePagedIds
import com.aniko.data.cache.persistPagedReleases
import com.aniko.data.mapper.toDomain
import com.aniko.data.paging.Paginator
import com.aniko.database.cache.CacheKeys
import com.aniko.database.cache.CachePolicy
import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.ReleaseCacheStore
import com.aniko.database.store.ReleaseListStore
import com.aniko.model.AnixError
import com.aniko.model.Paged
import com.aniko.model.Release
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

class ReleaseRepository(
    private val releaseApi: ReleaseApi,
    private val searchApi: SearchApi,
    private val releaseCacheStore: ReleaseCacheStore,
    private val releaseListStore: ReleaseListStore,
    private val listMembershipStore: ListMembershipStore,
    private val clock: Clock,
) {
    private val stores = ReleaseCacheStores(releaseCacheStore, releaseListStore, listMembershipStore)

    suspend fun release(releaseId: Int): Release =
        releaseApi.release(releaseId, extendedMode = true).release?.toDomain()
            ?: throw AnixError.Parsing()

    suspend fun watching(page: Int): Paged<Release> = releaseApi.discoverWatching(page).toDomain { it.toDomain() }

    suspend fun recommendations(
        page: Int,
        previousPage: Int = 0,
    ): Paged<Release> = releaseApi.discoverRecommendations(page, previousPage).toDomain { it.toDomain() }

    /** `search/releases/{page}` — см. `SearchApi` про сверенную вживую форму ответа. */
    suspend fun search(
        query: String,
        page: Int,
    ): Paged<Release> = searchApi.releaseSearch(page, query).toDomain { it.toDomain() }

    /** Готовый пагинатор для экрана «Продолжить смотреть». */
    fun watchingPaginator(): Paginator<Release> = Paginator { page -> watching(page) }

    /** Готовый пагинатор для секции рекомендаций на главном экране. */
    fun recommendationsPaginator(): Paginator<Release> = Paginator { page -> recommendations(page) }

    /** Готовый пагинатор для экрана поиска — новый на каждый поисковый запрос. */
    fun searchPaginator(query: String): Paginator<Release> = Paginator { page -> search(query, page) }

    // ---- Cache-first чтение (P4.T7, S3) -------------------------------------------------

    /**
     * Карточка релиза — БД как SSOT, `release(releaseId)` обновляет её в фоне, см. `cacheFirstFlow`.
     * `listMembershipStore.initFromServer` заводит членство в списках из ответа сервера (см. его
     * KDoc) — без этого шага `myListStatus`/`isFavorite` никогда бы не попадали в локальную БД.
     */
    fun observeRelease(releaseId: ReleaseId): Flow<Cached<Release>> =
        cacheFirstFlow(
            local = releaseCacheStore.observe(releaseId),
            stampAt = { releaseCacheStore.fetchedAt(releaseId) },
            policy = CachePolicy.TitleMetadata,
            refresh = {
                val fetched = release(releaseId)
                val now = clock.now()
                releaseCacheStore.upsert(fetched, now)
                listMembershipStore.initFromServer(releaseId, fetched.myListStatus, fetched.isFavorite, now)
            },
            clock = clock,
        )

    fun observeWatching(page: Int): Flow<Cached<Paged<Release>>> {
        val key = CacheKeys.watching(page)
        return cacheFirstFlow(
            local = hydratePagedIds(releaseListStore.observePage(key), releaseCacheStore),
            stampAt = { releaseListStore.fetchedAt(key) },
            policy = CachePolicy.CatalogListing,
            refresh = { persistPagedReleases(key, watching(page), stores, clock.now()) },
            clock = clock,
        )
    }

    fun observeRecommendations(
        page: Int,
        previousPage: Int = 0,
    ): Flow<Cached<Paged<Release>>> {
        val key = CacheKeys.recommendations(page)
        return cacheFirstFlow(
            local = hydratePagedIds(releaseListStore.observePage(key), releaseCacheStore),
            stampAt = { releaseListStore.fetchedAt(key) },
            policy = CachePolicy.CatalogListing,
            refresh = { persistPagedReleases(key, recommendations(page, previousPage), stores, clock.now()) },
            clock = clock,
        )
    }
}
