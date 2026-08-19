package com.aniko.app.notification

import androidx.compose.runtime.Composable

/**
 * iOS не требует отдельного UI под разрешение: `UNUserNotificationCenter.requestAuthorization`
 * вызывается из самого презентера ([com.aniko.data.notification.IosLocalNotificationPresenter])
 * и показывает системный алерт откуда угодно, без `Activity`-подобного посредника.
 */
@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState? = null
