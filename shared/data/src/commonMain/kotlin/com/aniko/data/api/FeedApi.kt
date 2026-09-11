package com.aniko.data.api

import com.aniko.data.dto.ArticleDto
import com.aniko.data.dto.PageableResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * `FeedApi` — публичная лента постов каналов/блогов (P16.T3, MVP). См. KDoc `ArticleDto` для
 * полного обоснования формы ответа/ограничений MVP.
 *
 * Реализован один метод из трёх в декомпиле (`network/api/FeedApi.java`): `feed/latest/all/
 * {page}` — постраничный общий фид, новые посты первыми (аналог `feed/latest` без обёртки в
 * "последний один пост", `feed/all/{page}` — тот же список, но с опциональным фильтром по
 * конкретному каналу, не нужен MVP-ленте без вкладок по каналам).
 *
 * Токен в query дописывает `AnixTokenPlugin`, как и во всех остальных `*Api`.
 */
class FeedApi(
    private val client: HttpClient,
) {
    /** `GET feed/latest/all/{page}` — общий фид, новые посты первыми. */
    suspend fun latestArticles(page: Int): PageableResponseDto<ArticleDto> =
        apiCall {
            client.get("feed/latest/all/$page").body<PageableResponseDto<ArticleDto>>().requireOk()
        }
}
