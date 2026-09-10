package com.aniko.ui.theme

import androidx.compose.runtime.Composable

/** Desktop — no-op (ревью замечание #4): окно не имеет системной статус-панели в Android-смысле. */
@Suppress("EmptyFunctionBlock") // Осознанный no-op — см. KDoc `expect fun` в `SystemBarStyle.kt`.
@Composable
actual fun SystemBarStyleEffect(darkTheme: Boolean) {
}
