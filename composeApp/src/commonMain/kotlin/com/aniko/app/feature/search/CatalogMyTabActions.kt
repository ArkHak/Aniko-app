package com.aniko.app.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aniko.model.CatalogFilter
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Действия «Моей вкладки» каталога (P16.T2): сохранить текущий набор фильтров, применить
 * сохранённый, поделиться ссылкой на набор.
 *
 * Один компонент на обе раскладки экрана (Compact — [CatalogInlineFilterChips], Expanded —
 * [CatalogFilterPanel]): сущность одна и та же, а разные копии разошлись бы по поведению — тот же
 * аргумент, по которому общий у плеера сделан один `QualityPicker`/`AudioPickerOverlay`.
 *
 * «Применить» показывается только когда вкладка сохранена, «забыть» — рядом с ней же: кнопка
 * «применить» без сохранённой вкладки была бы кнопкой-обманкой (тот же принцип честного UI, что у
 * чипа Audio с одной озвучкой).
 */
@Suppress("LongParameterList") // myTab + 4 колбэка действий (P16.T2) + modifier — одна связная
// группа «Моей вкладки», см. KDoc класса про то, почему это один компонент, а не два.
@Composable
fun CatalogMyTabActions(
    myTab: CatalogFilter?,
    onApplyMyTab: () -> Unit,
    onSaveMyTab: () -> Unit,
    onClearMyTab: () -> Unit,
    onShareFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    // Горизонтальный скролл, а не перенос: на узкой Compact-ширине четыре подписи не помещаются,
    // а перенос в две строки сдвигал бы сетку результатов на каждом тапе (тот же приём, что у
    // ChipRow — «ряд без переноса, скроллится»).
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (myTab != null) {
            CatalogMyTabButton(
                icon = "bookmark",
                label = strings.catalogMyTabApply,
                onClick = onApplyMyTab,
            )
            CatalogMyTabButton(
                icon = "close",
                label = strings.catalogMyTabClear,
                onClick = onClearMyTab,
            )
        }
        CatalogMyTabButton(
            icon = "check_circle",
            label = strings.catalogMyTabSave,
            onClick = onSaveMyTab,
        )
        CatalogMyTabButton(
            icon = "share",
            label = strings.catalogFilterShare,
            onClick = onShareFilter,
        )
    }
}

@Composable
private fun CatalogMyTabButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    // `clearAndSetSemantics` не нужен: подпись — обычный `Text` внутри кнопки, и M3 TextButton
    // сливает её в свой озвучиваемый узел (в отличие от FilterChip, см. KDoc `ChipRow`).
    TextButton(onClick = onClick) {
        AnixIcon(
            name = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimens.badgeIconSize),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(start = dimens.spaceXs),
        )
    }
}
