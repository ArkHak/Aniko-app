package com.anixkmp.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * Android и Desktop.
 *
 * TODO(фаза «плеер»): Android → Media3 `ExoPlayer` + `PlayerView`;
 * Desktop → VLCJ или JavaFX MediaPlayer через Swing-интероп.
 */
@Composable
actual fun rememberPlayerController(): PlayerController {
    val controller = remember { StubPlayerController(platformName = "jvm") }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
