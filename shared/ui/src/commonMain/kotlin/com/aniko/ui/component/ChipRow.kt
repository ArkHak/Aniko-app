package com.aniko.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Горизонтальный ряд текстовых чипов для выбора одного варианта из [items] — единственный вариант
 * может быть выбран или (в некоторых сценариях-вызовах, см. `ReleaseDetailsScreen`) не выбран
 * вовсе. Изначально жил приватно в `ReleaseDetailsScreen.kt` (Фаза 6, статус списка/тип
 * озвучки/источник), вынесен в `shared/ui`, чтобы Фаза 7 (privacy-настройки профиля) переиспользовала
 * тот же компонент вместо повторного изобретения.
 *
 * Внутренняя реализация (Фаза 6, P6.T4) опирается на M3 [FilterChip] вместо самописных
 * `Text`+`background`+`clickable` — даёт верную `selected`/`Role.Checkbox`-семантику и тач-таргет
 * из коробки. Публичная сигнатура дополнена [leadingIcon] аддитивно (P8.T6, источники видео
 * плеера с иконкой хоста) — параметр опциональный с дефолтом `null`, существующие вызовы не
 * тронуты.
 *
 * @param leadingIcon необязательное имя иконки Material Symbols (для [AnixIcon]) перед подписью
 *   чипа для каждого [item]; `null` для конкретного элемента — чип без иконки (например,
 *   [com.aniko.model.VideoHost.UNKNOWN] у источника видео честно деградирует без значка, а не
 *   показывает мусор).
 * @param labelFontFamily необязательное переопределение шрифта подписи чипа (по умолчанию `null` —
 *   стиль наследуется от M3 [FilterChip], как и раньше). Нужен точечно [AnixLanguagePicker]
 *   (макет Claude Design рисует "EN"/"RU" шрифтом JetBrains Mono) — остальные вызовы `ChipRow`
 *   (дни расписания, жанры, статусы) его не передают и не меняются.
 * @param selectedColor необязательный акцентный цвет активного чипа (по умолчанию `null` —
 *   прежнее поведение, дефолтный цвет M3 [FilterChip], остальные вызовы `ChipRow` не меняются).
 *   Сверка Compact-раскладки с макетом Claude Design (`catalogViewTabs`/`listsGroups`) потребовала
 *   вместо дефолтной заливки M3 фон `selectedColor.copy(alpha = 0.2f)` + бордер
 *   `selectedColor.copy(alpha = 0.5f)` + radius 10dp — передаётся точечно из Catalog/Library.
 */
@Suppress("LongParameterList", "LongMethod")
// LongParameterList: Аддитивные `leadingIcon` (P8.T6), `labelFontFamily` (Track A+B финал) и
// `selectedColor` (Track A, сверка Compact-раскладки) подняли счётчик до 8 — все параметры
// содержательны (данные + 3 колбэка отображения/выбора + modifier + 3 опциональных
// переопределения отображения одной подписи/цвета), дробить дальше означало бы либо терять
// обобщённость `T`, либо заводить data-класс конфигурации ради самого счётчика.
// LongMethod: `selectedColor` (Track A) добавил ветвление colors/border/shape на активный чип —
// тело осталось линейным (один `FilterChip` на элемент), разбиение добавило бы косвенность ради
// счётчика строк, тот же аргумент, что уже применён в `ProgressRow.kt`.
@Composable
fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ((T) -> String?)? = null,
    labelFontFamily: FontFamily? = null,
    selectedColor: Color? = null,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        items.forEach { item ->
            val icon = leadingIcon?.invoke(item)
            val selected = isSelected(item)
            val chipLabel = label(item)
            FilterChip(
                selected = selected,
                onClick = { onClick(item) },
                label = {
                    Text(
                        text = chipLabel,
                        fontFamily = labelFontFamily,
                        fontWeight = if (selectedColor != null) FontWeight.Bold else null,
                        fontSize = if (selectedColor != null) CHIP_LABEL_FONT_SIZE else TextUnit.Unspecified,
                    )
                },
                leadingIcon =
                    icon?.let {
                        {
                            AnixIcon(
                                name = it,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
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
                            selected = selected,
                            selectedBorderColor = selectedColor.copy(alpha = SELECTED_BORDER_ALPHA),
                            borderWidth = CHIP_BORDER_WIDTH,
                            selectedBorderWidth = CHIP_BORDER_WIDTH,
                        )
                    } else {
                        FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
                    },
                shape =
                    if (selectedColor != null) {
                        RoundedCornerShape(CHIP_CORNER_RADIUS)
                    } else {
                        FilterChipDefaults.shape
                    },
                // Подтверждено на устройстве (Фаза 11, T9): M3 FilterChip не сливает подпись в
                // свой озвучиваемый узел (тот же паттерн, что и NavigationBarItem/AnixPoster —
                // см. их KDoc). Роль/selected переустановлены вручную — clearAndSetSemantics
                // стирает то, что FilterChip выставляет сам по себе.
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = chipLabel
                        role = Role.Checkbox
                        this.selected = selected
                    },
            )
        }
    }
}

// Track A (сверка Compact-раскладки, 2026-09-04): точные значения макета для активного чипа с
// [selectedColor] — radius 10dp (не токен `AnixDimens`, единственный потребитель — чипы с
// нестандартным акцентным цветом), бордер 1dp, подпись 13sp.
private val CHIP_CORNER_RADIUS = 10.dp
private val CHIP_BORDER_WIDTH = 1.dp
private val CHIP_LABEL_FONT_SIZE = 13.sp
private const val SELECTED_CONTAINER_ALPHA = 0.2f
private const val SELECTED_BORDER_ALPHA = 0.5f
