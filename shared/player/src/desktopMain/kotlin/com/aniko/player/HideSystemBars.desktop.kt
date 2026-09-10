package com.aniko.player

import androidx.compose.runtime.Composable

/** Desktop — no-op (ревью замечание #3): окно не имеет системных панелей. */
@Suppress("EmptyFunctionBlock") // Осознанный no-op — см. KDoc `expect fun` в `HideSystemBars.kt`.
@Composable
actual fun HideSystemBarsEffect() {
}
