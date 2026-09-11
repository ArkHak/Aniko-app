package com.aniko.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * 6 плиток быстрых действий главного экрана (P7.T1, 5-я «Лента» — P16.T3 MVP, 6-я «Коллекции» —
 * P16.T16 MVP) — чистая навигация без сетевых данных, все колбэки опциональны (дефолт `{}`).
 *
 * Раскладка теперь измеряет СВОЮ ширину через [BoxWithConstraints], а не опирается на
 * полноэкранный window size class: в узкой Medium-панели ListDetailHost (~360dp) 4 колонки
 * давали плитку ~76dp, что гарантированно обрезало RU-лейбл ([TextOverflow.Ellipsis],
 * titleMedium 16sp). Теперь 4 плитки в ряд используются только когда измеренная ширина
 * позволяет дать каждой плитке не менее [QUICK_ACTION_MIN_USABLE_WIDTH] — иначе падаем к
 * 2×2, как на Compact. Хост плиток (колонка под баннером либо правый столбец hero-`Row`
 * на широком контенте, см. `HomeScreen.HomeHeroSection` — сплит `Row` включается только от
 * ширины контента ≥ 880dp) тоже передаёт сюда фактическую ширину, поэтому измерение
 * собственной ширины даёт правильный выбор в обоих случаях.
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
 *
 * 5-я плитка «Лента» (2026-09-11, P16.T3 MVP) — вне мокапа Claude Design (там всего 4), цвет
 * подобран в той же OKLCH-семье (`L≈0.2-0.26 S≈45-100% H` — см. [FEED_TILE_COLOR]), не пиксель-
 * референс, а согласованное с остальными четырьмя продолжение палитры (тёплый янтарный,
 * перекликается с золотыми звёздами новой иконки приложения). Раскладка `BoxWithConstraints`
 * уже умела заполнять неполный последний ряд (см. комментарий у вызова [QuickActionTileView]
 * ниже) — пятая плитка просто становится единственной во третьем ряду 2-колоночной раскладки
 * без правок самого layout-алгоритма.
 *
 * 6-я плитка «Коллекции» (2026-09-11, P16.T16 MVP) — тот же приём, что у «Лента»: цвет из той же
 * OKLCH-семьи (бирюзовый, см. [COLLECTIONS_TILE_COLOR]), заполняет теперь полный третий ряд
 * 2-колоночной раскладки (2×3, как на Compact).
 */
@Suppress("LongParameterList") // 6 независимых навигационных колбэков плиток + layout-настройки.
@Composable
fun HomeQuickActions(
    modifier: Modifier = Modifier,
    onCatalogClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onRandomClick: () -> Unit = {},
    onFeedClick: () -> Unit = {},
    onCollectionsClick: () -> Unit = {},
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    val tiles =
        listOf(
            QuickActionTile(strings.homeQuickActionPopular, POPULAR_TILE_COLOR, onCatalogClick),
            QuickActionTile(strings.homeQuickActionSchedule, SCHEDULE_TILE_COLOR, onScheduleClick),
            QuickActionTile(strings.homeQuickActionFilter, FILTERS_TILE_COLOR, onFilterClick),
            QuickActionTile(strings.homeQuickActionRandom, RANDOM_TILE_COLOR, onRandomClick),
            QuickActionTile(strings.homeQuickActionFeed, FEED_TILE_COLOR, onFeedClick),
            QuickActionTile(strings.homeQuickActionCollections, COLLECTIONS_TILE_COLOR, onCollectionsClick),
        )

    // Измеряем именно ту ширину, которую займёт блок плиток в текущем контейнере:
    // на Medium ListDetailHost-панель ~360dp, на широком hero-`Row` (≥ 880dp контента)
    // правый столбец ~290dp.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 4 плитки в ряд нужны, чтобы каждая получила не менее QUICK_ACTION_MIN_USABLE_WIDTH;
        // иначе RU-лейбл titleMedium обрезается эллипсисом.
        val fourColumnThreshold =
            QUICK_ACTION_MIN_USABLE_WIDTH * FOUR_COLUMN_LAYOUT +
                dimens.spaceS * (FOUR_COLUMN_LAYOUT - 1)
        val columns = if (maxWidth >= fourColumnThreshold) FOUR_COLUMN_LAYOUT else TWO_COLUMN_LAYOUT

        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                ) {
                    rowTiles.forEach { tile ->
                        QuickActionTileView(tile = tile, modifier = Modifier.weight(1f))
                    }
                    // Последний ряд может быть короче остальных — заполняем пустыми
                    // весами, чтобы последняя плитка не растягивалась на всю ширину ряда.
                    repeat(columns - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
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

private const val TWO_COLUMN_LAYOUT = 2
private const val FOUR_COLUMN_LAYOUT = 4

/**
 * Минимальная ширина плитки, при которой RU-лейбл titleMedium (16sp) не уходит в
 * TextOverflow.Ellipsis. Medium-панель ListDetailHost (~360dp) при 4 колонках давала
 * ~76dp, что гарантированно обрезало подписи — отсюда порог для 4-колоночного режима.
 */
private val QUICK_ACTION_MIN_USABLE_WIDTH = 120.dp

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

// 2026-09-11 (P16.T3 MVP) — вне мокапа Claude Design, см. KDoc [HomeQuickActions] про метод.
private const val FEED_TILE_ALPHA = 0.3f

// 2026-09-11 (P16.T16 MVP) — та же логика, что у FEED_TILE_ALPHA: вне мокапа, новая плитка.
private const val COLLECTIONS_TILE_ALPHA = 0.3f

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

// #664D00 (hue 45°, тёплый янтарный) — не пиксель-референс макета (см. KDoc [HomeQuickActions]),
// та же OKLCH-семья L/S, что у остальных четырёх плиток.
@Suppress("MagicNumber")
private val FEED_TILE_COLOR = Color(0xFF664D00).copy(alpha = FEED_TILE_ALPHA)

// oklch(0.32 0.1 190 / 0.3) — бирюзовый, та же OKLCH-семья L/C, что у остальных пяти плиток.
@Suppress("MagicNumber")
private val COLLECTIONS_TILE_COLOR = Color(0xFF00413E).copy(alpha = COLLECTIONS_TILE_ALPHA)
