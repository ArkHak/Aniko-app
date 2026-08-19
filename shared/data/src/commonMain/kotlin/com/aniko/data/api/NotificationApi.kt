package com.aniko.data.api

import com.aniko.data.dto.NotificationCountResponseDto
import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ProfileNotificationDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * `NotificationApi` — лента уведомлений (P10.T6).
 *
 * Реализовано ровно два метода из восемнадцати в декомпиле
 * (`network/api/NotificationApi.java`) — те, на которых стоит поллинг
 * ([com.aniko.data.notification.NotificationPoller]): счётчик и первая страница общей ленты.
 *
 * Чего здесь намеренно нет:
 * - типовые ленты (`notification/episodes/{page}`, `notification/friends/{page}`, …) — общая
 *   `all` уже содержит всё то же самое, а отдельные ленты понадобятся только экрану уведомлений
 *   с вкладками, которого в плане нет;
 * - `notification/read` и `notification/delete/{id}` — они меняют состояние на сервере
 *   (`is_new` → false, удаление), а фоновый поллер обязан быть read-only: пометить всё
 *   прочитанным за спиной пользователя означало бы стереть непрочитанные уведомления в
 *   официальном клиенте. Дифф «уже показывали» ведётся локально по id, см.
 *   [com.aniko.data.notification.NotificationSyncStore].
 *
 * Токен в query дописывает `AnixTokenPlugin`, как и во всех остальных `*Api`.
 */
class NotificationApi(
    private val client: HttpClient,
) {
    /** `GET notification/count` — сколько непрочитанных уведомлений на сервере. */
    suspend fun count(): NotificationCountResponseDto =
        apiCall {
            client.get("notification/count").body<NotificationCountResponseDto>().requireOk()
        }

    /** `GET notification/all/{page}` — общая лента, все типы вперемешку, новые первыми. */
    suspend fun all(page: Int): PageableResponseDto<ProfileNotificationDto> =
        apiCall {
            client.get("notification/all/$page").body<PageableResponseDto<ProfileNotificationDto>>().requireOk()
        }
}
