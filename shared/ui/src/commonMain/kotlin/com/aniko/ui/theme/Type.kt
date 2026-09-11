package com.aniko.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import aniko.shared.ui.generated.resources.Res
import aniko.shared.ui.generated.resources.inter_bold
import aniko.shared.ui.generated.resources.inter_medium
import aniko.shared.ui.generated.resources.inter_regular
import aniko.shared.ui.generated.resources.inter_semibold
import aniko.shared.ui.generated.resources.jetbrains_mono_medium
import aniko.shared.ui.generated.resources.manrope_bold
import aniko.shared.ui.generated.resources.manrope_extrabold
import aniko.shared.ui.generated.resources.manrope_medium
import aniko.shared.ui.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

/**
 * Шрифтовая пара из Фазы 2 плана (P2.T3/P2.T4): Manrope для заголовков, Inter для текста.
 * Оба семейства — открытые шрифты Google Fonts (лицензия OFL), с полной поддержкой кириллицы
 * (см. `shared/ui/licenses/OFL-Manrope.txt` / `OFL-Inter.txt`).
 *
 * Manrope Medium (500) — статический инстанс, извлечённый из переменного шрифта под точное
 * соответствие макету Claude Design (Track A, финал).
 */
@Composable
internal fun manropeFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.manrope_medium, weight = FontWeight.Medium),
        Font(Res.font.manrope_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.manrope_bold, weight = FontWeight.Bold),
        Font(Res.font.manrope_extrabold, weight = FontWeight.ExtraBold),
    )

@Composable
internal fun interFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, weight = FontWeight.Normal),
        Font(Res.font.inter_medium, weight = FontWeight.Medium),
        Font(Res.font.inter_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.inter_bold, weight = FontWeight.Bold),
    )

/**
 * JetBrains Mono Medium (500) — используется точечно там, где макет Claude Design требует
 * моноширинный текст, сейчас это подпись выбранного языка в [com.aniko.ui.component.
 * AnixLanguagePicker] (`shared/ui/src/commonMain/kotlin/com/aniko/ui/component/ChipRow.kt`,
 * параметр `labelFontFamily`).
 *
 * Подсказка клавиш плеера на desktop, упомянутая в более раннем варианте этого KDoc
 * (`space · pause — ←/→ · seek — ↑/↓ · volume`), в текущем коде не существует: `PlayerScreen.kt`/
 * `PlayerDesktopControls.kt` такого текста не рисуют — на Desktop видео играет в системном
 * браузере (см. KDoc [com.aniko.app.feature.player.PlayerDesktopControls], P8.T1), у приложения
 * нет обработчика клавиатуры для плеера вовсе (`onKeyEvent`/`onPreviewKeyEvent` нигде не
 * используется). Подключать сюда эту подсказку не стали — это отдельная фича (обработка клавиш +
 * новый UI-элемент), а не подключение уже существующего шрифта.
 */
@Composable
internal fun jetBrainsMonoFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.jetbrains_mono_medium, weight = FontWeight.Medium),
    )

/**
 * Один стиль текста: пара (fontSize, lineHeight) в sp + вес + межбуквенный интервал.
 * Существует только чтобы вынести из [anixTypography] построчную сборку 15 [TextStyle] —
 * без этого хелпера функция превращается в детektor-нарушение `LongMethod` на ровном месте:
 * сама типографика не сложная, просто длинная как табличные данные.
 */
private fun textStyle(
    family: FontFamily,
    weight: FontWeight,
    size: Double,
    lineHeight: Double,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/**
 * Типографика приложения: `display*`/`headline*`/`title*` — Manrope (заголовки),
 * `body*`/`label*` — Inter (текст). Сохраняем двухшрифтовую пару как есть (брендовая
 * идентичность) — iOS-like редизайн (2026-09-11, полный HIG-паттерн) меняет ТОЛЬКО размерную
 * шкалу и стартовый набор весов, подгоняя её к официальной iOS-шкале (Apple HIG «Typography»,
 * Dynamic Type «Large» размер): Large Title 34/41, Title 1 28/34, Title 2 22/28, Title 3 20/25,
 * Headline/Body 17/22, Callout 16/21, Subheadline 15/20, Footnote 13/18, Caption 1 12/16,
 * Caption 2 11/13 — все веса Bold/Semibold/Regular по HIG. M3 [Typography] содержит 15 слотов
 * против 11 стилей iOS — где своего размера у iOS нет, переиспользован ближайший сосед по
 * иерархии (см. комментарии у каждого поля), а не выдуманное промежуточное значение.
 *
 * Прежняя шкала (Фаза 2, привязана к `Reelwave Architecture.dc.html`, `object Type`) заменена
 * целиком — то был ДРУГОЙ дизайн-референс (macOS-подобный дашборд), не HIG.
 *
 * Трекинг (letterSpacing) не заведён: точных iOS-значений трекинга по размеру шкалы (Apple
 * публикует их для San Francisco, не для Manrope/Inter) без визуальной калибровки на реальном
 * шрифте — не срисовывать вслепую, тот же принцип «не изобретать без референса», что и у
 * [com.aniko.ui.theme.AnixColors.live]/[warning].
 */
@Composable
internal fun anixTypography(): Typography {
    val manrope = manropeFontFamily()
    val inter = interFontFamily()

    return Typography(
        // Manrope — заголовки, крупные акценты (iOS Title-регистр).
        // displayLarge: Large Title 34/41 Bold.
        displayLarge = textStyle(manrope, FontWeight.Bold, size = 34.0, lineHeight = 41.0),
        // displayMedium: Title 1 28/34 Bold.
        displayMedium = textStyle(manrope, FontWeight.Bold, size = 28.0, lineHeight = 34.0),
        // displaySmall: Title 2 22/28 Bold.
        displaySmall = textStyle(manrope, FontWeight.Bold, size = 22.0, lineHeight = 28.0),
        // headlineLarge: тот же Title 2 — у iOS нет отдельного "ещё одного" уровня между
        // Title 2 и Title 3, повтор соседнего стиля честнее выдуманного промежуточного размера.
        headlineLarge = textStyle(manrope, FontWeight.Bold, size = 22.0, lineHeight = 28.0),
        // headlineMedium/headlineSmall: Title 3 20/25 Semibold (M3 даёт 3 headline-слота, iOS —
        // один Title 3, поэтому оба слота получают одно и то же значение).
        headlineMedium = textStyle(manrope, FontWeight.SemiBold, size = 20.0, lineHeight = 25.0),
        headlineSmall = textStyle(manrope, FontWeight.SemiBold, size = 20.0, lineHeight = 25.0),
        // titleLarge/titleMedium: Headline 17/22 Semibold (iOS Headline — самый частый стиль
        // заголовков карточек/строк; titleMedium уже используется по всему приложению как
        // подпись карточки — тот же слот, только новый размер).
        titleLarge = textStyle(manrope, FontWeight.SemiBold, size = 17.0, lineHeight = 22.0),
        titleMedium = textStyle(manrope, FontWeight.SemiBold, size = 17.0, lineHeight = 22.0),
        // titleSmall: Subheadline 15/20 Semibold.
        titleSmall = textStyle(manrope, FontWeight.SemiBold, size = 15.0, lineHeight = 20.0),
        // Inter — текст, лейблы (iOS Body-регистр).
        // bodyLarge: Body 17/22 Regular — основной текст (описания, длинные абзацы).
        bodyLarge = textStyle(inter, FontWeight.Normal, size = 17.0, lineHeight = 22.0),
        // bodyMedium: Callout 16/21 Regular.
        bodyMedium = textStyle(inter, FontWeight.Normal, size = 16.0, lineHeight = 21.0),
        // bodySmall: Subheadline 15/20 Regular.
        bodySmall = textStyle(inter, FontWeight.Normal, size = 15.0, lineHeight = 20.0),
        // labelLarge: Footnote 13/18 Semibold — кнопки/чипы часто требуют более плотный вес,
        // чем обычный текст той же величины (тот же приём, что и в исходной шкале Фазы 2).
        labelLarge = textStyle(inter, FontWeight.SemiBold, size = 13.0, lineHeight = 18.0),
        // labelMedium: Caption 1 12/16 Regular.
        labelMedium = textStyle(inter, FontWeight.Normal, size = 12.0, lineHeight = 16.0),
        // labelSmall: Caption 2 11/13 Regular.
        labelSmall = textStyle(inter, FontWeight.Normal, size = 11.0, lineHeight = 13.0),
    )
}
