package com.aniko.player

import androidx.compose.runtime.Composable

/** iOS — no-op (ревью замечание #3): см. KDoc `expect fun` в `HideSystemBars.kt`. */
@Suppress("EmptyFunctionBlock") // Осознанный no-op — см. KDoc `expect fun` в `HideSystemBars.kt`.
@Composable
actual fun HideSystemBarsEffect() {
}
