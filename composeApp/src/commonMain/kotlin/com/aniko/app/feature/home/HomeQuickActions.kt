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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * 4 плитки быстрых действий главного экрана (P7.T1) — чистая навигация без сетевых данных,
 * все колбэки опциональны (дефолт `{}`) — координатор Фазы 7 подключит реальные переходы на
 * Catalog/Schedule/Filters/случайный тайтл в `App.kt` без правки этой сигнатуры.
 *
 * Раскладка по [windowSize] (P7.T2): Medium — один ряд из всех 4 плиток, Compact и Expanded —
 * сетка 2×2 (на Expanded этот блок уже делит ширину с баннером в общем `Row`, см.
 * `HomeScreen.HomeHeroSection`, поэтому там нужна узкая колонка, а не широкий ряд).
 *
 * Track C (2026-09-04, точное соответствие макету Claude Design): плитки без иконок — только
 * цветной скруглённый прямоугольник (свой полупрозрачный оттенок на плитку, см.
 * [POPULAR_TILE_COLOR]/[SCHEDULE_TILE_COLOR]/[FILTERS_TILE_COLOR]/[RANDOM_TILE_COLOR]) + жирный
 * Manrope-лейбл. Третья плитка переименована Catalog/Library в Popular/Filters (макет
 * ведёт ОБЕ, Popular и Filters, в Catalog) — `onFilterClick` переименован из `onLibraryClick`:
 * колбэк по смыслу больше не «открыть Библиотеку» (`AnixDestination.Library` остаётся доступен
 * через нижнюю навигацию, просто эта плитка Home больше туда не ведёт), а «открыть Catalog с
 * акцентом на фильтры» — новое имя параметра честнее описывает, куда он теперь фактически ведёт
 * (см. `App.kt`, вызывающая сторона передаёт туда тот же переход, что и `onCatalogClick`).
 */
@Suppress("LongParameterList") // 4 независимых навигационных колбэка плиток + layout-настройки.
@Composable
fun HomeQuickActions(
    modifier: Modifier = Modifier,
    windowSize: AnixWindowSize = LocalAnixWindowSize.current,
    onCatalogClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onRandomClick: () -> Unit = {},
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    val tiles =
        listOf(
            QuickActionTile(strings.homeQuickActionPopular, POPULAR_TILE_COLOR, onCatalogClick),
            QuickActionTile(strings.homeQuickActionSchedule, SCHEDULE_TILE_COLOR, onScheduleClick),
            QuickActionTile(strings.homeQuickActionFilter, FILTERS_TILE_COLOR, onFilterClick),
            QuickActionTile(strings.homeQuickActionRandom, RANDOM_TILE_COLOR, onRandomClick),
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
    val background: Color,
    val onClick: () -> Unit,
)

/**
 * Без иконки (Track C, см. KDoc [HomeQuickActions]) — только цветная плитка + центрированный
 * жирный лейбл (`titleMedium` — Manrope Bold, см. `Type.kt` — не трогаем сам файл, переиспользуем
 * готовый стиль).
 */
@Composable
private fun QuickActionTileView(
    tile: QuickActionTile,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(QUICK_ACTION_TILE_CORNER)

    Column(
        modifier =
            modifier
                .height(dimens.quickActionTileHeight)
                .clip(shape)
                .background(tile.background, shape)
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
        Text(
            text = tile.label,
            style = MaterialTheme.typography.titleMedium,
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

/** Радиус скругления плитки — макет задаёт ~14dp, между уже существующими шагами токенов
 *  [com.aniko.ui.theme.AnixDimens.cornerM] (12dp) и `.corner16` (16dp) — локальная константа,
 *  а не новый шаг в общей шкале радиусов ради одного экрана. */
private val QUICK_ACTION_TILE_CORNER = 14.dp

// Track C (2026-09-04): свой полупрозрачный цвет на каждую плитку, точные hex пересчитаны из
// design-tokens макета (OKLCH, формула Ottosson OKLab→linear sRGB→гамма-коррекция, L=0.32 C=0.1
// у всех четырёх, отличается только H) — тем же методом, что и `AnixPalette` (см. KDoc `Color.kt`,
// не трогаем сам файл: это Home-локальные, не переиспользуемые в других экранах цвета). Alpha —
// именованными константами (не инлайн-литералами в `.copy(alpha = ...)`), тот же приём, что и
// `BANNER_SUBTITLE_ALPHA`/`INDICATOR_DOT_ALPHA` в `HomeBanner.kt` — detekt `MagicNumber`.
private const val POPULAR_TILE_ALPHA = 0.32f
private const val SCHEDULE_TILE_ALPHA = 0.28f
private const val FILTERS_TILE_ALPHA = 0.3f
private const val RANDOM_TILE_ALPHA = 0.26f

// oklch(0.32 0.1 22 / 0.32) — тёплый красно-коричневый.
@Suppress("MagicNumber") // hex-литерал цвета — то же самое исключение, что и `AnixPalette` в
// `Color.kt` (не трогаем сам файл): значение уже поимённано в KDoc/комментарии выше.
private val POPULAR_TILE_COLOR = Color(0xFF5B161A).copy(alpha = POPULAR_TILE_ALPHA)

// oklch(0.32 0.1 250 / 0.28) — холодный синий.
@Suppress("MagicNumber")
private val SCHEDULE_TILE_COLOR = Color(0xFF003463).copy(alpha = SCHEDULE_TILE_ALPHA)

// oklch(0.32 0.1 296 / 0.3) — фиолетовый (тот же hue, что и accent/primary бренда).
@Suppress("MagicNumber")
private val FILTERS_TILE_COLOR = Color(0xFF38255F).copy(alpha = FILTERS_TILE_ALPHA)

// oklch(0.32 0.1 150 / 0.26) — зелёный.
@Suppress("MagicNumber")
private val RANDOM_TILE_COLOR = Color(0xFF004013).copy(alpha = RANDOM_TILE_ALPHA)
