package com.aniko.app.feature.player

import androidx.compose.runtime.Composable

/** Desktop — единственная платформа с физической клавиатурой как основным вводом (P8.T7). */
@Composable
actual fun rememberIsKeyboardShortcutsPlatform(): Boolean = true
