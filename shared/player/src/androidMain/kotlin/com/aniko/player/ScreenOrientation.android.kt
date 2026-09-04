package com.aniko.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * `LocalContext.current` на Compose Multiplatform внутри Android-хоста — почти всегда обёртка
 * над `Activity` (`ContextThemeWrapper` и т.п.), а не сама `Activity` — `as? Activity` напрямую
 * почти всегда возвращает `null`. Разворачиваем цепочку `ContextWrapper.baseContext`, пока не
 * найдём настоящую `Activity` или не упрёмся в `Application`/`null` (тогда честно `null` — нечем
 * управлять, вызывающая сторона просто не блокирует ориентацию).
 */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, не жёсткий `LANDSCAPE` — даёт устройству выбирать между
 * `LandscapeLeft`/`LandscapeRight` по датчику (пользователь может держать телефон камерой влево
 * ИЛИ вправо, оба варианта нормальны для видео), но не даёт провалиться обратно в portrait.
 * `configChanges="orientation|..."` на `MainActivity` (`AndroidManifest.xml`) уже гарантирует, что
 * Activity не пересоздастся, когда мы сами меняем `requestedOrientation` — иначе весь экран плеера
 * потерял бы состояние ровно в момент вызова этой функции.
 */
@Composable
actual fun LockLandscapeOrientationEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
