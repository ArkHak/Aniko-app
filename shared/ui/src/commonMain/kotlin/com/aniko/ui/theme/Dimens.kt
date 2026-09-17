package com.aniko.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Токены отступов и радиусов скругления. Использовать вместо «магических» dp в фичах.
 *
 * Набор spacing (4/8/12/16/24/32/48) и radius (8/12/16/20/999) зафиксирован
 * `docs/REELWAVE_PLAN.md`, Фаза 2 (P2.T5). iOS-like редизайн (2026-09-11, полный HIG-паттерн):
 * [cornerM] поднят 12→14dp — стандартный радиус карточки/строки в iOS (виджеты, grouped-строки,
 * панели действий), самый частый corner-токен в приложении, поэтому самый заметный на глаз
 * сдвиг «фактуры» в сторону iOS. [cornerS]/[corner16]/[cornerL] оставлены — они уже были
 * достаточно близки к соответствующим iOS-значениям (мелкие элементы/крупные листы), трогать их
 * без визуальной сверки на реальном экране рискованно (валидируются экранами Apply-фазы этого же
 * редизайна, не вслепую по всему набору сразу).
 *
 * [cornerPill] — токен «999» из плана означает «полностью скруглённый» (pill/капсула), а не
 * буквальный радиус в 999dp. Compose не имеет отдельного типа `Dp.Infinity`, а
 * [androidx.compose.ui.unit.Dp] не умеет представлять «бесконечность» как значение токена
 * (в отличие от [androidx.compose.foundation.shape.CircleShape], которое пригодно только для
 * готовых `Shape`, а не для произвольных мест, ожидающих `Dp`). Идиоматичное решение в экосистеме
 * Compose — задать заведомо большое конечное значение dp: `RoundedCornerShape` клэмпит радиус до
 * половины меньшей стороны компонента, поэтому любое значение, заведомо большее реальной
 * высоты/ширины элемента (кнопки, чипа, бейджа), даёт визуально идентичный результат полному
 * скруглению.
 * `999.dp` — с большим запасом больше высоты любого реального UI-элемента в этом приложении.
 */
data class AnixDimens(
    val spaceXs: Dp = 4.dp,
    val spaceS: Dp = 8.dp,
    val space12: Dp = 12.dp,
    val spaceM: Dp = 16.dp,
    val spaceL: Dp = 24.dp,
    val spaceXl: Dp = 32.dp,
    val space48: Dp = 48.dp,
    val cornerS: Dp = 8.dp,
    /** iOS-стандарт карточки/строки (было 12dp, см. KDoc класса). */
    val cornerM: Dp = 14.dp,
    val corner16: Dp = 16.dp,
    val cornerL: Dp = 20.dp,
    /** Полное скругление (pill/капсула). См. KDoc класса — намеренно большое конечное значение. */
    val cornerPill: Dp = 999.dp,
    /** Стандартная ширина постера в сетке каталога. */
    val posterWidth: Dp = 120.dp,
    /** Anixart-постеры близки к 2:3. */
    val posterAspectRatio: Float = 2f / 3f,
    /** Компактный постер — используется в [com.aniko.ui.component] `ProgressRow` (Фаза 6, P6.T2). */
    val posterWidthS: Dp = 88.dp,
    /** Увеличенный постер рельсы на Medium/Expanded window size class (Фаза 6, P6.T1). */
    val posterWidthL: Dp = 160.dp,
    /** Диаметр кружка-бейджа статуса/рейтинга поверх постера (Фаза 6, P6.T3). */
    val badgeSize: Dp = 20.dp,
    /** Размер иконки внутри [badgeSize]-бейджа. */
    val badgeIconSize: Dp = 12.dp,
    /** Высота линейного индикатора прогресса просмотра (Фаза 6, P6.T2). */
    val progressBarHeight: Dp = 4.dp,
    /** Минимальный размер ячейки в сетке номеров серий (Фаза 6, P6.T4). */
    val episodeCellMinSize: Dp = 56.dp,
    /** Минимальный размер интерактивной области — ориентир доступности (WCAG/Material). */
    val minTouchTarget: Dp = 48.dp,
    /** Толщина кольца донат-диаграммы статистики профиля (Фаза 6, P6.T10/T11). */
    val donutStrokeWidth: Dp = 16.dp,
    /** Высота области под столбчатые/линейные графики статистики (Фаза 6, P6.T10/T11). */
    val chartHeight: Dp = 140.dp,
    /** Максимальная ширина контентной колонки на desktop/tablet-раскладках (Фаза 7). */
    val contentMaxWidth: Dp = 1200.dp,
    /** Высота баннера-карусели на главном экране (P7.T1) для Compact/Medium — 230dp по
     *  mobile-артбордам (сверена Фазой 14, была 220dp). Desktop (Expanded) использует
     *  [bannerHeightExpanded] 220dp по макету Claude Design (строка 757). */
    val bannerHeight: Dp = 230.dp,
    /** Высота баннера-карусели на desktop (Expanded) — 220dp по макету Claude Design (строка 757). */
    val bannerHeightExpanded: Dp = 220.dp,
    /** Высота плитки быстрого действия на главном экране (Фаза 7, P7.T1). */
    val quickActionTileHeight: Dp = 88.dp,
    /** Радиус backdrop-blur материала Liquid Glass (2026-09-11, feature/liquid-glass-tab-bar) —
     *  см. KDoc [com.aniko.ui.glass.LiquidGlass]/[com.aniko.ui.glass.LiquidGlassStyle.blurRadius]. */
    val glassBlurRadius: Dp = 24.dp,
    /** Высота светового блика по верхней кромке стекла, см. [com.aniko.ui.glass.LiquidGlassStyle.specularHeight]. */
    val glassSpecularHeight: Dp = 0.5.dp,
    /** Толщина волосяной обводки (rim) стекла, см. [com.aniko.ui.glass.LiquidGlassStyle.rimWidth]. */
    val glassRimWidth: Dp = 0.5.dp,
)

val LocalAnixDimens = staticCompositionLocalOf { AnixDimens() }
