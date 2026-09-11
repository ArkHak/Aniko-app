package com.aniko.data.repository

import com.aniko.data.api.CollectionApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.paging.Paginator
import com.aniko.model.AnixCollection
import com.aniko.model.Paged

/** Экран публичных коллекций в приложении (P16.T16, MVP) — тот же каркас, что
 *  [FeedRepository]/[NotificationRepository]: один пагинатор поверх `GET collection/all/{page}`. */
class CollectionRepository(
    private val api: CollectionApi,
) {
    fun collectionsPaginator(): Paginator<AnixCollection> = Paginator(fetch = ::fetchCollectionsPage)

    private suspend fun fetchCollectionsPage(page: Int): Paged<AnixCollection> {
        val response = api.collections(page)
        return response.toDomain { it.toDomain() }
    }
}
