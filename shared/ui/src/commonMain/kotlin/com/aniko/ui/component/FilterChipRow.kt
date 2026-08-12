package com.aniko.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 */
@Suppress("LongParameterList") // Бриф P6.T4: множественный выбор + wrap + leadingChip, см. KDoc
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
) {
    val dimens = AnixThemeTokens.dimens
    // singleSelection намеренно не влияет на поведение компонента, см. KDoc параметра.
    val content: @Composable () -> Unit = {
        leadingChip?.invoke()
        items.forEach { item ->
            FilterChip(
                selected = item in selected,
                onClick = { onToggle(item) },
                label = { Text(label(item)) },
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
