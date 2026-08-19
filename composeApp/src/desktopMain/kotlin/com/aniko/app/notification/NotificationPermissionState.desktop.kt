package com.aniko.app.notification

import androidx.compose.runtime.Composable

/**
 * У JVM-desktop разрешений на уведомления нет вообще: доступность определяется наличием системного
 * трея и проверяется внутри
 * [com.aniko.data.notification.DesktopLocalNotificationPresenter.ensurePermission] (P10.T5).
 */
@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState? = null
