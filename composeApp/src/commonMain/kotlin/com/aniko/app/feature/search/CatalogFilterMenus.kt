package com.aniko.app.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.launch

/**
 * Меню чипа-фильтра. [FilterMenuPresentation.Popup] (Medium/Expanded) — выпадающее меню у чипа
 * (`DropdownMenu`: закрывается кликом вне и Esc, содержимое скроллится, не выходит за окно);
 * [FilterMenuPresentation.BottomSheet] (Compact) — `ModalBottomSheet` с тем же содержимым и
 * заголовком.
 *
 * @param content содержимое меню; получает `close` — закрыть меню (для одиночного выбора после
 *   тапа). У множественного выбора `close` не вызывается — меню остаётся открытым.
 * @param onResetSection «Сбросить» в заголовке шторки: `null` — в этом фильтре нечего сбрасывать.
 *   В выпадающем меню заголовка нет: быстрый сброс там — ✕ на самом чипе.
 */
@Suppress("LongParameterList") // Состояние + представление + тексты + содержимое: описывает один хост меню.
@Composable
internal fun FilterMenuHost(
    open: Boolean,
    onClose: () -> Unit,
    presentation: FilterMenuPresentation,
    title: String,
    menuTag: String,
    onResetSection: (() -> Unit)?,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    when (presentation) {
        FilterMenuPresentation.Popup -> FilterPopupMenu(open, onClose, menuTag, content)
        FilterMenuPresentation.BottomSheet ->
            if (open) FilterBottomSheet(title, onClose, menuTag, onResetSection, content)
    }
}

@Composable
private fun FilterPopupMenu(
    open: Boolean,
    onClose: () -> Unit,
    menuTag: String,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = open,
        onDismissRequest = onClose,
        shape = RoundedCornerShape(dimens.corner16),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = POPUP_SHADOW_ELEVATION,
        border = BorderStroke(POPUP_BORDER_WIDTH, colors.outline),
    ) {
        // Ширина задана явно: DropdownMenu меряет содержимое по IntrinsicSize.Max, а у FlowRow это
        // «все чипы в одну строку» — без фиксированной ширины меню растянулось бы за пределы окна.
        Box(
            modifier =
                Modifier
                    .width(POPUP_WIDTH)
                    .padding(horizontal = dimens.spaceM, vertical = dimens.spaceS)
                    .testTag(menuTag),
        ) {
            content(onClose)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    title: String,
    onClose: () -> Unit,
    menuTag: String,
    onResetSection: (() -> Unit)?,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Программное закрытие (после выбора статуса): сначала анимация ухода шторки, потом снятие
    // флага «открыто» — иначе шторка исчезла бы рывком.
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = dimens.spaceM)
                    .padding(bottom = dimens.spaceM)
                    .testTag(menuTag),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onResetSection != null) {
                    TextButton(
                        onClick = onResetSection,
                        // primaryText — акцент как цвет ТЕКСТА, проходит AA 4.5:1 на `surface`.
                        colors = ButtonDefaults.textButtonColors(contentColor = AnixThemeTokens.colors.primaryText),
                    ) {
                        Text(strings.filterChipReset)
                    }
                }
            }
            content(close)
        }
    }
}

/** Контейнер чипов внутри меню/шторки: строки с переносом по ширине (для 18 жанров — 3–4 ряда). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OptionChipFlow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AnixThemeTokens.dimens.spaceS),
        content = content,
    )
}

/**
 * Чип-вариант внутри меню («Онгоинг», «экшен»): выбранный — тинт `primary` + рамка + значок ✓ (не
 * только цвет: состояние читается и без различения оттенков). Семантика — `Role.Checkbox` +
 * `selected`, как у остальных чипов проекта.
 */
@Composable
internal fun OptionChip(
    label: String,
    selected: Boolean,
    onTap: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = MaterialTheme.colorScheme
    ChipSurface(active = selected, open = false) {
        PillZone(
            onTap = onTap,
            overlayShape = RoundedCornerShape(dimens.cornerPill),
            semantics = {
                contentDescription = label
                role = Role.Checkbox
                this.selected = selected
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = CHIP_HORIZONTAL_PADDING),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHIP_CONTENT_GAP),
            ) {
                if (selected) {
                    AnixIcon(
                        name = "check_circle",
                        contentDescription = null,
                        modifier = Modifier.size(CHECK_ICON_SIZE),
                        filled = true,
                        tint = colors.primary,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val CHECK_ICON_SIZE = 16.dp
private val POPUP_WIDTH = 336.dp
private val POPUP_SHADOW_ELEVATION = 12.dp
private val POPUP_BORDER_WIDTH = 1.dp
