package com.aniko.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aniko.model.AnixGenres
import com.aniko.model.CatalogFilter
import com.aniko.ui.component.AnixFilterChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Боковая панель фильтров каталога для Expanded (desktop-артборд мокапа, строка 800): заголовок
 * "Catalog", вкладки «Все/Новинки» и вертикальные списки чипов статуса/жанра (не [AnixFilterChipRow]
 * с `wrap = true`/[androidx.compose.foundation.layout.FlowRow] — макет рисует их отдельными
 * строками сверху вниз, не переносом по ширине).
 *
 * Используется только в `ExpandedCatalogLayout` (`SearchScreen.kt`) — вызывающая сторона задаёт
 * фиксированную ширину 200.dp через [modifier] (не `weight`, см. бриф трека B). Compact/Medium с
 * P13.T3 переиспользуют не эту панель целиком, а [CatalogInlineFilterChips] ниже — те же данные,
 * но горизонтальными скроллящимися рядами, без заголовка/вкладок/вертикального скролла.
 *
 * Год/кол-во серий/длительность/возраст (строка "Доп. фильтры" в аудите P0.T3) — вне объёма
 * P7.T3-T6 (не запрошены брифом трека B), сюда не добавлены.
 */
@Suppress("LongParameterList") // Фильтр + вкладка + 3 колбэка панели — публичная сигнатура,
// разбиение в объект-параметр добавило бы косвенность ради самого разбиения.
@Composable
fun CatalogFilterPanel(
    filter: CatalogFilter,
    tab: CatalogTab,
    onTabSelected: (CatalogTab) -> Unit,
    onStatusToggle: (Int) -> Unit,
    onGenreToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PANEL_SECTION_GAP),
    ) {
        Text(
            text = strings.navCatalog,
            style =
                MaterialTheme.typography.displaySmall.copy(
                    fontSize = TITLE_FONT_SIZE,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = TITLE_LINE_HEIGHT,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        CatalogTabRow(selected = tab, onSelect = onTabSelected)

        // Мокап (строка 800, `catalogIsAll`): секции фильтров живут только под вкладкой «Все» —
        // «Новинки» — отдельная сортировка каталога без собственного набора фильтров на панели.
        if (tab == CatalogTab.All) {
            FilterSection(title = strings.catalogFiltersTitle) {
                STATUS_OPTIONS.forEach { option ->
                    CatalogFilterChip(
                        label = option.label(strings),
                        selected = filter.statusId == option.id,
                        selectedColor = MaterialTheme.colorScheme.secondary,
                        onClick = { onStatusToggle(option.id) },
                    )
                }
            }

            FilterSection(title = strings.navCatalog) {
                AnixGenres.popular.forEach { genre ->
                    CatalogFilterChip(
                        label = genre,
                        selected = genre in filter.genres,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        onClick = { onGenreToggle(genre) },
                    )
                }
            }
        }
    }
}

/**
 * Статус-чипы + жанр-чипы как две отдельные горизонтально скроллящиеся строки (P13.T3) — для
 * Compact/Medium `CompactCatalogLayout` (`SearchScreen.kt`), рендерится прямо под полем поиска
 * вместо кнопки "Фильтры" + [CatalogFilterPanel] в `ModalBottomSheet` (см. журнал изменений плана,
 * P13). Мокап Claude Design (`statusChips`/`genreChips`, `overflow-x:auto`) рисует их именно так —
 * рядами без переноса, а не сеткой/сайдбар-колонкой, отсюда `wrap = false` (дефолт
 * [AnixFilterChipRow]) вместо `wrap = true`, который использует [CatalogFilterPanel] для Expanded.
 *
 * Без заголовка "Фильтры" и кнопки "Сбросить" — в мокапе их тоже нет рядом с чипами (сброс —
 * по одному клику на уже активный чип, тот же паттерн, что уже реализован в
 * `SearchViewModel.updateFilter`), и без вертикального скролла — Compact-раскладка сама скроллит
 * весь экран, а не только панель фильтров.
 */
@Composable
fun CatalogInlineFilterChips(
    filter: CatalogFilter,
    onStatusToggle: (Int) -> Unit,
    onGenreToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        // Track A (сверка Compact-раскладки, 2026-09-04): статус-чипы и жанр-чипы раньше
        // рендерились одинаково (дефолтный M3 FilterChip) — макет разводит их на РАЗНЫЕ акценты:
        // статус — secondary, жанр — primary. Только Compact-вариант (эта функция) — боковая
        // панель Expanded (CatalogFilterPanel выше) переиспользует тот же принцип, но собственную
        // вертикальную раскладку чипов (не эту функцию).
        AnixFilterChipRow(
            items = STATUS_OPTIONS.map { it.id },
            selected = setOfNotNull(filter.statusId),
            label = { id -> STATUS_OPTIONS.first { it.id == id }.label(strings) },
            onToggle = onStatusToggle,
            selectedColor = MaterialTheme.colorScheme.secondary,
        )

        AnixFilterChipRow(
            items = AnixGenres.popular,
            selected = filter.genres,
            label = { genre -> genre },
            onToggle = onGenreToggle,
            selectedColor = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Вкладки «Все/Новинки» в левой колонке Expanded (мокап: padding 8/12, radius 9, 12.5px/700,
 * активная — плашка primary alpha 0.2 + border 1dp alpha 0.5).
 */
@Composable
private fun CatalogTabRow(
    selected: CatalogTab,
    onSelect: (CatalogTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
    ) {
        CatalogTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .semantics(mergeDescendants = true) {}
                        .clickable(onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(TAB_RADIUS))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = ACTIVE_TAB_CONTAINER_ALPHA),
                                        ).border(
                                            TAB_BORDER_WIDTH,
                                            MaterialTheme.colorScheme.primary.copy(alpha = ACTIVE_TAB_BORDER_ALPHA),
                                            RoundedCornerShape(TAB_RADIUS),
                                        )
                                } else {
                                    Modifier
                                },
                            ).padding(horizontal = TAB_HORIZONTAL_PADDING, vertical = TAB_VERTICAL_PADDING),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.tabLabel(strings),
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontSize = TAB_FONT_SIZE,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun CatalogTab.tabLabel(strings: Strings): String =
    when (this) {
        CatalogTab.All -> strings.catalogTabAll
        CatalogTab.New -> strings.catalogTabNew
    }

/**
 * Секция вертикальных чипов с uppercase-заголовком (мокап: 11px/700, letterSpacing 0.05em,
 * color `--t2-55`/[com.aniko.ui.theme.AnixColors.textSecondary55]).
 */
@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    chips: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SECTION_CHIP_GAP),
    ) {
        Text(
            text = title.uppercase(),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = SECTION_TITLE_LETTER_SPACING,
                ),
            color = AnixThemeTokens.colors.textSecondary55,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        chips()
    }
}

/**
 * Вертикальный чип фильтра боковой панели Expanded (мокап: padding 8/10, radius 8, 12.5px/600;
 * активный — плашка [selectedColor] alpha 0.18 + border 1dp alpha 0.45). Занимает всю ширину
 * 200.dp панели — сама панель растёт вертикальным списком, не переносом по ширине.
 */
@Composable
private fun CatalogFilterChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .semantics(mergeDescendants = true) {}
                .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CHIP_RADIUS))
                    .then(
                        if (selected) {
                            Modifier
                                .background(selectedColor.copy(alpha = ACTIVE_CHIP_CONTAINER_ALPHA))
                                .border(
                                    CHIP_BORDER_WIDTH,
                                    selectedColor.copy(alpha = ACTIVE_CHIP_BORDER_ALPHA),
                                    RoundedCornerShape(CHIP_RADIUS),
                                )
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = CHIP_HORIZONTAL_PADDING, vertical = CHIP_VERTICAL_PADDING),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontSize = CHIP_FONT_SIZE,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Статусы каталога — `FilterRequest.status_id` (подтверждено вживую, см. P0.T3): `1` — вышел,
 * `2` — выходит, `3` — анонс. Ровно те же id, что и в [com.aniko.model.CatalogFilter.statusId].
 */
private data class StatusOption(
    val id: Int,
    val label: (Strings) -> String,
)

private const val STATUS_ID_FINISHED = 1
private const val STATUS_ID_ONGOING = 2
private const val STATUS_ID_ANNOUNCE = 3

private val STATUS_OPTIONS =
    listOf(
        StatusOption(STATUS_ID_FINISHED) { it.releaseStatusFinished },
        StatusOption(STATUS_ID_ONGOING) { it.releaseStatusOngoing },
        StatusOption(STATUS_ID_ANNOUNCE) { it.releaseStatusAnnounce },
    )

/** Зазор между заголовком/вкладками/секциями панели (мокап: gap 18px). */
private val PANEL_SECTION_GAP = 18.dp
private val TITLE_FONT_SIZE = 20.sp
private val TITLE_LINE_HEIGHT = 24.sp
private val TAB_GAP = 6.dp
private val TAB_RADIUS = 9.dp
private val TAB_HORIZONTAL_PADDING = 12.dp
private val TAB_VERTICAL_PADDING = 8.dp
private val TAB_FONT_SIZE = 12.5.sp
private val TAB_BORDER_WIDTH = 1.dp
private const val ACTIVE_TAB_CONTAINER_ALPHA = 0.2f
private const val ACTIVE_TAB_BORDER_ALPHA = 0.5f

/** Зазор между чипами внутри секции (мокап: gap 6px). */
private val SECTION_CHIP_GAP = 6.dp
private val SECTION_TITLE_LETTER_SPACING = 0.05.em

private val CHIP_RADIUS = 8.dp
private val CHIP_HORIZONTAL_PADDING = 10.dp
private val CHIP_VERTICAL_PADDING = 8.dp
private val CHIP_FONT_SIZE = 12.5.sp
private val CHIP_BORDER_WIDTH = 1.dp
private const val ACTIVE_CHIP_CONTAINER_ALPHA = 0.18f
private const val ACTIVE_CHIP_BORDER_ALPHA = 0.45f
