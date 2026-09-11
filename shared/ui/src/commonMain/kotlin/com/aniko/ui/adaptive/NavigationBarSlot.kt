package com.aniko.ui.adaptive

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.testing.AnixTestTags

/**
 * Bottom bar на [AnixWindowSize.Compact].
 *
 * iOS-like редизайн (2026-09-11, полный HIG-паттерн, пользовательский запрос): заменён
 * Claude Design-паттерн (верхняя полоска-маркер 28×3dp + иконка/подпись одного цвета для обеих
 * веток selected/unselected) на подлинный iOS Tab Bar:
 * - НЕТ маркера/индикатора выше иконки — активность вкладки читается ТОЛЬКО через tint (iOS
 *   красит саму иконку+подпись акцентным цветом активной вкладки, серым — неактивные; ни одна
 *   версия iOS Tab Bar не рисует отдельную полоску-индикатор).
 * - Контейнер отделён от контента тонкой волосяной линией сверху (`hairline`, `outlineVariant`,
 *   1dp), а не `tonalElevation`-тенью M3 — iOS Tab Bar использует именно hairline-разделитель.
 * Контейнер — hand-rolled `Surface` + `Row` (2026-09-10, ревью замечание #2), НЕ M3
 * `NavigationBar`: M3-компонент навязывает `defaultMinSize(minHeight = 80.dp)` контентному `Row`
 * (M3 "Tall" navigation bar token) и центрирует наш 64dp-контент внутри — визуально это давало
 * лишние 8dp сверху и 8dp снизу ДО настоящего инсета системной панели (выглядело как «бар не
 * прижат к низу экрана»). Insets по-прежнему настоящие (`WindowInsets.navigationBars`), просто
 * без чужого минимума высоты — контентная высота ровно [BOTTOM_NAV_BAR_HEIGHT], как в макете.
 */
@Composable
internal fun AnixNavigationBar(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier =
            Modifier
                .border(
                    width = BOTTOM_NAV_HAIRLINE_WIDTH,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ).testTag(AnixTestTags.BOTTOM_NAV_BAR),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(BOTTOM_NAV_BAR_HEIGHT),
        ) {
            items.forEach { item ->
                val selected = item.id == selectedItemId
                AnixBottomNavItem(
                    item = item,
                    selected = selected,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AnixBottomNavItem(
    item: AdaptiveNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS Tab Bar tint: активная вкладка — акцентный цвет (`primary`), неактивная — приглушённый
    // серый (`onSurfaceVariant`, iOS `secondaryLabel`-аналог). Единственный сигнал активности —
    // цвет (+ filled-иконка), без отдельного индикатора (см. KDoc [AnixNavigationBar]).
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clickable(onClick = onClick)
                // Подтверждено на устройстве (Фаза 11, T9, дамп accessibility-дерева): M3
                // `NavigationBarItem` не сливает иконку и подпись в один озвучиваемый узел —
                // имя/роль/состояние задаются напрямую на кликабельном узле.
                .testTag(AnixTestTags.bottomNavItem(item.id))
                .clearAndSetSemantics {
                    contentDescription = item.label
                    role = Role.Tab
                    this.selected = selected
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnixIcon(
            name = item.icon,
            contentDescription = null,
            filled = selected,
            tint = tint,
            modifier = Modifier.size(BOTTOM_NAV_ICON_SIZE),
        )
        Text(
            text = item.label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = BOTTOM_NAV_LABEL_SIZE,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Константы bottom bar (iOS-like редизайн 2026-09-11 — см. KDoc [AnixNavigationBar]).
private val BOTTOM_NAV_ICON_SIZE = 25.dp
private val BOTTOM_NAV_LABEL_SIZE = 10.sp
private val BOTTOM_NAV_BAR_HEIGHT = 64.dp
private val BOTTOM_NAV_HAIRLINE_WIDTH = 0.5.dp
