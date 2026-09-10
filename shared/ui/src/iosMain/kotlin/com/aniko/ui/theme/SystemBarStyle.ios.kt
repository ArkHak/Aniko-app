package com.aniko.ui.theme

import androidx.compose.runtime.Composable

/** iOS — no-op (ревью замечание #4): статус-бар сам адаптирует стиль под контент на уровне
 * `UIViewController`, отдельного Android-подобного контроллера окна здесь нет. */
@Suppress("EmptyFunctionBlock") // Осознанный no-op — см. KDoc `expect fun` в `SystemBarStyle.kt`.
@Composable
actual fun SystemBarStyleEffect(darkTheme: Boolean) {
}
