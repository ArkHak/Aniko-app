package com.aniko.ui.adaptive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import com.aniko.ui.theme.manropeFontFamily

/**
 * Ширина постоянного sidebar на [AnixWindowSize.Expanded]. В [com.aniko.ui.theme.AnixDimens] нет
 * подходящего токена ширины (там только spacing/radius/размер постера) — значение 232dp
 * зафиксировано desktop-мокапом Claude Design (строка 738), заведено локальной константой,
 * а не новым общим токеном, потому что сайдбар — единственный потребитель этой ширины.
 */
private val sidebarWidth = 232.dp

/**
 * Постоянная боковая панель на [AnixWindowSize.Expanded]: [header] сверху, бренд, список [items]
 * (текущий пункт выделен лавандовой плашкой + точкой-маркером), [footer] внизу.
 *
 * Desktop-мокап Claude Design (строка 738): фон панели — нейтральный оверлей поверх фона
 * приложения (light: чёрный ~4%, dark: белый 3%), НЕ тонировка `tonalElevation` M3 (та давала
 * лиловый оттенок в светлой теме); пункты — текст Manrope 13/600 без иконок, активный — плашка
 * `oklch(0.64 0.19 296 / 0.18)` (= #996DF0 @18%) + border 0.4dp, внизу — футер-слот
 * (переключатель языка, передаётся из `App.kt`).
 *
 * @param header Слот вверху панели (например, кнопки управления окном). БЕЗ собственного
 *   отступа — вызывающая сторона передаёт padding (6/8/20dp) в modifier содержимого; обёртка
 *   с повторным padding здесь удваивала отступ (см. комментарий у вызова, ревью F4).
 * @param footer Слот внизу панели (например, переключатель языка). Рендерится после
 *   контентной `Column` с весом 1, без дополнительной обёртки — вызывающая сторона сама
 *   контролирует фон.
 */
@Composable
internal fun AnixSidebar(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(SIDEBAR_ITEM_RADIUS)

    Surface(
        modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
        // Собственный нейтральный цвет, а не прозрачность поверх корневого градиента: левый
        // радиальный blob приложения просвечивал бы и давал лавандовый тон (см. KDoc).
        color = sidebarBackgroundColor(),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(SIDEBAR_OUTER_PADDING),
        ) {
            // Padding 6/8/20dp на стороне ВЫЗЫВАЮЩЕГО (App.kt передаёт его в modifier
            // AppSidebarTrafficLights) — повторная обёртка Column здесь удвоила отступ
            // (ревью F4, 2026-09-17): traffic lights оказались на 12/40 вместо 6/20.
            header()

            Text(
                text = strings.brand,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontFamily = manropeFontFamily(),
                        fontSize = SIDEBAR_BRAND_FONT_SIZE,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = SIDEBAR_BRAND_LINE_HEIGHT,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(SIDEBAR_BRAND_PADDING),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            ) {
                items.forEach { item ->
                    val selected = item.id == selectedItemId
                    SidebarItem(item = item, selected = selected, onClick = { onItemClick(item) }, shape = shape)
                }
            }

            footer()
        }
    }
}

/**
 * Нейтральный фон сайдбара из макета: light — чёрный оверлей 4.3% поверх контента #FDFDFF
 * (≈ #F2F2F4), dark — белый оверлей 3% поверх #05060A (≈ #0C0D11). Цвета рассчитаны из
 * CSS-значений desktop-артборда Claude Design (строка 738).
 */
@Composable
private fun sidebarBackgroundColor(): Color {
    val isDark =
        MaterialTheme.colorScheme.surface.luminance() < SIDEBAR_DARK_LUMINANCE_THRESHOLD
    return if (isDark) SIDEBAR_BG_DARK else SIDEBAR_BG_LIGHT
}

@Composable
private fun SidebarItem(
    item: AdaptiveNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val borderModifier =
        if (selected) {
            Modifier.border(
                BorderStroke(SIDEBAR_ITEM_BORDER_WIDTH, SIDEBAR_PILL_COLOR.copy(alpha = SIDEBAR_ITEM_BORDER_ALPHA)),
                shape,
            )
        } else {
            Modifier
        }
    val backgroundModifier =
        if (selected) {
            Modifier.background(SIDEBAR_PILL_COLOR.copy(alpha = SIDEBAR_PILL_ALPHA), shape)
        } else {
            Modifier
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(backgroundModifier)
                .then(borderModifier)
                .clickable(onClick = onClick)
                .padding(vertical = SIDEBAR_ITEM_VERTICAL_PADDING, horizontal = SIDEBAR_ITEM_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SIDEBAR_DOT_LABEL_GAP),
    ) {
        // Точка-маркер активного пункта; у неактивных — прозрачный плейсхолдер, чтобы лейбл
        // не сдвигался при переключении (в макете лейблы всех пунктов стоят на одной оси x).
        Box(
            modifier =
                Modifier
                    .size(SIDEBAR_DOT_SIZE)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        )
        Text(
            text = item.label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontFamily = manropeFontFamily(),
                    fontSize = SIDEBAR_LABEL_FONT_SIZE,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = textColor,
        )
    }
}

/** Токены сайдбара, сверенные с макетом Claude Design (desktop-артборд, строка 738). */
@Suppress("MagicNumber") // hex-литерал токена макета — исключение как у AnixPalette.
private val SIDEBAR_PILL_COLOR = Color(0xFF996DF0) // oklch(0.64 0.19 296) — макет
private const val SIDEBAR_PILL_ALPHA = 0.18f
private const val SIDEBAR_DARK_LUMINANCE_THRESHOLD = 0.5f
private val SIDEBAR_ITEM_RADIUS = 10.dp
private val SIDEBAR_ITEM_VERTICAL_PADDING = 10.dp
private val SIDEBAR_ITEM_HORIZONTAL_PADDING = 12.dp
private val SIDEBAR_ITEM_BORDER_WIDTH = 0.4.dp
private const val SIDEBAR_ITEM_BORDER_ALPHA = 0.4f
private val SIDEBAR_DOT_SIZE = 7.dp
private val SIDEBAR_DOT_LABEL_GAP = 10.dp
private val SIDEBAR_LABEL_FONT_SIZE = 13.sp
private val SIDEBAR_BRAND_FONT_SIZE = 17.sp
private val SIDEBAR_BRAND_LINE_HEIGHT = 22.sp
private val SIDEBAR_BRAND_PADDING =
    PaddingValues(
        start = 10.dp,
        end = 10.dp,
        bottom = 20.dp,
    )
private val SIDEBAR_OUTER_PADDING =
    PaddingValues(
        start = 12.dp,
        top = 16.dp,
        end = 12.dp,
        bottom = 16.dp,
    )

// Фон сайдбара: композиты оверлеев макета над базой (см. KDoc sidebarBackgroundColor).
@Suppress("MagicNumber") // hex-литералы токенов макета — исключение как у AnixPalette.
private val SIDEBAR_BG_LIGHT = Color(0xFFF2F2F4)

@Suppress("MagicNumber") // см. выше
private val SIDEBAR_BG_DARK = Color(0xFF0C0D11)
