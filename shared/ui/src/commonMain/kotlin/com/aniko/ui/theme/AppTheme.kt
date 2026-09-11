package com.aniko.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Корневая тема приложения. Оборачивает Material3 и добавляет собственные токены:
 * цвета (M3 [androidx.compose.material3.ColorScheme] + [LocalAnixColors] для токенов без слота
 * в M3), типографику ([anixTypography]) и spacing/radius ([LocalAnixDimens]).
 *
 * Раньше называлась `AnixTheme` — переименована в `AppTheme` (Фаза 2 плана, P2.T6) при
 * переносе на полный набор токенов; переименование было дешёвым: единственный вызов был в
 * `composeApp/.../App.kt`, он обновлён вместе с этим файлом.
 * По умолчанию светлая ([darkTheme] = false) — макет Home в Claude Design светлый (сверка
 * 2026-09-08 по скриншоту пользователя); системная тема macOS не учитывается. `App` передаёт
 * выбор явно из `ThemeStore` ("light"/"dark"/null → светлая); [TokenGalleryScreen] использует
 * собственный локальный переключатель. Тёмная тема (2026-09-10) объединена с прежним AMOLED-
 * вариантом; iOS-like редизайн (2026-09-11) заменил её значения на подлинный iOS
 * `systemGroupedBackground` (`#000000`/`#1C1C1E`, см. KDoc `AnixPalette`) — тёмная тема теперь
 * ДЕЙСТВИТЕЛЬНО чистый чёрный на странице (осознанная отмена более раннего анти-АМОЛЕД решения,
 * см. подробности там же).
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    SystemBarStyleEffect(darkTheme = darkTheme)
    val extraColors = if (darkTheme) AnixDarkExtraColors else AnixLightExtraColors
    val colorScheme = if (darkTheme) AnixDarkColors else AnixLightColors
    CompositionLocalProvider(
        LocalAnixDimens provides AnixDimens(),
        LocalAnixColors provides extraColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = anixTypography(),
        ) {
            // Корневой фон приложения (Track A, Foundation; iOS-like редизайн 2026-09-11 —
            // см. KDoc [com.aniko.ui.theme.AnixPalette]) — сплошная заливка `background` текущей
            // темы (iOS `systemGroupedBackground`), БЕЗ декоративных радиальных градиентов
            // ("блобов" макета Claude Design, см. `BackgroundGradient.kt` в истории git) —
            // подлинные iOS grouped-экраны (Settings/Mail/Notes) плоские, без цветных пятен под
            // контентом. Единственное место применения: всё дерево ниже (`content`) — все
            // экраны, все платформы, а не только Home, потому что `App()`
            // (composeApp/.../App.kt) вызывает [AppTheme] один раз на самом корне. Дочерние
            // `Scaffold` внутри `AdaptiveScaffold` красят `containerColor = Color.Transparent`,
            // чтобы не перекрывать этот фон своей непрозрачной плашкой.
            // 2026-09-08 (баг тёмной темы «не видно текст тайтлов»): где-то в контенте
            // LocalContentColor деградировал в тёмный (вероятно, contentColorFor(Transparent) у
            // Scaffold-обёрток) — текст по умолчанию (заголовки карточек/строк без явного color)
            // рисовался тёмным на тёмном. Явно пиним контентный цвет на onSurface текущей схемы
            // для всего дерева приложения (проверено пиксельно: до — 0 белых пикселей в зоне
            // тайтлов, после — белые глифы на месте).
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
                    content()
                }
            }
        }
    }
}

/** Быстрый доступ к токенам: `AnixThemeTokens.dimens.spaceM`, `AnixThemeTokens.colors.success`. */
object AnixThemeTokens {
    val dimens: AnixDimens
        @Composable get() = LocalAnixDimens.current

    val colors: AnixColors
        @Composable get() = LocalAnixColors.current
}
