package com.aniko.ui.adaptive

import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.aniko.ui.component.AnixIcon

/** Nav rail на [AnixWindowSize.Medium] — обёртка над M3 `NavigationRail`. */
@Composable
internal fun AnixNavigationRail(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
) {
    NavigationRail {
        items.forEach { item ->
            val selected = item.id == selectedItemId
            NavigationRailItem(
                selected = selected,
                onClick = { onItemClick(item) },
                icon = {
                    AnixIcon(
                        name = item.icon,
                        contentDescription = item.label,
                        filled = selected,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}
