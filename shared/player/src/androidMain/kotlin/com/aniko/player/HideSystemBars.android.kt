package com.aniko.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Android-реализация (ревью замечание #3) — см. KDoc `expect fun` в `HideSystemBars.kt`.
 *
 * Проблема двух одновременных инстансов плеера, из-за которой [LockLandscapeOrientationEffect]
 * (`ScreenOrientation.android.kt`, тот же пакет) вынужден считать реф-каунт (см. её KDoc), уже
 * устранена на уровне навигации (мгновенные переходы + `popUpTo<Player>`,
 * `TitleNavigator.openPlayer`) — этому эффекту реф-каунт не нужен, обычный `DisposableEffect`
 * безопасен.
 */
@Composable
actual fun HideSystemBarsEffect() {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
