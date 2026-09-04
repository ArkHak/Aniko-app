package com.aniko.ui.adaptive

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.testing.AnixTestTags

/** Bottom bar на [AnixWindowSize.Compact] — обёртка над M3 `NavigationBar`. */
@Composable
internal fun AnixNavigationBar(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
) {
    NavigationBar(modifier = Modifier.testTag(AnixTestTags.BOTTOM_NAV_BAR)) {
        items.forEach { item ->
            val selected = item.id == selectedItemId
            NavigationBarItem(
                selected = selected,
                onClick = { onItemClick(item) },
                icon = {
                    AnixIcon(
                        name = item.icon,
                        contentDescription = item.label,
                        filled = selected,
                    )
                },
                label = {
                    // При 5 вкладках самый длинный лейбл ("Расписание", RU) переносился на 2
                    // строки на дефолтном `labelMedium` (12sp + letterSpacing 0.5) — узкой ширины
                    // одного таба не хватало. `labelSmall` (11sp, без letterSpacing) + явный
                    // `maxLines=1`/ellipsis как страховка от переноса на ещё более узких экранах.
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // Подтверждено на устройстве (Фаза 11, T9, дамп accessibility-дерева): M3
                // `NavigationBarItem` не сливает иконку и подпись в один озвучиваемый узел —
                // TalkBack фокусировал кликабельный элемент без имени, подпись оставалась
                // отдельным, недостижимым для навигации узлом. Обычный `mergeDescendants = true`
                // здесь не сработал (проверено на эмуляторе — `modifier` этого composable
                // навешивается не на тот узел, чьи потомки нужно слить) — `clearAndSetSemantics`
                // задаёт имя/роль/состояние напрямую на кликабельном узле, без надежды на merge.
                modifier =
                    Modifier
                        .testTag(AnixTestTags.bottomNavItem(item.id))
                        .clearAndSetSemantics {
                            contentDescription = item.label
                            role = Role.Tab
                            this.selected = selected
                        },
            )
        }
    }
}
