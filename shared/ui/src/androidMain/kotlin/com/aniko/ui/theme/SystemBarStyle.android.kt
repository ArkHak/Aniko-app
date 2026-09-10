package com.aniko.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Android-реализация (ревью замечание #4) — см. KDoc `expect fun` в `SystemBarStyle.kt`.
 *
 * `WindowCompat.getInsetsController(window, view)` нужен именно `Window` текущей Activity, а не
 * произвольный `Context` — [LocalView.current.context] в Compose иногда обёрнут
 * (themed wrapper), поэтому разворачиваем его тем же приёмом, что и `findActivity()` в
 * `PlayerSystemLevels.android.kt`/`ScreenOrientation.android.kt` (не выносится в общий модуль —
 * тот же паттерн намеренно дублируется в каждом androidMain-файле, где нужен, см. их KDoc).
 *
 * `SideEffect`, а не `LaunchedEffect`: чистый побочный эффект без suspend-работы, должен
 * применяться на каждой успешной рекомпозиции с новым [darkTheme] синхронно с остальным UI —
 * тот же повод, что у стандартного паттерна `rememberSystemUiController`-подобных решений.
 */
@Composable
actual fun SystemBarStyleEffect(darkTheme: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    SideEffect {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
