package com.aniko.app.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Passthrough — см. KDoc [PlayerOverlayHost] (commonMain). */
@Composable
actual fun PlayerOverlayHost(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, content = content)
}
