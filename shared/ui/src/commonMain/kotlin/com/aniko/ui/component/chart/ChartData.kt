package com.aniko.ui.component.chart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Один сегмент [DonutChart]. Абстрактная пара «подпись + значение» — компонент не знает, что
 * означают эти данные (жанры, время просмотра и т.п.), это ответственность вызывающей стороны
 * (Фаза 6, P6.T10; конкретный потребитель на статистике профиля появится в будущей фазе).
 *
 * [color] — явный override цвета сегмента; если `null`, [DonutChart] берёт цвет из своей палитры
 * по индексу сегмента.
 */
@Immutable
data class ChartSlice(
    val label: String,
    val value: Float,
    val color: Color? = null,
)

/** Один столбец [WeeklyBarChart]. */
@Immutable
data class BarEntry(
    val label: String,
    val value: Float,
)
