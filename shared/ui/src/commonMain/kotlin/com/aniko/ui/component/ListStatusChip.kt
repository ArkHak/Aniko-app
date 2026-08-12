@file:Suppress("MatchingDeclarationName")
// Файл назван по главному экспорту (fun ListStatusChip), а не по вспомогательному enum
// ListStatusChipStyle — так и задумано брифом Фазы 6 (P6.T2): один публичный компонент.

package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.aniko.model.ListStatus
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.i18n.shortLabel
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Раскладка [ListStatusChip]:
 * - [Compact] — кружок-буква (диаметр [com.aniko.ui.theme.AnixDimens.badgeSize]), как раньше было
 *   в приватной `ListStatusBadge` внутри `ReleaseCard.kt` (Фаза 6, P6.T3).
 * - [Full] — полноразмерный чип с полным текстом статуса (`ListStatus.displayName`), тот же
 *   набор ключей, что уже используется в `LibraryScreen.kt`.
 */
enum class ListStatusChipStyle {
    Compact,
    Full,
}

/** Индикатор статуса релиза в списке пользователя. См. [ListStatusChipStyle]. */
@Composable
fun ListStatusChip(
    status: ListStatus,
    modifier: Modifier = Modifier,
    style: ListStatusChipStyle = ListStatusChipStyle.Full,
) {
    when (style) {
        ListStatusChipStyle.Compact -> CompactListStatusChip(status, modifier)
        ListStatusChipStyle.Full -> FullListStatusChip(status, modifier)
    }
}

@Composable
private fun CompactListStatusChip(
    status: ListStatus,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    // defaultMinSize, а не size(): на увеличенном масштабе шрифта (P6.T12) буква внутри должна
    // иметь право раздвинуть кружок, а не обрезаться/вылезти за его границы — фиксированный
    // size() этого не допускал. labelSmall без ручного override fontSize — так текст реагирует
    // на системный масштаб шрифта, как и остальной UI.
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = dimens.badgeSize, minHeight = dimens.badgeSize)
                .clip(CircleShape)
                .background(status.badgeColor())
                .padding(dimens.spaceXs / 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.shortLabel(LocalStrings.current),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FullListStatusChip(
    status: ListStatus,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(dimens.cornerPill))
                .background(status.badgeColor())
                .padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs),
    ) {
        Text(
            text = status.displayName(LocalStrings.current),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun ListStatus.badgeColor(): Color =
    when (this) {
        ListStatus.WATCHING -> MaterialTheme.colorScheme.primary
        ListStatus.PLANNED -> MaterialTheme.colorScheme.secondary
        ListStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        ListStatus.ON_HOLD -> MaterialTheme.colorScheme.outline
        ListStatus.DROPPED -> MaterialTheme.colorScheme.errorContainer
    }
