package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 */
@Composable
fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onClick: (T) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        items.forEach { item ->
            val selected = isSelected(item)
            Text(
                text = label(item),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier =
                    Modifier
                        .background(
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            shape = RoundedCornerShape(dimens.cornerL),
                        ).clickable { onClick(item) }
                        .padding(horizontal = dimens.spaceM, vertical = dimens.spaceS),
            )
        }
    }
}
