package com.aniko.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Сверен с мобильным артбордом макета Claude Design (2026-09-08): активная вкладка — тонкий
 * верхний маркер-полоска (28×3dp, `primary`) + filled-иконка; плашки-подложки M3
 * `NavigationBarItem` и цветового выделения (выбранный = тот же цвет, что невыбранный) в макете
 * нет. Рисуем собственные элементы вместо M3-компонента (M3 сам навешивает indicator-пилюлю и
 * перекрашивает selected-иконку).
 *
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
        modifier = Modifier.testTag(AnixTestTags.BOTTOM_NAV_BAR),
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
    val colors = MaterialTheme.colorScheme
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
    ) {
        // Тонкий верхний маркер активной вкладки (макет: 28×3dp полоса цвета primary).
        Box(
            modifier =
                Modifier
                    .width(BOTTOM_NAV_MARKER_WIDTH)
                    .height(BOTTOM_NAV_MARKER_HEIGHT)
                    .background(
                        if (selected) {
                            colors.primary
                        } else {
                            Color.Transparent
                        },
                        RoundedCornerShape(BOTTOM_NAV_MARKER_RADIUS),
                    ),
        )
        // Иконка над подписью, вертикально по центру оставшейся высоты; цвет не меняется при
        // выборе (в макете и активная, и неактивные иконки/подписи одного цвета) — отличие
        // только в маркере и filled-иконке.
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnixIcon(
                name = item.icon,
                contentDescription = null,
                filled = selected,
                modifier = Modifier.size(BOTTOM_NAV_ICON_SIZE),
            )
            Text(
                text = item.label,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = BOTTOM_NAV_LABEL_SIZE,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- Константы bottom bar, сверенные с макетом Claude Design (phone-артборд, 2026-09-08).
private val BOTTOM_NAV_MARKER_WIDTH = 28.dp
private val BOTTOM_NAV_MARKER_HEIGHT = 3.dp
private val BOTTOM_NAV_MARKER_RADIUS = 2.dp
private val BOTTOM_NAV_ICON_SIZE = 23.dp
private val BOTTOM_NAV_LABEL_SIZE = 10.5.sp
private val BOTTOM_NAV_BAR_HEIGHT = 64.dp
