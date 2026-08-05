package com.anixkmp.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun VideoSurface(
    controller: PlayerController,
    modifier: Modifier,
) {
    // Заглушка: чёрный прямоугольник вместо AVPlayerLayer / WKWebView.
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
        content = {},
    )
}
