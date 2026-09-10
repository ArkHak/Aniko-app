package com.aniko.data.repository

import com.aniko.data.api.NotificationApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.notification.NotificationSyncStore
import com.aniko.data.notification.unreadBadgeCount
import com.aniko.data.paging.Paginator
import com.aniko.model.AppNotification
import com.aniko.model.Paged

/**
 * Экран уведомлений в приложении (P16.T18) — в отличие от [NotificationApi], которым пользуется
 * только фоновый [com.aniko.data.notification.NotificationPoller] (read-only, см. его KDoc),
 * этот репозиторий обслуживает UI: полный постраничный список + бейдж непрочитанных.
 *
 * Не переиспользует [com.aniko.data.notification.NotificationPoller]/[NotificationSyncStore]
 * напрямую в UI-код — тот же приём, что у остальных `*Repository`: `composeApp` не должен видеть
 * сырые DTO/`Settings`, только доменные типы и чистые операции.
 */
class NotificationRepository(
    private val api: NotificationApi,
    private val syncStore: NotificationSyncStore,
) {
    /** Готовый пагинатор общей ленты уведомлений (`GET notification/all/{page}`). */
    fun notificationsPaginator(): Paginator<AppNotification> = Paginator(fetch = ::fetchNotificationsPage)

    private suspend fun fetchNotificationsPage(page: Int): Paged<AppNotification> {
        val response = api.all(page)
        return response.toDomain { it.toDomain() }
    }

    /**
     * Сколько уведомлений показать бейджем сейчас — см. [unreadBadgeCount]. Безопасно дергать
     * часто (один дешёвый `GET notification/count`, как и у поллера).
     */
    suspend fun unreadBadgeCount(): Long {
        val current = api.count().count
        return unreadBadgeCount(currentCount = current, lastSeenCount = syncStore.lastSeenCount)
    }

    /**
     * Отмечает текущий счётчик как увиденный — вызывать при открытии экрана уведомлений
     * ([com.aniko.app.feature.notifications.NotificationsViewModel]). Следующий бейдж будет
     * пустым, пока `notification/count` не вырастет сверх этого значения.
     */
    suspend fun acknowledgeSeen() {
        syncStore.lastSeenCount = api.count().count
    }
}
