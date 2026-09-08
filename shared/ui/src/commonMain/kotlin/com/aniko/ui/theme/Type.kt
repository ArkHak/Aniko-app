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
 * `body*`/`label*` — Inter (текст).
 *
 * Фаза 2 фиксировала здесь стандартную Material3 size/lineHeight-шкалу, потому что точных
 * px/sp-значений в `docs/REELWAVE_PLAN.md` не было (план ссылался на недоступный на тот момент
 * архитектурный документ макета, `Reelwave Architecture.dc.html`, раздел «2. Design tokens —
 * `AppTheme`», `object Type`). По итогам сверки с макетом (Фаза design-check, 2026-08-23)
 * документ стал читаем через `claude_design` MCP — семь стилей ниже, для которых в нём есть явные
 * значения, приведены к ним точно (`displayLarge`/`titleLarge`/`titleMedium`/`bodyLarge`/
 * `bodyMedium`/`label`/`caption`). Остальные M3-слоты (`displayMedium/Small`, `headline*`,
 * `bodySmall`, `labelMedium`) в документе не описаны вовсе — оставлены как есть (без изменений),
 * а не досочинены по аналогии, по той же логике, что и [com.aniko.ui.theme.AnixColors.live]/
 * [com.aniko.ui.theme.AnixColors.warning] (не изобретать значения без дизайн-референса).
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
        // displayLarge: 30/36 ExtraBold — Reelwave Architecture.dc.html, §2 object Type.
        displayLarge = textStyle(manrope, FontWeight.ExtraBold, size = 30.0, lineHeight = 36.0),
        displayMedium = textStyle(manrope, FontWeight.ExtraBold, size = 45.0, lineHeight = 52.0),
        displaySmall = textStyle(manrope, FontWeight.Bold, size = 36.0, lineHeight = 44.0),
        headlineLarge = textStyle(manrope, FontWeight.Bold, size = 32.0, lineHeight = 40.0),
        headlineMedium = textStyle(manrope, FontWeight.Bold, size = 28.0, lineHeight = 36.0),
        headlineSmall = textStyle(manrope, FontWeight.Bold, size = 24.0, lineHeight = 32.0),
        // titleLarge: 22/28 Bold — совпадало со стандартной M3-шкалой и до этой правки.
        titleLarge = textStyle(manrope, FontWeight.Bold, size = 22.0, lineHeight = 28.0),
        // titleMedium: 16/22 Bold — Reelwave Architecture.dc.html, §2 object Type.
        titleMedium = textStyle(manrope, FontWeight.Bold, size = 16.0, lineHeight = 22.0),
        titleSmall = textStyle(manrope, FontWeight.SemiBold, size = 14.0, lineHeight = 20.0, letterSpacing = 0.1),
        // Inter — текст, лейблы.
        // bodyLarge: 15/22 Normal — Reelwave Architecture.dc.html, §2 object Type.
        bodyLarge = textStyle(inter, FontWeight.Normal, size = 15.0, lineHeight = 22.0),
        // bodyMedium: 13/19 Normal — Reelwave Architecture.dc.html, §2 object Type.
        bodyMedium = textStyle(inter, FontWeight.Normal, size = 13.0, lineHeight = 19.0),
        bodySmall = textStyle(inter, FontWeight.Normal, size = 12.0, lineHeight = 16.0, letterSpacing = 0.4),
        // "label" из плана: 12/16 SemiBold — Reelwave Architecture.dc.html, §2 object Type.
        labelLarge = textStyle(inter, FontWeight.SemiBold, size = 12.0, lineHeight = 16.0),
        labelMedium = textStyle(inter, FontWeight.Medium, size = 12.0, lineHeight = 16.0, letterSpacing = 0.5),
        // "caption" из плана: 11/14 Medium — Reelwave Architecture.dc.html, §2 object Type.
        labelSmall = textStyle(inter, FontWeight.Medium, size = 11.0, lineHeight = 14.0),
    )
}
