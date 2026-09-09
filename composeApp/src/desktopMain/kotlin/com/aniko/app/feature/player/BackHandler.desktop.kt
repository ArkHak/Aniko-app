package com.aniko.app.feature.player

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // Desktop-меню обрабатывает back отдельно через `onBackHandlerReady` (см. `Main.kt`);
    // Compose-компонент здесь не нужен.
}
