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
import aniko.shared.ui.generated.resources.manrope_bold
import aniko.shared.ui.generated.resources.manrope_extrabold
import aniko.shared.ui.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

/**
 * Шрифтовая пара из Фазы 2 плана (P2.T3/P2.T4): Manrope для заголовков, Inter для текста.
 * Оба семейства — открытые шрифты Google Fonts (лицензия OFL), с полной поддержкой кириллицы
 * (см. `shared/ui/licenses/OFL-Manrope.txt` / `OFL-Inter.txt`).
 *
 * Веса, для которых нет отдельного статического файла (например, Manrope Medium/Regular),
 * сознательно не подключены — используем только те веса, что перечислены в плане
 * (Manrope 600/700/800, Inter 400–700).
 */
@Composable
internal fun manropeFontFamily(): FontFamily =
    FontFamily(
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
 * `body*`/`label*` — Inter (текст).
 *
 * Точных px/sp-значений в `docs/REELWAVE_PLAN.md` нет (план ссылается на недоступный
 * архитектурный документ макета) — размерная шкала здесь стандартная Material3 type scale,
 * без изменений в size/lineHeight, меняются только `fontFamily`/`fontWeight`.
 *
 * Соответствие названиям из плана (`displayLarge/titleLarge/titleMedium/bodyLarge/bodyMedium/
 * label/caption`) для полей, которых нет буквально в M3 [Typography] (`label`, `caption` — это
 * терминология Material2): `label` → [Typography.labelLarge], `caption` → [Typography.labelSmall].
 */
@Composable
internal fun anixTypography(): Typography {
    val manrope = manropeFontFamily()
    val inter = interFontFamily()

    return Typography(
        // Manrope — заголовки, крупные акценты.
        displayLarge = textStyle(manrope, FontWeight.ExtraBold, size = 57.0, lineHeight = 64.0, letterSpacing = -0.25),
        displayMedium = textStyle(manrope, FontWeight.ExtraBold, size = 45.0, lineHeight = 52.0),
        displaySmall = textStyle(manrope, FontWeight.Bold, size = 36.0, lineHeight = 44.0),
        headlineLarge = textStyle(manrope, FontWeight.Bold, size = 32.0, lineHeight = 40.0),
        headlineMedium = textStyle(manrope, FontWeight.Bold, size = 28.0, lineHeight = 36.0),
        headlineSmall = textStyle(manrope, FontWeight.Bold, size = 24.0, lineHeight = 32.0),
        titleLarge = textStyle(manrope, FontWeight.Bold, size = 22.0, lineHeight = 28.0),
        titleMedium = textStyle(manrope, FontWeight.SemiBold, size = 16.0, lineHeight = 24.0, letterSpacing = 0.15),
        titleSmall = textStyle(manrope, FontWeight.SemiBold, size = 14.0, lineHeight = 20.0, letterSpacing = 0.1),
        // Inter — текст, лейблы.
        bodyLarge = textStyle(inter, FontWeight.Normal, size = 16.0, lineHeight = 24.0, letterSpacing = 0.5),
        bodyMedium = textStyle(inter, FontWeight.Normal, size = 14.0, lineHeight = 20.0, letterSpacing = 0.25),
        bodySmall = textStyle(inter, FontWeight.Normal, size = 12.0, lineHeight = 16.0, letterSpacing = 0.4),
        // "label" из плана.
        labelLarge = textStyle(inter, FontWeight.Medium, size = 14.0, lineHeight = 20.0, letterSpacing = 0.1),
        labelMedium = textStyle(inter, FontWeight.Medium, size = 12.0, lineHeight = 16.0, letterSpacing = 0.5),
        // "caption" из плана.
        labelSmall = textStyle(inter, FontWeight.Medium, size = 11.0, lineHeight = 16.0, letterSpacing = 0.5),
    )
}
