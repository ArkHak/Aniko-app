package com.aniko.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.aniko.data.R

/**
 * Android-реализация [LocalNotificationPresenter] (P10.T6).
 *
 * **Почему платформенный `Notification.Builder`, а не `NotificationCompat`.** `minSdk` проекта —
 * 26, а и `NotificationChannel`, и `Notification.Builder(context, channelId)` появились ровно в
 * API 26. То есть `NotificationCompat` здесь не покрывает ни одной реальной версии Android и
 * потребовал бы новой зависимости (`androidx.core`) в `shared/data` ради нуля функциональности.
 *
 * **Канал** создаётся лениво при первом показе, а не в `Application.onCreate`: `shared/data` не
 * имеет своей точки входа, а `createNotificationChannel` идемпотентен (повторный вызов с тем же id
 * только обновляет имя/описание, звук и важность уже созданного канала система намеренно
 * игнорирует).
 *
 * **Разрешение.** С API 33 показ требует runtime-разрешения `POST_NOTIFICATIONS`, и запросить его
 * можно только из живой `Activity` — чего у фонового `WorkManager`-тика нет. Поэтому
 * [ensurePermission] здесь только ПРОВЕРЯЕТ (см. KDoc интерфейса), а сам запрос делает экран
 * настроек (`NotificationPermissionState` в `composeApp`). До API 33 разрешение выдаётся при
 * установке, и проверка всегда истинна.
 *
 * **Клик по уведомлению → deep link.** Единственная платформа из трёх, где это доведено до
 * навигации. Ничего нового ради этого не появилось: `PendingIntent` несёт обычный
 * `ACTION_VIEW` с уже существующей схемой `aniko://release/{id}` (P10.T7), которую ловит
 * `<intent-filter>` `MainActivity` и разбирает общий `parseDeepLink`. `setPackage` на своё
 * приложение обязателен — иначе система показала бы диалог выбора приложения для чужой схемы.
 */
class AndroidLocalNotificationPresenter(
    context: Context,
    private val channelName: String,
) : LocalNotificationPresenter {
    private val appContext = context.applicationContext

    private val manager: NotificationManager?
        get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override suspend fun ensurePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    override suspend fun show(notification: LocalNotification) {
        val notificationManager = manager ?: return
        ensureChannel(notificationManager)

        val builder =
            Notification
                .Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_aniko_notification)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setStyle(Notification.BigTextStyle().bigText(notification.body))
                .setAutoCancel(true)

        contentIntentOrNull(notification)?.let(builder::setContentIntent)

        // id уведомления — серверный `AppNotification.id`, урезанный до Int (API принимает только
        // Int). Коллизия после урезания теоретически возможна, но её цена — замена одного
        // уведомления другим в шторке, а не сбой.
        notificationManager.notify(notification.id.toInt(), builder.build())
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        notificationManager.createNotificationChannel(channel)
    }

    private fun contentIntentOrNull(notification: LocalNotification): PendingIntent? {
        val deepLink = notification.deepLink ?: return null
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                setPackage(appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        return PendingIntent.getActivity(
            appContext,
            notification.id.toInt(),
            intent,
            // FLAG_IMMUTABLE обязателен с API 31 (иначе IllegalArgumentException) и безвреден
            // раньше: подменять extras этому PendingIntent никто не должен.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "aniko.notifications"
    }
}
