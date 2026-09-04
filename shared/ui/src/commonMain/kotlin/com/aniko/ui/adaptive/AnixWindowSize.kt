package com.aniko.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Границы M3 (compact/medium/expanded) — единственный авторитет размера окна в приложении.
 * `ListDetailHost` (P5.T3) дополнительно читает `currentWindowAdaptiveInfo()` из
 * material3-adaptive для posture/hinge складных устройств, но границы 600/840dp ОБЯЗАНЫ
 * совпадать с этим enum — расхождение между ними считается багом.
 */
enum class AnixWindowSize {
    Compact,
    Medium,
    Expanded,
    ;

    /** true — есть место под постоянную панель детали рядом со списком (P5.T3). */
    val isTwoPane: Boolean get() = this != Compact

    companion object {
        const val MEDIUM_MIN_DP = 600
        const val EXPANDED_MIN_DP = 840

        fun fromWidthDp(widthDp: Int): AnixWindowSize =
            when {
                widthDp < MEDIUM_MIN_DP -> Compact
                widthDp < EXPANDED_MIN_DP -> Medium
                else -> Expanded
            }
    }
}

val LocalAnixWindowSize = staticCompositionLocalOf { AnixWindowSize.Compact }

/**
 * Вычисляет текущий [AnixWindowSize] из ширины окна.
 *
 * `WindowInfo.containerDpSize` (compose.ui 1.11.1, `androidx.compose.ui.platform.WindowInfo`)
 * отдаёт ширину окна уже в dp напрямую (реальная реализация `WindowInfoImpl` держит её как
 * отдельный `MutableState<DpSize>`, обновляемый платформенным кодом при ресайзе) — поэтому
 * `LocalDensity`/ручная конвертация из пикселей здесь не нужна, в отличие от более старого
 * `containerSize` (`IntSize` в пикселях).
 */
@Composable
fun rememberAnixWindowSize(): AnixWindowSize {
    val containerDpSize = LocalWindowInfo.current.containerDpSize
    val widthDp = containerDpSize.width.value.toInt()
    return AnixWindowSize.fromWidthDp(widthDp)
}

/** Нижняя граница [rememberListPanePreferredWidth] на `Expanded` — см. KDoc функции. */
private val EXPANDED_LIST_PANE_MIN = 420.dp

/** Верхняя граница [rememberListPanePreferredWidth] на `Expanded` — см. KDoc функции. */
private val EXPANDED_LIST_PANE_MAX = 560.dp

/** Доля ширины окна, которую list-панель занимает на `Expanded` до применения min/max — см. KDoc. */
private const val EXPANDED_LIST_PANE_WIDTH_FRACTION = 0.34f

/**
 * Предпочтительная ширина list-панели [androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold]
 * (P13.T6) — единый источник истины для `ListDetailHost.kt`, чтобы там не жило магическое число.
 *
 * **Проблема, которую решает эта функция**: `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`
 * (material3-adaptive 1.2.0, сверено с реальным `.kt` из sources-jar артефакта — `PaneScaffoldDirective.kt`,
 * не с декомпилированным `.class`) без явного `PaneScaffoldDirective.copy(defaultPanePreferredWidth = …)`
 * берёт библиотечный дефолт **360.dp** — причём ОДИНАКОВЫЙ для list- и detail-панели одновременно (обе
 * панели без индивидуального `PaneScaffoldScope.preferredWidth` наследуют один и тот же
 * `directive.defaultPanePreferredWidth`). Внутри `ThreePaneScaffold.measureAndPlacePartitionsInBounds`
 * остаток ширины окна сверх суммы обеих заявок (`allocatableWidth - totalPreferredWidth`) целиком уходит
 * панели с наивысшим приоритетом — это Detail (Primary-роль), а не List (Secondary-роль). Поэтому
 * list-панель залипает ровно на 360dp независимо от того, насколько растянуто Desktop-окно, а detail-
 * панель забирает всю оставшуюся ширину (это подтверждено live-прогоном — см. Desktop pass 2026-09-03 в
 * памяти проекта: "Title Detail: unexpectedly good — once a title is selected, the detail pane gets all
 * remaining width"). Тем временем [rememberAnixWindowSize] — единственный авторитет размера окна в
 * приложении — классифицирует то же самое окно как `Expanded` от его ПОЛНОЙ ширины, и экраны внутри
 * list-панели (Home/Catalog/Schedule) рендерят Expanded-раскладку, рассчитанную на куда больше 360dp
 * (постоянный сайдбар фильтров + сетка) — отсюда переносы текста по буквам и сжатые сетки на Desktop.
 *
 * **Формула** (P13.T6, выбрана по здравому смыслу — не выведена из мокапа напрямую: desktop-вариант
 * мокапа, `Reelwave Prototype.dc.html`/секция `isDesktop`, вообще не использует list-detail-сплит для
 * Catalog/Home на десктопе — постоянный сайдбар навигации + контент на всю ширину без отдельной
 * "списочной" колонки; выделенная desktop-раскладка, которая по-настоящему соответствовала бы мокапу —
 * отдельная задача P13.T7, поверх этого фикса): `~34%` ширины окна, зажатое в
 * [EXPANDED_LIST_PANE_MIN]..[EXPANDED_LIST_PANE_MAX].
 * - Верхняя граница (560.dp) не даёт list-панели доминировать над деталью на сверхширoких мониторах —
 *   34% с потолком гарантированно остаётся далеко от 50%, которые прямо запрещены задачей.
 * - Нижняя граница (420.dp) обеспечивает заметный прирост уже на минимальной ширине `Expanded`
 *   (`AnixWindowSize.EXPANDED_MIN_DP` = 840dp), где чистая доля (`840 * 0.34 ≈ 286dp`) была бы даже
 *   меньше библиотечного дефолта. Ровно на границе 840dp список (list) + деталь (detail) вместе просят
 *   `2 * 420 + 24 (spacer) = 864dp` — на 24dp больше окна, поэтому там на волосок применяется
 *   штатный fallback самой библиотеки (`allocatableWidth < totalPreferredWidth` в
 *   `measureAndPlacePartitionsInBounds`), который пропорционально ужимает ОБЕ заявки до факта. Итог
 *   на 840dp — около 408dp, что всё ещё заметно больше 360dp; уже при ширине окна свыше ~864dp
 *   list-панель получает полные 420dp без всякого сжатия. Это не баг, а штатное поведение самого
 *   material3-adaptive для тесных окон — было верно и для дефолта 360dp на границе Medium/Expanded.
 *
 * Возвращает `null` на `Compact`/`Medium` (переопределять не нужно): на `Compact` в списке "expanded"-
 * панелей всегда ровно одна (single-pane), и весь остаток ширины алгоритм отдаёт именно ей независимо от
 * заявленного `preferredWidth` — значение здесь физически не влияет на итоговую раскладку. `Medium` не
 * был предметом бага (см. Desktop pass 2026-09-03 в памяти проекта — жалоба была именно про широкое
 * Desktop-окно, то есть `Expanded`) — библиотечный дефолт 360dp там ведёт себя предсказуемо, трогать не
 * нужно, чтобы не расширять скоуп задачи P13.T6.
 */
@Composable
fun rememberListPanePreferredWidth(): Dp? {
    if (rememberAnixWindowSize() != AnixWindowSize.Expanded) return null
    val windowWidth = LocalWindowInfo.current.containerDpSize.width
    return (windowWidth * EXPANDED_LIST_PANE_WIDTH_FRACTION)
        .coerceIn(EXPANDED_LIST_PANE_MIN, EXPANDED_LIST_PANE_MAX)
}
