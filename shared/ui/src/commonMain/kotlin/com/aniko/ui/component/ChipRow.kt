package com.aniko.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * @param leadingIcon необязательная иконка перед подписью чипа для каждого [item]; `null` для
 *   конкретного элемента — чип без иконки (например, [com.aniko.model.VideoHost.UNKNOWN] у
 *   источника видео честно деградирует без значка, а не показывает мусор).
 */
@Suppress("LongParameterList") // Аддитивный `leadingIcon` (P8.T6) поднял счётчик до 6 —
// все параметры содержательны (данные + 3 колбэка отображения/выбора + modifier + опциональная
// иконка), дробить дальше означало бы либо терять обобщённость `T`, либо заводить data-класс
// конфигурации ради самого счётчика.
@Composable
fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ((T) -> ImageVector?)? = null,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        items.forEach { item ->
            val icon = leadingIcon?.invoke(item)
            FilterChip(
                selected = isSelected(item),
                onClick = { onClick(item) },
                label = { Text(label(item)) },
                leadingIcon =
                    icon?.let {
                        {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    },
            )
        }
    }
}
