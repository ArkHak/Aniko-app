package com.aniko.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.model.Release

/**
 * Legacy delegate kept for source compatibility with existing composeApp call sites
 * (`LibraryScreen.kt`/`SearchScreen.kt`/`ScheduleScreen.kt`). New code should call [TitleCard]
 * directly (Фаза 6, P6.T1/T3 — трек A плана Reelwave/Aniko).
 */
@Deprecated(
    "Use TitleCard",
    ReplaceWith("TitleCard(release, onClick, modifier, onLongClick = onLongClick)"),
)
@Composable
fun ReleaseCard(
    release: Release,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) = TitleCard(release = release, onClick = onClick, modifier = modifier, onLongClick = onLongClick)
