package com.aniko.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Палитра. Значения зафиксированы дизайном (Claude Design, `Reelwave Prototype.dc.html`) —
 * design-tokens в OKLCH, здесь уже сконвертированы в sRGB hex формулой Ottosson (не пересчитывать
 * на глаз, значения переданы координатором построчно).
 *
 * Track A (точное соответствие макету, 2026-09-04) переписал эту палитру под точные значения
 * макета — было (Фаза 2/6/11 плана, `docs/REELWAVE_PLAN.md`): Primary=`#8B6FF0`,
 * Secondary=`#C0483F` (общий на обе темы), Warning=`#E0A030` (общий на обе темы),
 * BackgroundDark=`#0A0C12` (это было на самом деле значение bg-elevated, а не bg-page),
 * BackgroundLight=`#FAF9FD`, OnDark=`#EDE8F2`, OnLight=`#1A1730`. Ключевое структурное
 * изменение: макет задаёт всего ДВА уровня поверхности на тему — bg-page (страница) и
 * bg-elevated (карточки/поверхности) — вместо прежних трёх (Background/Surface/SurfaceElevated).
 * `SurfaceDarkElevated`/`SurfaceLightElevated` ниже поэтому равны `SurfaceDark`/`SurfaceLight`
 * (третьего, более светлого уровня макет не определяет) — поля-алиасы сохранены, чтобы
 * `surface`/`surfaceVariant` в [darkColorScheme]/[lightColorScheme] остались раздельными слотами
 * на случай, если третий уровень появится в макете позже.
 */
@Suppress("MagicNumber") // hex-литералы цвета — сами значения и есть содержательные константы,
// каждая уже поименована (Primary/Secondary/...), заводить отдельные именованные числа под них
// избыточно.
internal object AnixPalette {
    // Brand — accent/accent2/gold из макета теперь заданы РАЗДЕЛЬНО на тёмную и светлую тему
    // (в отличие от старой палитры, где Secondary/Warning были общими на обе темы) — таковы
    // точные значения макета.
    val PrimaryDark = Color(0xFF996DF0) // accent, тёмная тема
    val PrimaryLight = Color(0xFF7743CC) // accent, светлая тема
    val SecondaryDark = Color(0xFFF75C61) // accent2, тёмная тема
    val SecondaryLight = Color(0xFFBA0329) // accent2, светлая тема
    val WarningDark = Color(0xFFE18528) // gold, тёмная тема
    val WarningLight = Color(0xFFA54100) // gold, светлая тема

    // Semantic — вне таблицы токенов макета (там нет отдельных error/success), значения не
    // менялись Track A: точного дизайн-референса для них по-прежнему нет (та же логика, что и у
    // [AnixColors.live]/[AnixColors.warning] ниже — не изобретать значения без референса).
    val Error = Color(0xFFE5484D)
    val Success = Color(0xFF3FB27F)

    // Dark theme surfaces — bg-page/bg-elevated макета.
    val BackgroundDark = Color(0xFF05060A) // bg-page
    val SurfaceDark = Color(0xFF0A0C12) // bg-elevated
    val SurfaceDarkElevated = SurfaceDark // alias — см. KDoc объекта выше
    val OnDark = Color(0xFFFFFFFF) // text-1

    // AMOLED (P16.T20) — обводки/разделители на чистом чёрном фоне, чтобы сохранить читаемость.
    val AmoledOutline = Color(0xFF333333)
    val AmoledOutlineVariant = Color(0xFF222222)

    // Light theme surfaces — bg-page/bg-elevated макета.
    val BackgroundLight = Color(0xFFF3F5F9) // bg-page
    val SurfaceLight = Color(0xFFFDFDFF) // bg-elevated
    val SurfaceLightElevated = SurfaceLight // alias — см. KDoc объекта выше
    val OnLight = Color(0xFF0F141D) // text-1

    // Text variants — Фаза 11 (P11.T6), пересчитано Track A под новые accent/accent2/gold и
    // новые bg-elevated поверхности (оба изменились относительно старой палитры, см. KDoc
    // объекта выше). Метод не изменился: тот же hue/saturation, что и у исходного цвета, только
    // смещена светлота (темнее для светлой темы / светлее для тёмной), пока контраст
    // относительно самой требовательной поверхности своей темы не станет ≥4.5:1 (AA, обычный
    // текст). Для тёмной темы это [SurfaceDarkElevated] (`#0A0C12`), для светлой —
    // [SurfaceLightElevated] (`#FDFDFF`). Контраст посчитан по стандартной формуле WCAG
    // (relative luminance, python3, см. отчёт Track A) для каждого значения ниже.
    //
    // Primary/Secondary/Warning в новой палитре уже проходят порог БЕЗ сдвига светлоты — макет
    // сам выбрал accent-оттенки с запасом контраста на своей elevated-поверхности, поэтому ниже
    // они равны соответствующим Primary/Secondary/WarningDark|Light без изменений. Сдвиг
    // потребовался только для [ErrorTextLight] и [SuccessTextLight].
    val PrimaryTextDark = PrimaryDark // на SurfaceDarkElevated: 5.40 (без сдвига)
    val PrimaryTextLight = PrimaryLight // на SurfaceLightElevated: 5.98 (без сдвига)
    val SecondaryTextDark = SecondaryDark // на SurfaceDarkElevated: 6.20 (без сдвига)
    val SecondaryTextLight = SecondaryLight // на SurfaceLightElevated: 6.60 (без сдвига)
    val ErrorTextDark = Error // на SurfaceDarkElevated: 4.99 (без сдвига)
    val ErrorTextLight = Color(0xFFE12B30) // на SurfaceLightElevated: 4.52 (было 3.85 у Error как есть)
    val SuccessTextLight = Color(0xFF2F845E) // на SurfaceLightElevated: 4.51 (было 2.62 у Success как есть)
    val WarningTextLight = WarningLight // на SurfaceLightElevated: 6.16 (без сдвига)

    // t2-ряд — вторичный текст, 11 шагов lightness (chroma 0.02, hue 260 в OKLCH), уже
    // сконвертированные в sRGB hex значения макета (не пересчитывать).
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
    )

/**
 * AMOLED-вариант тёмной темы (P16.T20): те же акценты и тексты, но фон и все поверхности
 * чистый `#000000`, обводки/разделители — тёмно-серые, чтобы сохранить читаемость.
 */
internal val AnixAmoledColors =
    AnixDarkColors.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color.Black,
        outline = AnixPalette.AmoledOutline,
        outlineVariant = AnixPalette.AmoledOutlineVariant,
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

/** AMOLED переиспользует те же семантические/текстовые токены, что и обычная тёмная тема (P16.T20) —
 * меняются только поверхности [ColorScheme] (см. [AnixAmoledColors]), не токены [AnixColors]. */
internal val AnixAmoledExtraColors = AnixDarkExtraColors
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
