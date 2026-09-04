package com.aniko.player

import androidx.compose.runtime.Composable

/**
 * No-op на Desktop — у окна нет "ориентации" в мобильном смысле, а сам Desktop-плеер остаётся
 * стабом (системный браузер, `project_reelwave_desktop_player_backlog` п.2): кнопка "На весь
 * экран" на Desktop не показывается вовсе (см. `PlayerScreen.kt` — ветка `!controller.isSupported`
 * рисует [PlayerDesktopControls], не эту функцию), эта реализация — только чтобы `expect`/`actual`
 * компилировался для всех таргетов common-модуля.
 */
@Suppress("EmptyFunctionBlock") // Осознанный no-op, см. KDoc выше — не заглушка "забыли
// реализовать", а честный CUT: Desktop-плееру в принципе не из чего разворачивать ориентацию.
@Composable
actual fun LockLandscapeOrientationEffect() {
}
