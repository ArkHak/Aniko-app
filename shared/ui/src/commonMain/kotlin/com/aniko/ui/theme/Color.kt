package com.aniko.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Палитра. Значения — стартовые, подбираются на этапе дизайна. */
internal object AnixPalette {
    val Purple = Color(0xFF7C4DFF)
    val PurpleDark = Color(0xFF5B32CC)
    val Coral = Color(0xFFFF6E6E)
    val SurfaceDark = Color(0xFF121016)
    val SurfaceDarkElevated = Color(0xFF1C1922)
    val SurfaceLight = Color(0xFFFDFBFF)
    val OnDark = Color(0xFFEDE8F2)
    val OnLight = Color(0xFF1A1720)
}

internal val AnixDarkColors =
    darkColorScheme(
        primary = AnixPalette.Purple,
        onPrimary = Color.White,
        secondary = AnixPalette.Coral,
        background = AnixPalette.SurfaceDark,
        onBackground = AnixPalette.OnDark,
        surface = AnixPalette.SurfaceDark,
        onSurface = AnixPalette.OnDark,
        surfaceVariant = AnixPalette.SurfaceDarkElevated,
    )

internal val AnixLightColors =
    lightColorScheme(
        primary = AnixPalette.PurpleDark,
        onPrimary = Color.White,
        secondary = AnixPalette.Coral,
        background = AnixPalette.SurfaceLight,
        onBackground = AnixPalette.OnLight,
        surface = AnixPalette.SurfaceLight,
        onSurface = AnixPalette.OnLight,
    )
