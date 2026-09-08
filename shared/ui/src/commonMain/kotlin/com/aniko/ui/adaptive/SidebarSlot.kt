package com.aniko.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * (текущий пункт выделен лавандовой плашкой + точкой-маркером). Футер-слот был удалён
 * (2026-09-08): в него composeApp сажал `AnixLanguagePicker`, который обрезался на малой
 * высоте и дублировал переключатель языка из Settings — язык теперь только в Settings.
 *
 * Внешний вид сверен с макетом Claude Design (desktop-артборд, 2026-09-08): фон панели —
 * нейтральный оверлей поверх фона приложения (light: чёрный ~4%, dark: белый 3%), НЕ тонировка
 * `tonalElevation` M3 (та давала лиловый оттенок в светлой теме); пункты — текст Manrope
 * 13/600 без иконок, активный — плашка `oklch(0.64 0.19 296 / 0.18)` (= #996DF0 @18%,
 * светло-лавандовая в light, приглушённо-фиолетовая в dark) + точка-маркер 7dp цветом
 * [MaterialTheme.colorScheme.primary] (#7743CC light / #996DF0 dark — ровно как в макете).
 */
@Composable
internal fun AnixSidebar(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Surface(
        modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
        // Собственный нейтральный цвет, а не прозрачность поверх корневого градиента: левый
        // радиальный blob приложения просвечивал бы и давал лавандовый тон (см. KDoc).
        color = sidebarBackgroundColor(),
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
        }
    }
}

/**
 * Нейтральный фон сайдбара из макета: light — чёрный оверлей 4.3% поверх контента #FDFDFF
 * (≈ #F2F2F4), dark — белый оверлей 3% поверх #05060A (≈ #0C0D11). Цвета рассчитаны из
 * CSS-значений desktop-артборда Claude Design (2026-09-08).
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
) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(SIDEBAR_ITEM_RADIUS)
    val textColor = MaterialTheme.colorScheme.onSurface
    val pillModifier =
        if (selected) {
            Modifier.background(SIDEBAR_PILL_COLOR.copy(alpha = SIDEBAR_PILL_ALPHA), shape)
        } else {
            Modifier
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SIDEBAR_ITEM_HEIGHT)
                .then(pillModifier)
                .clickable(onClick = onClick)
                .padding(start = dimens.spaceM),
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
                    fontSize = SIDEBAR_LABEL_FONT_SIZE,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = textColor,
        )
    }
}

/** Токены сайдбара, сверенные с макетом Claude Design (desktop-артборд, 2026-09-08) —
 * значения локальны (единственный потребитель — сайдбар), как и [sidebarWidth]. */
@Suppress("MagicNumber") // hex-литерал токена макета — исключение как у AnixPalette (см. KDoc Color.kt)
private val SIDEBAR_PILL_COLOR = Color(0xFF996DF0) // oklch(0.64 0.19 296) — макет
private const val SIDEBAR_PILL_ALPHA = 0.18f
private const val SIDEBAR_DARK_LUMINANCE_THRESHOLD = 0.5f
private val SIDEBAR_ITEM_HEIGHT = 38.dp
private val SIDEBAR_ITEM_RADIUS = 10.dp
private val SIDEBAR_DOT_SIZE = 7.dp
private val SIDEBAR_DOT_LABEL_GAP = 10.dp
private val SIDEBAR_LABEL_FONT_SIZE = 13.sp

// Фон сайдбара: композиты оверлеев макета над базой (см. KDoc sidebarBackgroundColor).
@Suppress("MagicNumber") // hex-литералы токенов макета — исключение как у AnixPalette (см. KDoc Color.kt)
private val SIDEBAR_BG_LIGHT = Color(0xFFF2F2F4)

@Suppress("MagicNumber") // см. выше
private val SIDEBAR_BG_DARK = Color(0xFF0C0D11)
