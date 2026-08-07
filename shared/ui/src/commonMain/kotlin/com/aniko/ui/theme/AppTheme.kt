package com.aniko.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Корневая тема приложения. Оборачивает Material3 и добавляет собственные токены:
 * цвета (M3 [androidx.compose.material3.ColorScheme] + [LocalAnixColors] для токенов без слота
 * в M3), типографику ([anixTypography]) и spacing/radius ([LocalAnixDimens]).
 *
 * Раньше называлась `AnixTheme` — переименована в `AppTheme` (Фаза 2 плана, P2.T6) при
 * переносе на полный набор токенов; переименование было дешёвым: единственный вызов был в
 * `composeApp/.../App.kt`, он обновлён вместе с этим файлом.
 *
 * По умолчанию [darkTheme] берётся из системной настройки через [isSystemInDarkTheme] — явный
 * override (например, ручной переключатель темы в настройках приложения) остаётся возможным,
 * это по-прежнему обычный параметр функции, а не жёстко зашитое поведение.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extraColors = if (darkTheme) AnixDarkExtraColors else AnixLightExtraColors
    CompositionLocalProvider(
        LocalAnixDimens provides AnixDimens(),
        LocalAnixColors provides extraColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) AnixDarkColors else AnixLightColors,
            typography = anixTypography(),
            content = content,
        )
    }
}

/** Быстрый доступ к токенам: `AnixThemeTokens.dimens.spaceM`, `AnixThemeTokens.colors.success`. */
object AnixThemeTokens {
    val dimens: AnixDimens
        @Composable get() = LocalAnixDimens.current

    val colors: AnixColors
        @Composable get() = LocalAnixColors.current
}
