package com.aniko.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Ширина постоянного sidebar на [AnixWindowSize.Expanded]. В [com.aniko.ui.theme.AnixDimens] нет
 * подходящего токена ширины (там только spacing/radius/размер постера) — плановое значение M3
 * для постоянного navigation drawer (~240dp), заведено локальной константой, а не новым
 * общим токеном, потому что сайдбар — единственный потребитель этой ширины.
 */
private val sidebarWidth = 240.dp

/**
 * Постоянная боковая панель на [AnixWindowSize.Expanded]: [header] сверху, список [items]
 * (текущий пункт выделен фоном/цветом темы), [footer] снизу — туда composeApp сажает
 * `AnixLanguagePicker`.
 */
@Composable
internal fun AnixSidebar(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Surface(
        modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
        tonalElevation = dimens.spaceXs,
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(dimens.spaceM)) {
            header()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            ) {
                items.forEach { item ->
                    val selected = item.id == selectedItemId
                    SidebarItem(item = item, selected = selected, onClick = { onItemClick(item) })
                }
            }

            footer()
        }
    }
}

@Composable
private fun SidebarItem(
    item: AdaptiveNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = backgroundColor, shape = RoundedCornerShape(dimens.cornerM))
                .clickable(onClick = onClick)
                .padding(horizontal = dimens.spaceM, vertical = dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = null,
            tint = contentColor,
        )
        Text(text = item.label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
