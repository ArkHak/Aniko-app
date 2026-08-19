package com.aniko.app.notification

import androidx.compose.runtime.Composable

/**
 * Состояние runtime-разрешения на показ уведомлений, если платформа его вообще требует (P10.T6).
 *
 * `expect`/`actual`, а не интерфейс с реализацией в Koin, — в отличие от
 * [com.aniko.data.notification.LocalNotificationPresenter]: здесь нужна именно Compose-механика
 * (`rememberLauncherForActivityResult` привязывается к жизненному циклу композиции и не может быть
 * создан заранее в DI), а платформенных зависимостей в конструкторе нет.
 */
interface NotificationPermissionState {
    /** Разрешение уже выдано — секцию с просьбой показывать не надо. */
    val isGranted: Boolean

    /** Показывает системный диалог. Повторный вызов после отказа система может проигнорировать. */
    fun request()
}

/**
 * @return `null` на платформах, где runtime-разрешения на уведомления не существует — то есть
 * везде, кроме Android 13+. Именно `null`, а не «объект с `isGranted = true`»: UI обязан в этом
 * случае не показывать блок про разрешение вообще, а не показывать его в состоянии «уже выдано».
 *
 * iOS сюда не относится: там разрешение запрашивает сам
 * [com.aniko.data.notification.IosLocalNotificationPresenter] через
 * `UNUserNotificationCenter.requestAuthorization`, которому не нужен ни Activity, ни
 * `ActivityResultLauncher`.
 */
@Composable
expect fun rememberNotificationPermissionState(): NotificationPermissionState?
