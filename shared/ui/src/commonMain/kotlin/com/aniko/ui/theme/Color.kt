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

    // Text variants — Фаза 11 (P11.T6). Аудит WCAG показал, что Primary/Secondary/Error/
    // Success/Warning выше не проходят 4.5:1 (AA, обычный текст) как цвет ТЕКСТА на части
    // поверхностей — притом что как заливки/иконки/акценты эти же значения остаются как есть
    // (решение: базовую палитру не трогать). Ниже — тот же hue/saturation, что и у исходного
    // цвета, только смещена светлота (темнее для светлой темы / светлее для тёмной), пока
    // контраст относительно самой требовательной поверхности своей темы не станет ≥4.5:1.
    // Для тёмной темы это [SurfaceDarkElevated] (самая светлая из трёх тёмных поверхностей —
    // даёт наименьший контраст со светлым текстом), для светлой — [SurfaceLightElevated]
    // (самая тёмная из трёх светлых — по той же логике). Success/Warning в тёмной теме уже
    // проходят 4.5:1 без изменений (6.17 / 7.23 на элевейтед-поверхности) — отдельных
    // *TextDark для них нет, используются существующие [Success]/[Warning].
    val PrimaryTextDark = Color(0xFF8E73F0) // на SurfaceDarkElevated: 4.58 (было 4.40)
    val PrimaryTextLight = Color(0xFF714FED) // на SurfaceLightElevated: 4.56 (было 3.28)
    val SecondaryTextDark = Color(0xFFCC6B64) // на SurfaceDarkElevated: 4.58 (было 3.32)
    val SecondaryTextLight = Color(0xFFBA463D) // на SurfaceLightElevated: 4.57 (было 4.35)
    val ErrorTextDark = Color(0xFFE75559) // на SurfaceDarkElevated: 4.57 (было 4.20)
    val ErrorTextLight = Color(0xFFD51E24) // на SurfaceLightElevated: 4.56 (было 3.44)
    val SuccessTextLight = Color(0xFF2B7A57) // на SurfaceLightElevated: 4.58 (было 2.34)
    val WarningTextLight = Color(0xFF916416) // на SurfaceLightElevated: 4.57 (было 2.00)
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
 * - [primaryText]/[secondaryText]/[errorText]/[successText]/[warningText] — Фаза 11 (P11.T6):
 *   версии Primary/Secondary/Error/Success/Warning, проходящие WCAG 4.5:1 именно как цвет
 *   ТЕКСТА (не заливки/иконки/акцента) на поверхностях текущей темы — см. [AnixPalette] для
 *   точных hex и посчитанного контраста каждого варианта. В отличие от [success]/[warning]
 *   выше (которые про "текст/иконка ПОВЕРХ цветной заливки"), эти токены — про сам
 *   семантический цвет, используемый как текст НА поверхности (`surface`/`surfaceVariant`/
 *   `background`). Уже существующие места, где Primary/Secondary/Error/Success/Warning
 *   используются как цвет текста напрямую (не через эти токены), не переключены —
 *   это задача других треков Фазы 11 (см. отчёт P11.T6).
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
    val primaryText: Color = AnixPalette.PrimaryTextDark,
    val secondaryText: Color = AnixPalette.SecondaryTextDark,
    val errorText: Color = AnixPalette.ErrorTextDark,
    val successText: Color = AnixPalette.Success,
    val warningText: Color = AnixPalette.Warning,
)

internal val AnixDarkExtraColors =
    AnixColors(
        primaryText = AnixPalette.PrimaryTextDark,
        secondaryText = AnixPalette.SecondaryTextDark,
        errorText = AnixPalette.ErrorTextDark,
        successText = AnixPalette.Success,
        warningText = AnixPalette.Warning,
    )
internal val AnixLightExtraColors =
    AnixColors(
        primaryText = AnixPalette.PrimaryTextLight,
        secondaryText = AnixPalette.SecondaryTextLight,
        errorText = AnixPalette.ErrorTextLight,
        successText = AnixPalette.SuccessTextLight,
        warningText = AnixPalette.WarningTextLight,
    )

val LocalAnixColors = staticCompositionLocalOf { AnixColors() }
