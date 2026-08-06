package com.aniko.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Токены отступов и размеров. Использовать вместо «магических» dp в фичах. */
data class AnixDimens(
    val spaceXs: Dp = 4.dp,
    val spaceS: Dp = 8.dp,
    val spaceM: Dp = 16.dp,
    val spaceL: Dp = 24.dp,
    val spaceXl: Dp = 32.dp,
    val cornerS: Dp = 8.dp,
    val cornerM: Dp = 12.dp,
    val cornerL: Dp = 20.dp,
    /** Стандартная ширина постера в сетке каталога. */
    val posterWidth: Dp = 120.dp,
    /** Anixart-постеры близки к 2:3. */
    val posterAspectRatio: Float = 2f / 3f,
)

val LocalAnixDimens = staticCompositionLocalOf { AnixDimens() }
