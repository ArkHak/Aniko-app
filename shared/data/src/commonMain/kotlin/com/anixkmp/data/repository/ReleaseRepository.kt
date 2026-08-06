package com.anixkmp.data.repository

import com.anixkmp.data.api.ReleaseApi
import com.anixkmp.data.api.SearchApi
import com.anixkmp.data.mapper.toDomain
import com.anixkmp.data.paging.Paginator
import com.anixkmp.model.AnixError
import com.anixkmp.model.Paged
import com.anixkmp.model.Release

class ReleaseRepository(
    private val releaseApi: ReleaseApi,
    private val searchApi: SearchApi,
) {
    suspend fun release(releaseId: Int): Release =
        releaseApi.release(releaseId, extendedMode = true).release?.toDomain()
            ?: throw AnixError.Parsing()

    suspend fun watching(page: Int): Paged<Release> =
        releaseApi.discoverWatching(page).toDomain { it.toDomain() }

    suspend fun recommendations(page: Int, previousPage: Int = 0): Paged<Release> =
        releaseApi.discoverRecommendations(page, previousPage).toDomain { it.toDomain() }

    /** `search/releases/{page}` — см. `SearchApi` про сверенную вживую форму ответа. */
    suspend fun search(query: String, page: Int): Paged<Release> =
        searchApi.releaseSearch(page, query).toDomain { it.toDomain() }

    /** Готовый пагинатор для экрана «Продолжить смотреть». */
    fun watchingPaginator(): Paginator<Release> = Paginator { page -> watching(page) }

    /** Готовый пагинатор для секции рекомендаций на главном экране. */
    fun recommendationsPaginator(): Paginator<Release> = Paginator { page -> recommendations(page) }

    /** Готовый пагинатор для экрана поиска — новый на каждый поисковый запрос. */
    fun searchPaginator(query: String): Paginator<Release> = Paginator { page -> search(query, page) }
}
