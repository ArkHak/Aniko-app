package com.aniko.app.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent

/** Passthrough — см. KDoc [PlayerOverlayHost] (commonMain). */
@Composable
actual fun PlayerOverlayHost(
    modifier: Modifier,
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, content = content)
}
