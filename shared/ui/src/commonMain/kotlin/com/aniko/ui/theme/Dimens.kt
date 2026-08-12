package com.aniko.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Токены отступов и радиусов скругления. Использовать вместо «магических» dp в фичах.
 *
 * Набор spacing (4/8/12/16/24/32/48) и radius (8/12/16/20/999) зафиксирован
 * `docs/REELWAVE_PLAN.md`, Фаза 2 (P2.T5).
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
    val cornerM: Dp = 12.dp,
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
)

val LocalAnixDimens = staticCompositionLocalOf { AnixDimens() }
