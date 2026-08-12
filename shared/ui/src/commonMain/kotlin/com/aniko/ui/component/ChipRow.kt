package com.aniko.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * из коробки. Публичная сигнатура не изменилась.
 */
@Composable
fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = isSelected(item),
                onClick = { onClick(item) },
                label = { Text(label(item)) },
            )
        }
    }
}
