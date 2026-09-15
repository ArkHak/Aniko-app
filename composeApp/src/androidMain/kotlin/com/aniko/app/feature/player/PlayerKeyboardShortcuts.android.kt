package com.aniko.app.feature.player

import androidx.compose.runtime.Composable

/** Android — CUT (P8.T7 в объём этой ветки не входил): основной ввод — тач, не клавиатура. */
@Composable
actual fun rememberIsKeyboardShortcutsPlatform(): Boolean = false
