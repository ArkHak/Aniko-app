package com.aniko.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Плитка одной статистики: крупное значение сверху, мелкая приглушённая подпись под ним.
 * Извлечено из инлайнового кода `ProfileScreen.StatsGrid` (Фаза 6, P6.T9) как переиспользуемый
 * компонент — сам `StatsGrid` пока не переключён на него (переключение — работа Фазы 9), но новые
 * места (и будущий рефакторинг профиля) могут использовать `StatTile`/[StatTileRow] сразу.
 *
 * [value] — `String`, а не `Int`/`Number`: форматирование значения (часы просмотра, разделители
 * разрядов и т.п.) — ответственность вызывающей стороны, в commonMain нет `String.format`
 * (см. KDoc [com.aniko.ui.i18n.Strings] — та же причина, по которой форматируемые строки там
 * выражены лямбдами).
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = STAT_LABEL_ALPHA),
        )
    }
}

/** Данные одной плитки для [StatTileRow]. */
data class StatTileData(
    val value: String,
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * Сетка плиток статистики: [tiles] разбивается на строки по [columns] штук (`chunked`), каждая
 * плитка в строке занимает равную долю ширины (`Modifier.weight(1f)`) — та же раскладка, что и
 * `ProfileScreen.StatsGrid`, но параметризуемая под другое число колонок.
 */
@Composable
fun StatTileRow(
    tiles: List<StatTileData>,
    modifier: Modifier = Modifier,
    columns: Int = STAT_TILE_ROW_DEFAULT_COLUMNS,
) {
    val dimens = AnixThemeTokens.dimens
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        tiles.chunked(columns).forEach { rowTiles ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                rowTiles.forEach { tile ->
                    StatTile(
                        value = tile.value,
                        label = tile.label,
                        onClick = tile.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val STAT_TILE_ROW_DEFAULT_COLUMNS = 4
private const val STAT_LABEL_ALPHA = 0.7f
