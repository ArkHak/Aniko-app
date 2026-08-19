package com.aniko.data.notification

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS-реализация [LocalNotificationPresenter] на `UNUserNotificationCenter` (P10.T6).
 *
 * **Разрешение.** `requestAuthorizationWithOptions` можно звать откуда угодно и сколько угодно:
 * системный алерт показывается ровно один раз за установку, все последующие вызовы мгновенно
 * возвращают уже принятое пользователем решение. Поэтому отдельного «проверить, не запрашивая»
 * здесь не нужно, и [ensurePermission] честно делает и то и другое (см. KDoc интерфейса — на
 * Android так нельзя, там метод только проверяет).
 *
 * **Почему триггер, а не «показать прямо сейчас».** У `UNUserNotificationCenter` нет API
 * «показать немедленно»: локальное уведомление всегда планируется. `trigger = null` означает не
 * «сразу», а «доставить немедленно, но только если приложение НЕ на переднем плане» — и в фоне
 * (а мы почти всегда в фоне, нас разбудил `BGAppRefreshTask`) это как раз то, что нужно. Тем не
 * менее используется [MIN_TRIGGER_INTERVAL] секунды: `UNTimeIntervalNotificationTrigger` бросает
 * `NSInvalidArgumentException` при `timeInterval <= 0`, а секунда — минимальное допустимое
 * значение и практически неотличима от мгновенного показа.
 *
 * **Клик по уведомлению.** Deep link кладётся в `userInfo`, но до навигации НЕ доводится:
 * для этого нужен `UNUserNotificationCenterDelegate` на стороне Swift
 * (`didReceiveNotificationResponse` → `DeepLinkDispatcher.dispatch`), то есть новый объект в
 * `iosApp` с ручным пробросом в Kotlin. Это несоразмерно объёму P10.T6, поэтому тап по
 * уведомлению просто открывает приложение — ровно то же поведение, что и на Desktop. Данные для
 * будущей доработки уже лежат в `userInfo`, менять эту часть не придётся.
 */
class IosLocalNotificationPresenter : LocalNotificationPresenter {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun ensurePermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            center.requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { granted, _ ->
                // Ошибку намеренно игнорируем: для вызывающего «не дали разрешение» и «запрос
                // не удался» — один и тот же исход «показывать нельзя», и различать их незачем.
                if (continuation.isActive) continuation.resume(granted)
            }
        }

    override suspend fun show(notification: LocalNotification) {
        val content =
            UNMutableNotificationContent().apply {
                setTitle(notification.title)
                setBody(notification.body)
                notification.deepLink?.let { setUserInfo(mapOf(USER_INFO_DEEP_LINK to it)) }
            }

        val request =
            UNNotificationRequest.requestWithIdentifier(
                // Стабильный идентификатор из серверного id: повторная доставка того же
                // уведомления заменит уже показанное, а не создаст дубль.
                identifier = notification.id.toString(),
                content = content,
                trigger =
                    UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                        timeInterval = MIN_TRIGGER_INTERVAL,
                        repeats = false,
                    ),
            )

        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    private companion object {
        const val MIN_TRIGGER_INTERVAL = 1.0
        const val USER_INFO_DEEP_LINK = "deepLink"
    }
}
