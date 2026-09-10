package com.aniko.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Палитра. Brand-токены (Primary/Warning + фоновые "блобы") и фоновые поверхности пересчитаны
 * 2026-09-10 под официальную иконку приложения (иллюстрация девушки с фиолетовыми волосами на
 * индиго-фоне, см. отчёт задачи в `docs/REELWAVE_PLAN.md`) — метод: HSL-сэмплы иконки (фон/волосы
 * ≈ hue 248°, звёзды-акцент ≈ hue 37°) заменили hue исходных Primary/Warning значений макета
 * (Claude Design, было hue 260°/30°) при неизменных saturation/lightness — тем самым WCAG-
 * контраст, посчитанный для старых значений (см. числа в комментариях ниже), сохраняется с
 * точностью до 0.01. Error/Success/Secondary(accent2, live/newEpisode-бейджи) — НЕ тронуты: это
 * семантические цвета вне визуальной идентичности бренда (красный "live" — универсальная
 * UX-конвенция), референса в иконке для них нет, эта задача осознанно их не переизобретает.
 *
 * Тёмная тема (2026-09-10, ревью замечание #1): АМОЛЕД-вариант (чистый `#000000`, P16.T20)
 * отменён — референс официального Anixart 10 (декомпилированный APK,
 * `res/values-night/colors.xml`) использует `screen_background = #121212` (НЕ чёрный) и
 * заметно более светлый `bottom_nav_background = #252525` для elevated-поверхностей (тот же
 * принцип различия «страница/поверхность», что и у нас, просто с бОльшим разрывом). Взят тот же
 * принцип (не чистый чёрный + различимый elevated-уровень), но цвет — не нейтральный серый
 * Anixart, а тон иконки (hue 250°, тот же, что у Primary/блобов): [BackgroundDark] ≈ L7%,
 * [SurfaceDark] ≈ L10% (различимый elevated-уровень для нав-бара/карточек, но без потери WCAG-
 * контраста accent-цветов — проверено python3-скриптом, ≥4.5:1 для всех Primary/Secondary/
 * Error/Warning на [SurfaceDark]). [DarkOutline]/[DarkOutlineVariant] (бывшие
 * `AmoledOutline`/`AmoledOutlineVariant`) пересчитаны в тот же hue.
 */
@Suppress("MagicNumber") // hex-литералы цвета — сами значения и есть содержательные константы,
// каждая уже поименована (Primary/Secondary/...), заводить отдельные именованные числа под них
// избыточно.
internal object AnixPalette {
    // Brand — Primary/Warning пересчитаны под иконку (см. KDoc объекта); Secondary — не тронут.
    val PrimaryDark = Color(0xFF836DF0) // accent, тёмная тема (hue 250°, было 260°)
    val PrimaryLight = Color(0xFF5A43CC) // accent, светлая тема (hue 250°, было 263°)
    val SecondaryDark = Color(0xFFF75C61) // accent2, тёмная тема — не тронут
    val SecondaryLight = Color(0xFFBA0329) // accent2, светлая тема — не тронут
    val WarningDark = Color(0xFFE19A28) // gold, тёмная тема (hue 37°, было 30°)
    val WarningLight = Color(0xFFA56600) // gold, светлая тема (hue 37°, было 24°)

    // Semantic — вне таблицы токенов макета (там нет отдельных error/success), значения не
    // менялись: точного дизайн-референса для них по-прежнему нет (та же логика, что и у
    // [AnixColors.live]/[AnixColors.warning] ниже — не изобретать значения без референса).
    val Error = Color(0xFFE5484D)
    val Success = Color(0xFF3FB27F)

    // Dark theme surfaces (2026-09-10, ревью замечание #1) — hue иконки (250°), НЕ чистый
    // чёрный/серый; два различимых уровня (bg-page темнее bg-elevated), тот же принцип, что у
    // референса Anixart 10 (`screen_background`/`bottom_nav_background`, см. KDoc объекта).
    val BackgroundDark = Color(0xFF0E0C18) // bg-page (H250 S35 L7%)
    val SurfaceDark = Color(0xFF141221) // bg-elevated (H250 S30 L10%) — нав-бар/карточки светлее
    val SurfaceDarkElevated = SurfaceDark // alias — см. KDoc объекта выше
    val OnDark = Color(0xFFFFFFFF) // text-1

    // Обводки/разделители тёмной темы — тот же hue 250°, что и поверхности; заданы явно, т.к. M3
    // не подбирает их алгоритмически от произвольного (не нейтрального) surface-тона.
    val DarkOutline = Color(0xFF353149)
    val DarkOutlineVariant = Color(0xFF262334)

    // Light theme surfaces — bg-page пересчитан под hue иконки (250°, было 220°), bg-elevated
    // не тронут (уже практически белый, разница неразличима).
    val BackgroundLight = Color(0xFFF4F3F9) // bg-page
    val SurfaceLight = Color(0xFFFDFDFF) // bg-elevated
    val SurfaceLightElevated = SurfaceLight // alias — см. KDoc объекта выше
    val OnLight = Color(0xFF0F141D) // text-1

    // Text variants — Фаза 11 (P11.T6), пересчитано под новые Primary/Warning/поверхности
    // (2026-09-10, см. KDoc объекта); метод не изменился: тот же hue/saturation, что и у
    // исходного цвета, только смещена светлота (темнее для светлой темы / светлее для тёмной),
    // пока контраст относительно самой требовательной поверхности своей темы не станет ≥4.5:1
    // (AA, обычный текст). Для тёмной темы это [SurfaceDarkElevated] (`#141221`), для светлой —
    // [SurfaceLightElevated] (`#FDFDFF`). Контраст посчитан по стандартной формуле WCAG
    // (relative luminance, python3) для каждого значения ниже.
    //
    // Primary/Secondary/Warning в палитре уже проходят порог БЕЗ сдвига светлоты — выбранные
    // accent-оттенки идут с запасом контраста на своей elevated-поверхности, поэтому ниже они
    // равны соответствующим Primary/Secondary/WarningDark|Light без изменений.
    val PrimaryTextDark = PrimaryDark // на #141221: 4.75 (было 5.41 на чистом чёрном)
    val PrimaryTextLight = PrimaryLight // на SurfaceLightElevated: 6.65 (без сдвига)
    val SecondaryTextDark = SecondaryDark // на #141221: 5.85 (было 6.20 на чистом чёрном)
    val SecondaryTextLight = SecondaryLight // на SurfaceLightElevated: 6.60 (без сдвига)
    val ErrorTextDark = Error // на #141221: 4.71 (было 4.99 на чистом чёрном)
    val ErrorTextLight = Color(0xFFE12B30) // на SurfaceLightElevated: 4.52 (было 3.85 у Error как есть)
    val SuccessTextLight = Color(0xFF2F845E) // на SurfaceLightElevated: 4.51 (было 2.62 у Success как есть)
    val WarningTextLight = WarningLight // на SurfaceLightElevated: 4.59 (без сдвига)

    // t2-ряд — вторичный текст, 11 шагов lightness (chroma 0.02, hue 260 в OKLCH) — НЕ тронут:
    // chroma настолько мала, что пересчёт под hue 250° иконки дал бы неразличимую на глаз
    // разницу в sRGB, а риск случайно испортить откалиброванный по контрасту 11-шаговый ряд
    // реальный (уже сконвертированные в sRGB hex значения макета, не пересчитывать).
    val TextSecondaryDark45 = Color(0xFF4F5661)
    val TextSecondaryDark48 = Color(0xFF575E69)
    val TextSecondaryDark50 = Color(0xFF5D646F)
    val TextSecondaryDark55 = Color(0xFF6B727E)
    val TextSecondaryDark58 = Color(0xFF737B87)
    val TextSecondaryDark60 = Color(0xFF79818D)
    val TextSecondaryDark62 = Color(0xFF7F8793)
    val TextSecondaryDark65 = Color(0xFF88909C)
    val TextSecondaryDark70 = Color(0xFF979FAB)
    val TextSecondaryDark72 = Color(0xFF9DA5B1)
    val TextSecondaryDark75 = Color(0xFFA7AEBB)

    val TextSecondaryLight45 = Color(0xFF79818D)
    val TextSecondaryLight48 = Color(0xFF717884)
    val TextSecondaryLight50 = Color(0xFF6B727E)
    val TextSecondaryLight55 = Color(0xFF5D646F)
    val TextSecondaryLight58 = Color(0xFF545B66)
    val TextSecondaryLight60 = Color(0xFF4F5661)
    val TextSecondaryLight62 = Color(0xFF49505B)
    val TextSecondaryLight65 = Color(0xFF414853)
    val TextSecondaryLight70 = Color(0xFF343B45)
    val TextSecondaryLight72 = Color(0xFF2F3640)
    val TextSecondaryLight75 = Color(0xFF282E38)
}

/**
 * Единственная тёмная тема (2026-09-10, слияние с прежним AMOLED-вариантом — см. KDoc
 * [AnixPalette]): чистый `#000000` на background/surface, обводки/разделители заданы явно
 * ([AnixPalette.DarkOutline]/[DarkOutlineVariant]), потому что M3 не может вычислить их
 * алгоритмически из surface-тона, когда background == surface == чёрный.
 */
internal val AnixDarkColors =
    darkColorScheme(
        primary = AnixPalette.PrimaryDark,
        onPrimary = Color.White,
        secondary = AnixPalette.SecondaryDark,
        onSecondary = Color.White,
        error = AnixPalette.Error,
        onError = Color.White,
        background = AnixPalette.BackgroundDark, // bg-page
        onBackground = AnixPalette.OnDark,
        surface = AnixPalette.SurfaceDark, // bg-elevated
        onSurface = AnixPalette.OnDark,
        surfaceVariant = AnixPalette.SurfaceDarkElevated, // bg-elevated (alias, см. KDoc AnixPalette)
        onSurfaceVariant = AnixPalette.OnDark,
        outline = AnixPalette.DarkOutline,
        outlineVariant = AnixPalette.DarkOutlineVariant,
    )

internal val AnixLightColors =
    lightColorScheme(
        primary = AnixPalette.PrimaryLight,
        onPrimary = Color.White,
        secondary = AnixPalette.SecondaryLight,
        onSecondary = Color.White,
        error = AnixPalette.Error,
        onError = Color.White,
        background = AnixPalette.BackgroundLight, // bg-page
        onBackground = AnixPalette.OnLight,
        surface = AnixPalette.SurfaceLight, // bg-elevated
        onSurface = AnixPalette.OnLight,
        surfaceVariant = AnixPalette.SurfaceLightElevated, // bg-elevated (alias, см. KDoc AnixPalette)
        onSurfaceVariant = AnixPalette.OnLight,
    )

/**
 * Токены цвета без слота в стандартной Material3 [androidx.compose.material3.ColorScheme]:
 * - [success] — статусы завершения/успеха (например, `completed` в списках, успешные тосты).
 * - [live] — индикатор «идёт эфир/трансляция». Точного отдельного значения в макете/плане нет,
 *   поэтому решение зафиксировано здесь: `live = secondary` (accent2 макета), тот же
 *   crimson-акцент, что и вторичный цвет бренда, теперь раздельный на тёмную/светлую тему вместе
 *   с [AnixPalette.SecondaryDark]/[AnixPalette.SecondaryLight]. Если дизайн впоследствии выделит
 *   для live отдельный оттенок, меняем только это поле, использования в фичах не тронутся.
 * - [warning] — предупреждающие статусы (Фаза 6, P6.T3/T9), точного отдельного дизайн-референса
 *   для СЕМАНТИКИ нет (сам цвет — gold из таблицы токенов макета, значение зафиксировано): решение
 *   зафиксировано аналогично [live] — сознательно выбранный янтарный оттенок
 *   (см. [AnixPalette.WarningDark]/[AnixPalette.WarningLight]), не изобретённый заново.
 * - [newEpisode] — бейдж «новая серия» на постере (P6.T3): по той же логике, что и [live],
 *   переиспользуем уже существующий `secondary`, а не заводим третий похожий crimson-оттенок.
 * - [posterScrim] — затемняющий градиент/подложка текста поверх постера, до Фазы 6 был
 *   захардкожен в `ReleaseCard.kt` (`Color.Black.copy(alpha = 0.55f)`); значение перенесено сюда
 *   без изменений, использование в `ReleaseCard.kt` предстоит переключить в рамках Трека A.
 * - [chartTrack]/[chartSeries] — подложка и палитра серий для графиков статистики (P6.T10/T11),
 *   выбраны на глаз из уже существующих брендовых/семантических цветов, чтобы не плодить новые
 *   hex без дизайн-референса (та же логика, что у [live]/[warning]). Категориальный набор общий
 *   на обе темы (не переопределяется в Dark/Light-инстансах), как и раньше.
 * - [primaryText]/[secondaryText]/[errorText]/[successText]/[warningText] — Фаза 11 (P11.T6):
 *   версии Primary/Secondary/Error/Success/Warning, проходящие WCAG 4.5:1 именно как цвет
 *   ТЕКСТА (не заливки/иконки/акцента) на поверхностях текущей темы — см. [AnixPalette] для
 *   точных hex и посчитанного контраста каждого варианта. В отличие от [success]/[warning]
 *   выше (которые про "текст/иконка ПОВЕРХ цветной заливки"), эти токены — про сам
 *   семантический цвет, используемый как текст НА поверхности (`surface`/`surfaceVariant`/
 *   `background`). Уже существующие места, где Primary/Secondary/Error/Success/Warning
 *   используются как цвет текста напрямую (не через эти токены), не переключены —
 *   это задача других треков Фазы 11 (см. отчёт P11.T6).
 * - [textSecondary45]..[textSecondary75] — Track A: t2-ряд макета (вторичный текст, 11 шагов
 *   lightness при фиксированных chroma/hue). Числовой суффикс — процентный шаг из макета
 *   (`t2-45`..`t2-75`), не абсолютное значение lightness/alpha. Раздельные наборы на тёмную и
 *   светлую тему (см. [AnixDarkExtraColors]/[AnixLightExtraColors]) — в отличие от
 *   [textSecondary]-подобных полей выше, у этого ряда нет одного класс-дефолта, годного для
 *   обеих тем: шаги в макете идут в ПРОТИВОПОЛОЖНЫХ направлениях (t2-45 — самый тёмный шаг на
 *   тёмной теме, но самый светлый на светлой).
 * - [overlay03]..[overlay18] — Track A: w0x-ряд макета (альфа-рампа elevation/бордеров, 11
 *   шагов). Суффикс — доля alpha ×100 из имени токена макета (`w03` → 0.03, `w045` → 0.045, ...).
 *   Тёмная тема — белая подложка (`rgba(255,255,255,X)`), светлая — чёрная
 *   (`rgba(0,0,0,X)`) с ДРУГИМИ (не зеркальными) значениями alpha — так задано макетом, чтобы
 *   визуальный вес рамки/элевейшена был одинаковым на глаз в обеих темах.
 */
@Immutable
@Suppress("LongParameterList") // Токены design-системы — плоский список именованных полей с
// понятными дефолтами (не бизнес-логика с необходимостью группировки), дробление на вложенные
// data class ради обхода линта добавило бы косвенность без пользы (тот же аргумент, что уже
// использован в `AdaptiveScaffold.kt` для похожего случая).
data class AnixColors(
    val success: Color = AnixPalette.Success,
    val onSuccess: Color = Color.White,
    val live: Color = AnixPalette.SecondaryDark,
    val onLive: Color = Color.White,
    val warning: Color = AnixPalette.WarningDark,
    val onWarning: Color = Color.Black,
    val newEpisode: Color = AnixPalette.SecondaryDark,
    val onNewEpisode: Color = Color.White,
    val posterScrim: Color = Color.Black.copy(alpha = 0.55f),
    val chartTrack: Color = Color.Gray.copy(alpha = 0.2f),
    val chartSeries: List<Color> =
        listOf(
            AnixPalette.PrimaryDark,
            AnixPalette.SecondaryDark,
            AnixPalette.Success,
            AnixPalette.WarningDark,
            AnixPalette.PrimaryLight,
            Color.Gray,
        ),
    val primaryText: Color = AnixPalette.PrimaryTextDark,
    val secondaryText: Color = AnixPalette.SecondaryTextDark,
    val errorText: Color = AnixPalette.ErrorTextDark,
    val successText: Color = AnixPalette.Success,
    val warningText: Color = AnixPalette.WarningDark,
    val textSecondary45: Color = AnixPalette.TextSecondaryDark45,
    val textSecondary48: Color = AnixPalette.TextSecondaryDark48,
    val textSecondary50: Color = AnixPalette.TextSecondaryDark50,
    val textSecondary55: Color = AnixPalette.TextSecondaryDark55,
    val textSecondary58: Color = AnixPalette.TextSecondaryDark58,
    val textSecondary60: Color = AnixPalette.TextSecondaryDark60,
    val textSecondary62: Color = AnixPalette.TextSecondaryDark62,
    val textSecondary65: Color = AnixPalette.TextSecondaryDark65,
    val textSecondary70: Color = AnixPalette.TextSecondaryDark70,
    val textSecondary72: Color = AnixPalette.TextSecondaryDark72,
    val textSecondary75: Color = AnixPalette.TextSecondaryDark75,
    val overlay03: Color = Color.White.copy(alpha = 0.03f),
    val overlay045: Color = Color.White.copy(alpha = 0.045f),
    val overlay05: Color = Color.White.copy(alpha = 0.05f),
    val overlay06: Color = Color.White.copy(alpha = 0.06f),
    val overlay07: Color = Color.White.copy(alpha = 0.07f),
    val overlay08: Color = Color.White.copy(alpha = 0.08f),
    val overlay09: Color = Color.White.copy(alpha = 0.09f),
    val overlay10: Color = Color.White.copy(alpha = 0.1f),
    val overlay12: Color = Color.White.copy(alpha = 0.12f),
    val overlay16: Color = Color.White.copy(alpha = 0.16f),
    val overlay18: Color = Color.White.copy(alpha = 0.18f),
)

internal val AnixDarkExtraColors =
    AnixColors(
        live = AnixPalette.SecondaryDark,
        warning = AnixPalette.WarningDark,
        newEpisode = AnixPalette.SecondaryDark,
        primaryText = AnixPalette.PrimaryTextDark,
        secondaryText = AnixPalette.SecondaryTextDark,
        errorText = AnixPalette.ErrorTextDark,
        successText = AnixPalette.Success,
        warningText = AnixPalette.WarningDark,
        textSecondary45 = AnixPalette.TextSecondaryDark45,
        textSecondary48 = AnixPalette.TextSecondaryDark48,
        textSecondary50 = AnixPalette.TextSecondaryDark50,
        textSecondary55 = AnixPalette.TextSecondaryDark55,
        textSecondary58 = AnixPalette.TextSecondaryDark58,
        textSecondary60 = AnixPalette.TextSecondaryDark60,
        textSecondary62 = AnixPalette.TextSecondaryDark62,
        textSecondary65 = AnixPalette.TextSecondaryDark65,
        textSecondary70 = AnixPalette.TextSecondaryDark70,
        textSecondary72 = AnixPalette.TextSecondaryDark72,
        textSecondary75 = AnixPalette.TextSecondaryDark75,
        overlay03 = Color.White.copy(alpha = 0.03f),
        overlay045 = Color.White.copy(alpha = 0.045f),
        overlay05 = Color.White.copy(alpha = 0.05f),
        overlay06 = Color.White.copy(alpha = 0.06f),
        overlay07 = Color.White.copy(alpha = 0.07f),
        overlay08 = Color.White.copy(alpha = 0.08f),
        overlay09 = Color.White.copy(alpha = 0.09f),
        overlay10 = Color.White.copy(alpha = 0.1f),
        overlay12 = Color.White.copy(alpha = 0.12f),
        overlay16 = Color.White.copy(alpha = 0.16f),
        overlay18 = Color.White.copy(alpha = 0.18f),
    )

internal val AnixLightExtraColors =
    AnixColors(
        live = AnixPalette.SecondaryLight,
        warning = AnixPalette.WarningLight,
        newEpisode = AnixPalette.SecondaryLight,
        primaryText = AnixPalette.PrimaryTextLight,
        secondaryText = AnixPalette.SecondaryTextLight,
        errorText = AnixPalette.ErrorTextLight,
        successText = AnixPalette.SuccessTextLight,
        warningText = AnixPalette.WarningTextLight,
        textSecondary45 = AnixPalette.TextSecondaryLight45,
        textSecondary48 = AnixPalette.TextSecondaryLight48,
        textSecondary50 = AnixPalette.TextSecondaryLight50,
        textSecondary55 = AnixPalette.TextSecondaryLight55,
        textSecondary58 = AnixPalette.TextSecondaryLight58,
        textSecondary60 = AnixPalette.TextSecondaryLight60,
        textSecondary62 = AnixPalette.TextSecondaryLight62,
        textSecondary65 = AnixPalette.TextSecondaryLight65,
        textSecondary70 = AnixPalette.TextSecondaryLight70,
        textSecondary72 = AnixPalette.TextSecondaryLight72,
        textSecondary75 = AnixPalette.TextSecondaryLight75,
        overlay03 = Color.Black.copy(alpha = 0.045f),
        overlay045 = Color.Black.copy(alpha = 0.065f),
        overlay05 = Color.Black.copy(alpha = 0.075f),
        overlay06 = Color.Black.copy(alpha = 0.09f),
        overlay07 = Color.Black.copy(alpha = 0.1f),
        overlay08 = Color.Black.copy(alpha = 0.11f),
        overlay09 = Color.Black.copy(alpha = 0.12f),
        overlay10 = Color.Black.copy(alpha = 0.13f),
        overlay12 = Color.Black.copy(alpha = 0.16f),
        overlay16 = Color.Black.copy(alpha = 0.2f),
        overlay18 = Color.Black.copy(alpha = 0.22f),
    )

val LocalAnixColors = staticCompositionLocalOf { AnixColors() }
