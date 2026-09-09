package com.aniko.app.feature.player

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS не имеет системной кнопки «назад», перехватываемой Compose; выход из плеера — через
    // стрелку в оверлее (fullscreen→compact→exit, см. KDoc `PlayerOverlay`).
}
