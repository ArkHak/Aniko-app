package com.aniko.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Палитра. Значения зафиксированы дизайном (Reelwave-макет, Фаза 2 плана,
 * `docs/REELWAVE_PLAN.md`, находка №3): primary/secondary/error/success и фон/поверхности
 * тёмной и светлой темы.
 */
@Suppress("MagicNumber") // hex-литералы цвета — сами значения и есть содержательные константы,
// каждая уже поименована (Primary/Secondary/...), заводить отдельные именованные числа под них
// избыточно.
internal object AnixPalette {
    // Brand
    val Primary = Color(0xFF8B6FF0)
    val PrimaryDark = Color(0xFF6B4FD1)
    val Secondary = Color(0xFFC0483F)

    // Semantic
    val Error = Color(0xFFE5484D)
    val Success = Color(0xFF3FB27F)

    // Semantic — добавлено Фазой 6 (P6.T3/T9): янтарный, согласован на глаз с уже
    // существующими Primary (#8B6FF0, холодный фиолет) и Secondary (#C0483F, тёплый crimson) —
    // тёплый жёлто-оранжевый достаточно далёк по тону от обоих, чтобы не путаться с ними в бейджах.
    val Warning = Color(0xFFE0A030)

    // Dark theme surfaces
    val BackgroundDark = Color(0xFF0A0C12)
    val SurfaceDark = Color(0xFF13151D)
    val SurfaceDarkElevated = Color(0xFF1C1F2A)
    val OnDark = Color(0xFFEDE8F2)

    // Light theme surfaces
    val BackgroundLight = Color(0xFFFAF9FD)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceLightElevated = Color(0xFFF1EFF8)
    val OnLight = Color(0xFF1A1730)
}

internal val AnixDarkColors =
    darkColorScheme(
        primary = AnixPalette.Primary,
        onPrimary = Color.White,
        secondary = AnixPalette.Secondary,
        onSecondary = Color.White,
        error = AnixPalette.Error,
        onError = Color.White,
        background = AnixPalette.BackgroundDark,
        onBackground = AnixPalette.OnDark,
        surface = AnixPalette.SurfaceDark,
        onSurface = AnixPalette.OnDark,
        surfaceVariant = AnixPalette.SurfaceDarkElevated,
        onSurfaceVariant = AnixPalette.OnDark,
    )

internal val AnixLightColors =
    lightColorScheme(
        primary = AnixPalette.PrimaryDark,
        onPrimary = Color.White,
        secondary = AnixPalette.Secondary,
        onSecondary = Color.White,
        error = AnixPalette.Error,
        onError = Color.White,
        background = AnixPalette.BackgroundLight,
        onBackground = AnixPalette.OnLight,
        surface = AnixPalette.SurfaceLight,
        onSurface = AnixPalette.OnLight,
        surfaceVariant = AnixPalette.SurfaceLightElevated,
        onSurfaceVariant = AnixPalette.OnLight,
    )

/**
 * Токены цвета без слота в стандартной Material3 [androidx.compose.material3.ColorScheme]:
 * - [success] — статусы завершения/успеха (например, `completed` в списках, успешные тосты).
 * - [live] — индикатор «идёт эфир/трансляция». Точного отдельного значения в макете/плане нет,
 *   поэтому решение зафиксировано здесь: `live = secondary` (`#C0483F`) — тот же crimson-акцент,
 *   что и вторичный цвет бренда. Если дизайн впоследствии выделит для live отдельный оттенок,
 *   меняем только это поле, использования в фичах не тронутся.
 * - [warning] — предупреждающие статусы (Фаза 6, P6.T3/T9), точного дизайн-референса тоже нет:
 *   решение зафиксировано аналогично [live] — сознательно выбранный янтарный оттенок
 *   (см. `AnixPalette.Warning`), не изобретённый заново, если позже появится макет.
 * - [newEpisode] — бейдж «новая серия» на постере (P6.T3): по той же логике, что и [live],
 *   переиспользуем уже существующий `secondary`, а не заводим третий похожий crimson-оттенок.
 * - [posterScrim] — затемняющий градиент/подложка текста поверх постера, до Фазы 6 был
 *   захардкожен в `ReleaseCard.kt` (`Color.Black.copy(alpha = 0.55f)`); значение перенесено сюда
 *   без изменений, использование в `ReleaseCard.kt` предстоит переключить в рамках Трека A.
 * - [chartTrack]/[chartSeries] — подложка и палитра серий для графиков статистики (P6.T10/T11),
 *   выбраны на глаз из уже существующих брендовых/семантических цветов, чтобы не плодить новые
 *   hex без дизайн-референса (та же логика, что у [live]/[warning]).
 */
@Immutable
data class AnixColors(
    val success: Color = AnixPalette.Success,
    val onSuccess: Color = Color.White,
    val live: Color = AnixPalette.Secondary,
    val onLive: Color = Color.White,
    val warning: Color = AnixPalette.Warning,
    val onWarning: Color = Color.Black,
    val newEpisode: Color = AnixPalette.Secondary,
    val onNewEpisode: Color = Color.White,
    val posterScrim: Color = Color.Black.copy(alpha = 0.55f),
    val chartTrack: Color = Color.Gray.copy(alpha = 0.2f),
    val chartSeries: List<Color> =
        listOf(
            AnixPalette.Primary,
            AnixPalette.Secondary,
            AnixPalette.Success,
            AnixPalette.Warning,
            AnixPalette.PrimaryDark,
            Color.Gray,
        ),
)

internal val AnixDarkExtraColors = AnixColors()
internal val AnixLightExtraColors = AnixColors()

val LocalAnixColors = staticCompositionLocalOf { AnixColors() }
