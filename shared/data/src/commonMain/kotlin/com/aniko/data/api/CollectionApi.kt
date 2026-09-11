package com.aniko.data.api

import com.aniko.data.dto.CollectionDto
import com.aniko.data.dto.PageableResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * `CollectionApi` — публичные коллекции (P16.T16, MVP). См. KDoc `CollectionDto` для метода/
 * ограничений.
 *
 * Реализован один метод из пяти в декомпиле (`network/api/CollectionApi.java`): общий публичный
 * список. Остальные (`collection/{id}` детали, `collection/{id}/releases/{page}` вложенный
 * список релизов, `collection/all/profile/{p_id}/{page}` коллекции конкретного профиля,
 * `collection/all/release/{r_id}/{page}` коллекции с конкретным релизом) — не входят в MVP
 * (просмотр деталей/вложенного списка релизов, не только общая лента карточек).
 *
 * Токен в query дописывает `AnixTokenPlugin`, как и во всех остальных `*Api` — но этот конкретный
 * маршрут публичный, токен не обязателен (см. KDoc `CollectionDto`).
 */
class CollectionApi(
    private val client: HttpClient,
) {
    /** `GET collection/all/{page}` — публичный общий список коллекций. */
    suspend fun collections(page: Int): PageableResponseDto<CollectionDto> =
        apiCall {
            client.get("collection/all/$page").body<PageableResponseDto<CollectionDto>>().requireOk()
        }
}
