package com.aniko.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Ряд чипов с множественным выбором ([selected] — `Set<T>`, а не единственное значение, как в
 * [ChipRow]). Часть Фазы 6 (P6.T4) — используется для фильтров каталога/поиска, где допустимо
 * выбрать сразу несколько значений (жанры, годы и т.п.).
 *
 * @param wrap `false` (по умолчанию) — один ряд с горизонтальным скроллом (тот же паттерн, что в
 *   [ChipRow]); `true` — перенос по нескольким строкам через [FlowRow], когда высота ряда не
 *   ограничена (например, разворачиваемая панель фильтров).
 * @param leadingChip необязательный чип перед [items] — например «Все»/«Сбросить»
 *   ([com.aniko.ui.i18n.Strings.filterChipAll]/[com.aniko.ui.i18n.Strings.filterChipReset]).
 * @param singleSelection семантический флаг для вызывающей стороны: сам компонент не ограничивает
 *   выбор — множественность обеспечивается типом [selected] (`Set<T>`). Флаг ничего не делает
 *   внутри компонента и предназначен для потенциальной разметки/обработки снаружи (см. бриф P6.T4).
 * @param selectedColor необязательный акцентный цвет активного чипа (по умолчанию `null` —
 *   прежнее поведение, дефолтный цвет M3 [FilterChip]). Сверка Compact-раскладки Catalog с
 *   макетом Claude Design (Трек A) развела статус-чипы (`secondary`) и жанр-чипы (`primary`) на
 *   разные акценты — раньше оба ряда рендерились одинаково, что было расхождением. Тот же паттерн
 *   фона/бордера/radius, что и [ChipRow.selectedColor], только radius — [cornerPill] (пилюля), а
 *   не 10dp: копия недопустима (единый компонент используется и с прямоугольными чипами
 *   [ChipRow]), см. параметры ниже.
 */
@Suppress("LongParameterList", "LongMethod")
// LongParameterList: Бриф P6.T4 (множественный выбор + wrap + leadingChip) + selectedColor
// (Трек A, сверка Compact-раскладки), см. KDoc.
// LongMethod: selectedColor добавил то же ветвление colors/border/shape, что и в ChipRow.kt —
// см. её KDoc-обоснование, тот же аргумент (линейное тело, разбиение добавило бы косвенность).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> AnixFilterChipRow(
    items: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UnusedParameter") singleSelection: Boolean = false,
    leadingChip: (@Composable () -> Unit)? = null,
    wrap: Boolean = false,
    selectedColor: Color? = null,
) {
    val dimens = AnixThemeTokens.dimens
    // singleSelection намеренно не влияет на поведение компонента, см. KDoc параметра.
    val content: @Composable () -> Unit = {
        leadingChip?.invoke()
        items.forEach { item ->
            val isSelected = item in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(item) },
                label = {
                    Text(
                        text = label(item),
                        fontWeight = if (selectedColor != null) FontWeight.SemiBold else null,
                        fontSize = if (selectedColor != null) CHIP_LABEL_FONT_SIZE else TextUnit.Unspecified,
                    )
                },
                colors =
                    if (selectedColor != null) {
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = selectedColor.copy(alpha = SELECTED_CONTAINER_ALPHA),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        FilterChipDefaults.filterChipColors()
                    },
                border =
                    if (selectedColor != null) {
                        FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            selectedBorderColor = selectedColor.copy(alpha = SELECTED_BORDER_ALPHA),
                            borderWidth = CHIP_BORDER_WIDTH,
                            selectedBorderWidth = CHIP_BORDER_WIDTH,
                        )
                    } else {
                        FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected)
                    },
                shape =
                    if (selectedColor != null) {
                        RoundedCornerShape(dimens.cornerPill)
                    } else {
                        FilterChipDefaults.shape
                    },
            )
        }
    }

    if (wrap) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
            content = { content() },
        )
    } else {
        Row(
            modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            content = { content() },
        )
    }
}

// Track A (сверка Compact-раскладки, 2026-09-04): точные значения макета для активного чипа с
// [selectedColor] — бордер 1dp, подпись 12sp (см. KDoc параметра).
private val CHIP_BORDER_WIDTH = 1.dp
private val CHIP_LABEL_FONT_SIZE = 12.sp
private const val SELECTED_CONTAINER_ALPHA = 0.22f
private const val SELECTED_BORDER_ALPHA = 0.55f
