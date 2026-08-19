package com.aniko.app.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android-реализация [rememberNotificationPermissionState] (P10.T6).
 *
 * До API 33 `POST_NOTIFICATIONS` не существует и выдаётся при установке — возвращаем `null`,
 * чтобы экран настроек не показывал бессмысленную просьбу.
 *
 * Начальное значение читается через `checkSelfPermission` при создании состояния, а дальше
 * обновляется результатом лаунчера. Отдельного отслеживания «пользователь ушёл в системные
 * настройки и выдал разрешение там» здесь нет: экран настроек и так пересоздаёт состояние при
 * возврате в композицию.
 */
@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null

    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            granted = result
        }

    return remember(granted) {
        object : NotificationPermissionState {
            override val isGranted: Boolean = granted

            override fun request() {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
