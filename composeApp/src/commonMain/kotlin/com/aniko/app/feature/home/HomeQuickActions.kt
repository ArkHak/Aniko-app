package com.aniko.app.feature.home

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.ui.glass.LiquidGlassStyle
import com.aniko.ui.glass.LocalGlassBackdrop
import com.aniko.ui.glass.liquidGlass
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
 * цветной скруглённый прямоугольник (свой оттенок на плитку, theme-aware с 2026-09-11 — см.
 * KDoc [QuickActionTileColors]) + жирный Manrope-лейбл. Третья плитка переименована
 * Catalog/Library в Popular/Filters (макет ведёт ОБЕ, Popular и Filters, в Catalog) —
 * `onFilterClick` переименован из `onLibraryClick`: колбэк по смыслу больше не «открыть
 * Библиотеку» (`AnixDestination.Library` остаётся доступен через нижнюю навигацию, просто эта
 * плитка Home больше туда не ведёт), а «открыть Catalog с акцентом на фильтры» — новое имя
 * параметра честнее описывает, куда он теперь фактически ведёт (см. `App.kt`, вызывающая сторона
 * передаёт туда тот же переход, что и `onCatalogClick`).
 *
 * 5-я плитка «Лента» (2026-09-11, P16.T3 MVP) — вне мокапа Claude Design (там всего 4), цвет
 * подобран в той же OKLCH-семье, что и остальные (см. KDoc [QuickActionTileColors]), не пиксель-
 * референс, а согласованное с остальными четырьмя продолжение палитры (тёплый янтарный,
 * перекликается с золотыми звёздами новой иконки приложения). Раскладка `BoxWithConstraints`
 * уже умела заполнять неполный последний ряд (см. комментарий у вызова [QuickActionTileView]
 * ниже) — пятая плитка просто становится единственной во третьем ряду 2-колоночной раскладки
 * без правок самого layout-алгоритма.
 *
 * 6-я плитка «Коллекции» (2026-09-11, P16.T16 MVP) — тот же приём, что у «Лента»: цвет из той же
 * OKLCH-семьи (бирюзовый, см. KDoc [QuickActionTileColors]), заполняет теперь полный третий ряд
 * 2-колоночной раскладки (2×3, как на Compact).
 *
 * Компактнее + стеклянные (2026-09-11, живой фидбек после теста только что зашедшего
 * theme-aware цвета: «плитки топорные — большие, а текст мелкий; сделай их стеклянными/глянцевыми
 * и меньше»). Три независимых правки:
 * - [com.aniko.ui.theme.AnixDimens.quickActionTileHeight] уменьшена 88dp→68dp (см. её KDoc) —
 *   меньше пустого поля вокруг однострочного лейбла.
 * - Лейбл плитки крупнее и жирнее относительно плитки — см. KDoc [QuickActionTileView].
 * - Плоская заливка (`Modifier.background`) заменена на [com.aniko.ui.glass.liquidGlass] в
 *   fallback-режиме — плитки лежат в потоке страницы, а не поверх скроллящегося контента под
 *   плавающим элементом (как `AnixNavigationBar`), поэтому реальному backdrop-blur физически
 *   нечего размывать; материал спроектирован деградировать именно в такой ситуации в плотную
 *   тонированную заливку ([com.aniko.ui.glass.LiquidGlassStyle.fallbackAlpha]) с тем же
 *   вертикальным градиентом/specular-бликом/rim-обводкой, что и у полноценного блюра — этого
 *   достаточно для "глянцевого стекла" без реального размытия. `tint` стекла — тот самый
 *   насыщенный OKLCH-тон конкретной плитки (см. KDoc [QuickActionTileColors]), НЕ нейтральный
 *   `colorScheme.surface`, как у таб-бара — иначе все 6 плиток слились бы в один цвет и пропала
 *   бы их идентификация друг от друга по цвету. Fallback обеспечен явным
 *   `CompositionLocalProvider(LocalGlassBackdrop provides null)` вокруг блока плиток (2026-09-17):
 *   одного `state = null` мало — `AdaptiveScaffold` провайдит backdrop-local выше по дереву, и без
 *   изоляции плитки читали бы записываемый ими же слой (рекурсия record→draw, SIGILL в
 *   skiko-харнессе desktopTest).
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
    val tileColors = quickActionTileColors()

    val tiles =
        listOf(
            QuickActionTile(strings.homeQuickActionPopular, tileColors.popular, onCatalogClick),
            QuickActionTile(strings.homeQuickActionSchedule, tileColors.schedule, onScheduleClick),
            QuickActionTile(strings.homeQuickActionFilter, tileColors.filters, onFilterClick),
            QuickActionTile(strings.homeQuickActionRandom, tileColors.random, onRandomClick),
            QuickActionTile(strings.homeQuickActionFeed, tileColors.feed, onFeedClick),
            QuickActionTile(strings.homeQuickActionCollections, tileColors.collections, onCollectionsClick),
        )

    // Изоляция от backdrop-источника: `AdaptiveScaffold` провайдит `LocalGlassBackdrop` и вешает
    // `glassBackdropSource` на всю контентную область Compact-ветки (см. AdaptiveScaffold.kt),
    // т.е. источник ЕСТЬ выше по дереву, и `state = null` в liquidGlass() сам по себе fallback
    // НЕ гарантирует — нода резолвит LocalGlassBackdrop (см. LiquidGlassNode.reresolveState).
    // Плитки лежат внутри записываемого поддерева: с реальным state их draw во время записи
    // слоя читал бы записываемый слой — рекурсия record→draw, SIGILL по переполнению стека в
    // skiko-харнессе desktopTest (2026-09-17). Явный `provides null` отрезает local: плитки
    // всегда рисуются плотной тонированной заливкой, как и задумано (реальному blur здесь
    // физически нечего размывать — плитки в потоке страницы, не поверх скролла).
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
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
}

private data class QuickActionTile(
    val label: String,
    val glass: QuickActionTileGlass,
    val onClick: () -> Unit,
)

/**
 * Без иконки (Track C, см. KDoc [HomeQuickActions]) — только стеклянная плитка (см. KDoc
 * [HomeQuickActions] про fallback-режим [com.aniko.ui.glass.liquidGlass]) + центрированный
 * лейбл, крупнее и жирнее базового `titleMedium` (Manrope SemiBold 17sp/22, см. `Type.kt` — не
 * трогаем сам файл, локальный override поверх готового стиля): при уменьшенной высоте плитки
 * (68dp, см. KDoc [com.aniko.ui.theme.AnixDimens.quickActionTileHeight]) текст должен визуально
 * доминировать над плиткой, а не теряться в пустом поле — [QUICK_ACTION_LABEL_FONT_SIZE]/
 * `FontWeight.Bold` дают этот эффект, `maxLines = 1` + `TextOverflow.Ellipsis` остаются как были
 * (проверено на самом длинном RU-лейбле «Случайный тайтл» на iOS-симуляторе — не обрезается).
 */
@Composable
private fun QuickActionTileView(
    tile: QuickActionTile,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(QUICK_ACTION_TILE_CORNER)
    val glassStyle =
        LiquidGlassStyle(
            blurRadius = dimens.glassBlurRadius,
            // Не используется на пути отрисовки: блок плиток изолирован от backdrop-local'а
            // (`CompositionLocalProvider(LocalGlassBackdrop provides null)` в [HomeQuickActions]),
            // поэтому всегда берётся fallbackAlpha (реальному blur физически нечего размывать —
            // см. KDoc [HomeQuickActions]). Значение заполнено для структурной полноты стиля (и на
            // случай, если плитки когда-нибудь переедут под реальный backdrop-blur).
            tintAlpha = colors.glassTintAlpha,
            tint = tile.glass.tint,
            fallbackAlpha = tile.glass.fallbackAlpha,
            specular = colors.glassSpecular,
            specularHeight = dimens.glassSpecularHeight,
            rim = colors.glassRim,
            rimWidth = dimens.glassRimWidth,
            shape = shape,
        )

    Column(
        modifier =
            modifier
                .height(dimens.quickActionTileHeight)
                // liquidGlass() уже клипует по style.shape (см. её KDoc) — отдельный
                // Modifier.clip(shape) здесь был бы избыточным дублированием.
                .liquidGlass(style = glassStyle, state = null)
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
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = QUICK_ACTION_LABEL_FONT_SIZE,
                    fontWeight = FontWeight.Bold,
                ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            // P11.T8/T11 (Трек C): RU-подпись плитки обычно длиннее EN — без overflow текст
            // жёстко обрезался бы посимвольно (TextOverflow.Clip по умолчанию), эллипсис честно
            // сигнализирует урезание.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Размер лейбла плитки — крупнее базового `titleMedium` (17sp), см. KDoc [QuickActionTileView]
 *  про причину увеличения. */
private val QUICK_ACTION_LABEL_FONT_SIZE = 19.sp

private const val TWO_COLUMN_LAYOUT = 2
private const val FOUR_COLUMN_LAYOUT = 4

/**
 * Минимальная ширина плитки, при которой RU-лейбл (см. [QUICK_ACTION_LABEL_FONT_SIZE]) не уходит
 * в TextOverflow.Ellipsis. Medium-панель ListDetailHost (~360dp) при 4 колонках давала
 * ~76dp, что гарантированно обрезало подписи — отсюда порог для 4-колоночного режима.
 */
private val QUICK_ACTION_MIN_USABLE_WIDTH = 120.dp

/** Радиус скругления плитки — макет задаёт ~14dp, между уже существующими шагами токенов
 *  [com.aniko.ui.theme.AnixDimens.cornerM] (12dp) и `.corner16` (16dp) — локальная константа,
 *  а не новый шаг в общей шкале радиусов ради одного экрана. */
private val QUICK_ACTION_TILE_CORNER = 14.dp

/**
 * Палитра плиток — тёмная и светлая версии (2026-09-11, живой фидбек пользователя после проверки
 * на iOS-симуляторе: «пастельные плитки на светлой теме, не премиально»).
 *
 * Тёмная версия (Track C, 2026-09-04) не изменилась ВИЗУАЛЬНО — тот же тёмный HEX (OKLCH, L=0.32
 * C=0.1, отличается только H), тем же методом, что и `AnixPalette` (см. KDoc `Color.kt`, не
 * трогаем сам файл: это Home-локальные, не переиспользуемые в других экранах цвета). Начиная с
 * перехода на [com.aniko.ui.glass.liquidGlass] (см. KDoc [HomeQuickActions]) alpha ~0.26-0.32
 * больше не запечена в сам `Color` (`.copy(alpha = ...)`), а передаётся отдельно как
 * [QuickActionTileGlass.fallbackAlpha] — `LiquidGlassStyle.fallbackAlpha` заменяет альфу тинта
 * целиком при отрисовке (`tint.copy(alpha = fallbackAlpha)`), поэтому запечённая в `Color` альфа
 * была бы просто отброшена. Число то же самое, только источник истины переехал.
 *
 * Светлая версия (Track C, 2026-09-04 → редизайн 2026-09-11) — тот же тёмный полупрозрачный цвет
 * поверх светлого фона (`AnixPalette.BackgroundLight`/`SurfaceLight`) давал блёклый пастельный вид
 * вместо "драгоценного" насыщенного оттенка — простое повышение alpha того же тёмного цвета тоже
 * даёт пастель, просто темнее, проблему не решает. Вместо этого — сплошная заливка того же
 * OKLCH-семейства (тот же hue H, что у тёмного варианта), но L≈0.58 C≈0.12 вместо L=0.32 —
 * насыщенный "драгоценный" тон, читаемый на белом (посчитано тем же методом Ottosson OKLab→linear
 * sRGB→гамма-коррекция, что и `AnixPalette`; near-black текст плитки поверх всех шести — контраст
 * ≥4:1). [QUICK_ACTION_FALLBACK_ALPHA_LIGHT] держит эту заливку почти непрозрачной (0.96, не 1.0)
 * — минимальный стеклянный характер (см. KDoc [HomeQuickActions]) БЕЗ повторного скатывания в
 * пастель, которое как раз и было исходной жалобой.
 *
 * Выбор набора на текущее дерево — [quickActionTileColors] ниже, тот же приём luminance-порога,
 * что уже используется в проекте для аналогичного Home/chrome-локального theme-aware цвета (см.
 * [com.aniko.ui.adaptive.SidebarSlot] `sidebarBackgroundColor` — `colorScheme.surface.luminance()`
 * ниже порога = тёмная тема). Не `isSystemInDarkTheme()`: тема приложения выбирается явно через
 * `ThemeStore`, системную не следует (см. KDoc `AppTheme`), поэтому фактическая цветовая схема
 * дерева — единственный надёжный источник истины.
 */
private data class QuickActionTileColors(
    val popular: QuickActionTileGlass,
    val schedule: QuickActionTileGlass,
    val filters: QuickActionTileGlass,
    val random: QuickActionTileGlass,
    val feed: QuickActionTileGlass,
    val collections: QuickActionTileGlass,
)

/**
 * `tint` — насыщенный OKLCH-тон конкретной плитки (свой на light/dark, см. KDoc
 * [QuickActionTileColors]), передаётся в [LiquidGlassStyle.tint]. `fallbackAlpha` —
 * [LiquidGlassStyle.fallbackAlpha] той же плитки (см. её KDoc: заменяет альфу [tint] целиком при
 * отрисовке в fallback-режиме без реального блюра, см. KDoc [HomeQuickActions]).
 */
private data class QuickActionTileGlass(
    val tint: Color,
    val fallbackAlpha: Float,
)

@Composable
private fun quickActionTileColors(): QuickActionTileColors {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < QUICK_ACTION_DARK_LUMINANCE_THRESHOLD
    return if (isDark) QUICK_ACTION_TILE_COLORS_DARK else QUICK_ACTION_TILE_COLORS_LIGHT
}

// Тот же порог, что SidebarSlot.SIDEBAR_DARK_LUMINANCE_THRESHOLD — не переиспользован напрямую
// (internal/private в разных модулях), значение совпадает намеренно для консистентности.
private const val QUICK_ACTION_DARK_LUMINANCE_THRESHOLD = 0.5f

private const val POPULAR_TILE_ALPHA_DARK = 0.32f
private const val SCHEDULE_TILE_ALPHA_DARK = 0.28f
private const val FILTERS_TILE_ALPHA_DARK = 0.3f
private const val RANDOM_TILE_ALPHA_DARK = 0.26f

// 2026-09-11 (P16.T3 MVP) — вне мокапа Claude Design, см. KDoc [HomeQuickActions] про метод.
private const val FEED_TILE_ALPHA_DARK = 0.3f

// 2026-09-11 (P16.T16 MVP) — та же логика, что у FEED_TILE_ALPHA_DARK: вне мокапа, новая плитка.
private const val COLLECTIONS_TILE_ALPHA_DARK = 0.3f

/** См. KDoc [QuickActionTileColors] про светлую версию — почти непрозрачно, чтобы не съехать
 *  обратно в блёклый пастельный вид, но с лёгким стеклянным характером. */
private const val QUICK_ACTION_FALLBACK_ALPHA_LIGHT = 0.96f

@Suppress("MagicNumber") // hex-литерал цвета — то же самое исключение, что и `AnixPalette` в
// `Color.kt` (не трогаем сам файл): значение уже поимённано в KDoc/комментарии выше.
private val QUICK_ACTION_TILE_COLORS_DARK =
    QuickActionTileColors(
        // oklch(0.32 0.1 22 / 0.32) — тёплый красно-коричневый.
        popular = QuickActionTileGlass(Color(0xFF5B161A), POPULAR_TILE_ALPHA_DARK),
        // oklch(0.32 0.1 250 / 0.28) — холодный синий.
        schedule = QuickActionTileGlass(Color(0xFF003463), SCHEDULE_TILE_ALPHA_DARK),
        // oklch(0.32 0.1 296 / 0.3) — фиолетовый (тот же hue, что и accent/primary бренда).
        filters = QuickActionTileGlass(Color(0xFF38255F), FILTERS_TILE_ALPHA_DARK),
        // oklch(0.32 0.1 150 / 0.26) — зелёный.
        random = QuickActionTileGlass(Color(0xFF004013), RANDOM_TILE_ALPHA_DARK),
        // #664D00 (hue 45°, тёплый янтарный) — не пиксель-референс макета, та же OKLCH-семья
        // L/S, что у остальных четырёх плиток.
        feed = QuickActionTileGlass(Color(0xFF664D00), FEED_TILE_ALPHA_DARK),
        // oklch(0.32 0.1 190 / 0.3) — бирюзовый, та же OKLCH-семья L/C, что у остальных пяти.
        collections = QuickActionTileGlass(Color(0xFF00413E), COLLECTIONS_TILE_ALPHA_DARK),
    )

@Suppress("MagicNumber")
private val QUICK_ACTION_TILE_COLORS_LIGHT =
    QuickActionTileColors(
        // oklch(0.58 0.12 22) — тот же hue, что тёмный Popular, сплошная насыщенная заливка.
        popular = QuickActionTileGlass(Color(0xFFB75A59), QUICK_ACTION_FALLBACK_ALPHA_LIGHT),
        // oklch(0.58 0.12 250) — тот же hue, что тёмный Schedule.
        schedule = QuickActionTileGlass(Color(0xFF3C7EBE), QUICK_ACTION_FALLBACK_ALPHA_LIGHT),
        // oklch(0.58 0.12 296) — тот же hue, что тёмный Filters.
        filters = QuickActionTileGlass(Color(0xFF816AB9), QUICK_ACTION_FALLBACK_ALPHA_LIGHT),
        // oklch(0.58 0.12 150) — тот же hue, что тёмный Random.
        random = QuickActionTileGlass(Color(0xFF3D8E53), QUICK_ACTION_FALLBACK_ALPHA_LIGHT),
        // oklch(0.58 0.12 45) — тот же hue, что тёмный Feed.
        feed = QuickActionTileGlass(Color(0xFFB36139), QUICK_ACTION_FALLBACK_ALPHA_LIGHT),
        // oklch(0.58 0.12 190) — тот же hue, что тёмный Collections (вне sRGB-гаммута при этих
        // L/C, ближайшее валидное значение — фактическое отображаемое здесь).
        collections = QuickActionTileGlass(Color(0xFF00908A), QUICK_ACTION_FALLBACK_ALPHA_LIGHT),
    )
