package com.aniko.data.repository

import com.aniko.data.api.FeedApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.paging.Paginator
import com.aniko.model.Article
import com.aniko.model.Paged

/** Экран ленты в приложении (P16.T3, MVP) — тот же каркас, что [NotificationRepository]: один
 *  пагинатор поверх `GET feed/latest/all/{page}`, без сортировки/фильтров/вкладок по каналам. */
class FeedRepository(
    private val api: FeedApi,
) {
    fun feedPaginator(): Paginator<Article> = Paginator(fetch = ::fetchFeedPage)

    private suspend fun fetchFeedPage(page: Int): Paged<Article> {
        val response = api.latestArticles(page)
        return response.toDomain { it.toDomain() }
    }
}
