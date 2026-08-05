package com.anixkmp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Корневая тема приложения. Оборачивает Material3 и добавляет собственные токены
 * через [LocalAnixDimens].
 */
@Composable
fun AnixTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAnixDimens provides AnixDimens()) {
        MaterialTheme(
            colorScheme = if (darkTheme) AnixDarkColors else AnixLightColors,
            content = content,
        )
    }
}

/** Быстрый доступ к токенам: `AnixTheme.dimens.spaceM`. */
object AnixThemeTokens {
    val dimens: AnixDimens
        @Composable get() = LocalAnixDimens.current
}
