package com.aniko.app.feature.player

import androidx.compose.runtime.Composable

/**
 * Мультиплатформенный перехватчик системной кнопки «назад».
 *
 * Android-реализация делегирует `androidx.activity.compose.BackHandler`; iOS и desktop — no-op,
 * потому что у них нет системного back/hardware-кнопки, который Compose мог бы перехватить
 * (desktop обрабатывает back через меню-обработчик `onBackHandlerReady`, см. `Main.kt`).
 */
@Composable
expect fun BackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
