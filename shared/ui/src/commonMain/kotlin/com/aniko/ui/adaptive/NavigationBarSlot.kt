package com.aniko.ui.adaptive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
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
import com.aniko.ui.glass.GlassIntensity
import com.aniko.ui.glass.LiquidGlassSurface
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Bottom bar на [AnixWindowSize.Compact].
 *
 * iOS-like редизайн (2026-09-11, полный HIG-паттерн, пользовательский запрос): заменён
 * Claude Design-паттерн (верхняя полоска-маркер 28×3dp + иконка/подпись одного цвета для обеих
 * веток selected/unselected) на подлинный iOS Tab Bar:
 * - НЕТ маркера/индикатора выше иконки — активность вкладки читается ТОЛЬКО через tint (iOS
 *   красит саму иконку+подпись акцентным цветом активной вкладки, серым — неактивные; ни одна
 *   версия iOS Tab Bar не рисует отдельную полоску-индикатор).
 *
 * **Liquid Glass (2026-09-11, feature/liquid-glass-tab-bar).** Контейнер — [LiquidGlassSurface]
 * (см. её KDoc/[com.aniko.ui.glass.LiquidGlass] про архитектуру двухслойного backdrop-blur),
 * не hand-rolled `Surface` — реальный преломляющий блюр контента, который скроллит позади бара,
 * вместо плоской непрозрачной заливки `MaterialTheme.colorScheme.surface`. Прежняя обводка
 * `Modifier.border(0.5dp, outlineVariant)` (рисовалась по всем 4 сторонам без формы) убрана без
 * замены отдельной линией — её роль теперь у `rim`-слоя внутри [com.aniko.ui.glass.LiquidGlass]
 * (волосяная обводка по контуру [shape]); для полноширинного `!floating` бара боковые/нижняя
 * стороны этого контура физически совпадают с краями экрана (не видны), визуально остаётся ровно
 * тот же верхний hairline, что и раньше — без дублирования линии.
 *
 * **Плавающая капсула, [floating] = `true` по умолчанию.** Мокап Apple iOS 26 tab bar — не полоса
 * во всю ширину, а плавающая пилюля: горизонтальный отступ от краёв ([AnixDimens.spaceM]),
 * отступ снизу над home-indicator, `shape = RoundedCornerShape([AnixDimens.cornerPill])`, мягкая
 * тень. [floating] — явный параметр (не убранный код) специально для отката в одну строку, если
 * живая проверка на устройстве найдёт проблему с тач-таргетами у пилюли (сжатыми боковыми зонами
 * по краям экрана) — `false` возвращает точно прежнее полноширинное поведение (`RectangleShape`,
 * контент инсетится `WindowInsets.navigationBars` изнутри, а не снаружи).
 */
@Composable
internal fun AnixNavigationBar(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
    floating: Boolean = true,
) {
    val dimens = AnixThemeTokens.dimens
    val shape: Shape = if (floating) RoundedCornerShape(dimens.cornerPill) else RectangleShape

    // floating: весь контейнер (не только контент) поднят над системным инсетом + видимый зазор
    // над home-indicator — капсула должна ПЛАВАТЬ, а не упираться фоном в самый низ экрана.
    // !floating: старое поведение без изменений — фон бара доходит до края экрана, инсет
    // применяется только к содержимому Row (см. ниже).
    val containerModifier =
        if (floating) {
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = dimens.spaceM, end = dimens.spaceM, bottom = BOTTOM_NAV_FLOATING_MARGIN)
                .shadow(elevation = BOTTOM_NAV_FLOATING_ELEVATION, shape = shape, clip = false)
        } else {
            Modifier.fillMaxWidth()
        }

    LiquidGlassSurface(
        shape = shape,
        intensity = GlassIntensity.Regular,
        modifier = containerModifier.testTag(AnixTestTags.BOTTOM_NAV_BAR),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (floating) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
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

// ---- Константы плавающей капсулы (Liquid Glass, 2026-09-11 — см. KDoc [AnixNavigationBar]).
// Горизонтальный отступ от краёв экрана — существующий токен `dimens.spaceM` (см. использование
// ниже), не отдельная константа.

/** Зазор между низом капсулы и системным home-indicator (после `windowInsetsPadding`). */
private val BOTTOM_NAV_FLOATING_MARGIN = 8.dp
private val BOTTOM_NAV_FLOATING_ELEVATION = 8.dp
