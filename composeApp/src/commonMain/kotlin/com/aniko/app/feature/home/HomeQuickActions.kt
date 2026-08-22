package com.aniko.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * 4 плитки быстрых действий главного экрана (P7.T1) — чистая навигация без сетевых данных,
 * все колбэки опциональны (дефолт `{}`) — координатор Фазы 7 подключит реальные переходы на
 * Catalog/Schedule/Library/случайный тайтл в `App.kt` без правки этой сигнатуры.
 *
 * Раскладка по [windowSize] (P7.T2): Medium — один ряд из всех 4 плиток, Compact и Expanded —
 * сетка 2×2 (на Expanded этот блок уже делит ширину с баннером в общем `Row`, см.
 * `HomeScreen.HomeHeroSection`, поэтому там нужна узкая колонка, а не широкий ряд).
 */
@Suppress("LongParameterList") // 4 независимых навигационных колбэка плиток + layout-настройки.
@Composable
fun HomeQuickActions(
    modifier: Modifier = Modifier,
    windowSize: AnixWindowSize = LocalAnixWindowSize.current,
    onCatalogClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onRandomClick: () -> Unit = {},
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    val tiles =
        listOf(
            QuickActionTile(strings.homeQuickActionCatalog, Icons.Filled.Search, onCatalogClick),
            QuickActionTile(strings.homeQuickActionSchedule, Icons.Filled.CalendarMonth, onScheduleClick),
            QuickActionTile(strings.homeQuickActionLibrary, Icons.Filled.VideoLibrary, onLibraryClick),
            QuickActionTile(strings.homeQuickActionRandom, Icons.Filled.Shuffle, onRandomClick),
        )
    val columns = if (windowSize == AnixWindowSize.Medium) MEDIUM_COLUMNS else COMPACT_COLUMNS

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        tiles.chunked(columns).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            ) {
                rowTiles.forEach { tile -> QuickActionTileView(tile = tile, modifier = Modifier.weight(1f)) }
                // Последний ряд может быть короче остальных (4 плитки, 4 колонки на Medium —
                // не короче, но при других значениях columns это защищает от растягивания
                // последней плитки на всю ширину ряда).
                repeat(columns - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

private data class QuickActionTile(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun QuickActionTileView(
    tile: QuickActionTile,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(dimens.cornerM)

    Column(
        modifier =
            modifier
                .height(dimens.quickActionTileHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .clickable(onClick = tile.onClick)
                // Подтверждено на устройстве (Фаза 11, T9): предположение ниже про "подпись уже
                // рядом" не выполнялось само по себе — `Modifier.clickable` не сливает потомков
                // в один озвучиваемый узел (обычный semantics(mergeDescendants=true) тоже не
                // помог, проверено на эмуляторе), TalkBack фокусировал плитку без имени.
                // clearAndSetSemantics задаёт имя напрямую на кликабельном узле. Не голый
                // tile.label: плитка "Расписание" и вкладка нижней навигации "Расписание"
                // озвучивались бы одинаково — WCAG duplicate-descriptions (найдено тем же
                // прогоном аудита) — глагол disambiguates обе цели друг от друга для TalkBack.
                .clearAndSetSemantics {
                    contentDescription = strings.homeQuickActionOpenContentDescription(tile.label)
                }.padding(dimens.spaceS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // contentDescription = null: подпись плитки уже показана рядом как видимый текст, второе
        // озвучивание того же самого скринридером было бы избыточным дублированием.
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // P11.T8/T11 (Трек C): RU-подпись плитки обычно длиннее EN — без overflow текст
            // жёстко обрезался бы посимвольно (TextOverflow.Clip по умолчанию), эллипсис честно
            // сигнализирует урезание.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val COMPACT_COLUMNS = 2
private const val MEDIUM_COLUMNS = 4
