package com.anixkmp.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Платформенная поверхность вывода видео.
 *
 * Android — `AndroidView(PlayerView)` / `WebView`, iOS — `UIKitView(AVPlayerLayer)` / `WKWebView`,
 * Desktop — Swing-интероп.
 */
@Composable
expect fun VideoSurface(
    controller: PlayerController,
    modifier: Modifier,
)
