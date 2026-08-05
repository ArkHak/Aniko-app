package com.anixkmp.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * iOS.
 *
 * TODO(фаза «плеер»): `AVPlayer` + `AVPlayerLayer` через `UIKitView`,
 * для embed-источников — `WKWebView`.
 */
@Composable
actual fun rememberPlayerController(): PlayerController {
    val controller = remember { StubPlayerController(platformName = "ios") }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
