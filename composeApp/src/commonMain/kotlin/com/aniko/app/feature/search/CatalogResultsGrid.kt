package com.aniko.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.app.ui.toContentState
import com.aniko.data.paging.PagingState
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseStatus
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.adaptive.LocalGlassBottomInset
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.AnixPoster
import com.aniko.ui.component.TitleCard
import com.aniko.ui.component.TitleCardLayout
import com.aniko.ui.component.TopEndRatingBadge
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixDimens
import com.aniko.ui.theme.AnixThemeTokens
import kotlin.math.roundToInt

/**
 * Список результатов Catalog/Search (P7.T3-T6) поверх [PagingState] — общая для обоих режимов
 * выдачи (поиск по строке / фильтр каталога, см. KDoc `SearchViewModel`), т.к. экран не
 * различает их источник. Мост `PagingState -> AnixContentState` — [toContentState]
 * (`composeApp/.../ui/PagingStateAdapter.kt`, Фаза 6).
 *
 * Раскладка по [AnixWindowSize]:
 * - Compact/Medium — списочные строки [TitleCard] ([CatalogList], тот же паттерн пагинации, что
 *   в `LibraryScreen`/старой `SearchScreen`: `itemsIndexed` + проверка индекса относительно
 *   конца списка на каждый видимый элемент, без отдельного `LazyGridState`/`snapshotFlow`).
 * - Expanded — сетка из 5 колонок ([CatalogGrid], desktop-артборд мокапа, строка 800): постер
 *   2:3 radius 14, бейдж рейтинга top-right, название/мета-строка/опциональный release-badge под
 *   постером — своя ячейка [CatalogGridItem] (не [TitleCard]: макет хочет рейтинг СПРАВА и без
 *   персональных оверлеев избранного/статуса списка, которые рисует стандартная Grid-раскладка
 *   [TitleCard]).
 */
@Suppress("LongParameterList") // Координирующий блок: пагинированное состояние + 5 колбэков.
@Composable
fun CatalogResultsGrid(
    pagingState: PagingState<Release>,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit = { _, _ -> },
    onRemoveFromList: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val isExpanded = LocalAnixWindowSize.current == AnixWindowSize.Expanded
    // P2.T10: не показываем error.message напрямую — технический AnixError, не UI-текст.
    // Один и тот же fallback для обоих режимов выдачи — специального catalogLoadError-ключа
    // фундамент Фазы 7 не заводил (в отличие от catalogEmptyResults), реюз searchError осознан.
    val contentState = pagingState.toContentState { strings.searchError }

    AnixContentSlot(
        state = contentState,
        modifier = modifier.fillMaxSize(),
        emptyMessage = strings.catalogEmptyResults,
        onRetry = onRetry,
    ) { items ->
        if (isExpanded) {
            CatalogGrid(
                items = items,
                isLoadingMore = contentState.isLoading,
                strings = strings,
                dimens = dimens,
                onReleaseClick = onReleaseClick,
                onLoadMore = onLoadMore,
            )
        } else {
            CatalogList(
                items = items,
                isLoadingMore = contentState.isLoading,
                onReleaseClick = onReleaseClick,
                onLoadMore = onLoadMore,
                dimens = dimens,
                onSetListStatus = onSetListStatus,
                onRemoveFromList = onRemoveFromList,
            )
        }
    }
}

/** Expanded: сетка 5 колонок, gap 16 (desktop-артборд мокапа, строка 800). */
@Suppress("LongParameterList") // Пагинация + рендер-зависимости ячейки, см. CatalogResultsGrid.
@Composable
private fun CatalogGrid(
    items: List<Release>,
    isLoadingMore: Boolean,
    strings: Strings,
    dimens: AnixDimens,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
        verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        contentPadding =
            PaddingValues(
                top = dimens.spaceS,
                bottom = dimens.spaceM + LocalGlassBottomInset.current,
            ),
    ) {
        itemsIndexed(items, key = { _, release -> release.id }) { index, release ->
            if (index >= items.size - PREFETCH_THRESHOLD) onLoadMore()
            CatalogGridItem(
                release = release,
                strings = strings,
                onClick = { onReleaseClick(release.id) },
            )
        }

        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnixLoadingState(Modifier.fillMaxWidth().padding(dimens.spaceM))
            }
        }
    }
}

/**
 * Ячейка сетки Catalog Expanded (мокап: постер 2:3 radius 14, бейдж рейтинга top-right inset 6,
 * название 12px/600 Manrope lh 1.25, мета-строка 10.5px `--t2-58`, опциональный release-badge).
 */
@Composable
private fun CatalogGridItem(
    release: Release,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AnixThemeTokens.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = release.title }
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(GRID_ITEM_VERTICAL_GAP),
    ) {
        Box {
            AnixPoster(
                url = release.posterUrl,
                contentDescription = null,
                width = null,
                modifier = Modifier.fillMaxWidth(),
            )
            val grade = release.grade
            if (grade != null) {
                TopEndRatingBadge(
                    grade = grade,
                    modifier = Modifier.align(Alignment.TopEnd).padding(RATING_BADGE_INSET),
                )
            }
        }

        Text(
            text = release.title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = GRID_TITLE_FONT_SIZE,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = GRID_TITLE_LINE_HEIGHT,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = GRID_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )

        val meta = releaseMeta(strings, release)
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = GRID_META_FONT_SIZE),
                color = colors.textSecondary58,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (release.status != ReleaseStatus.UNKNOWN) {
            ReleaseStatusBadge(status = release.status)
        }
    }
}

/** Опциональный бейдж статуса релиза под карточкой (мокап: padding 3/9, radius 20, 9.5px/700). */
@Composable
private fun ReleaseStatusBadge(
    status: ReleaseStatus,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = AnixThemeTokens.colors
    val label =
        when (status) {
            ReleaseStatus.FINISHED -> strings.releaseStatusFinished
            ReleaseStatus.ONGOING -> strings.releaseStatusOngoing
            ReleaseStatus.ANNOUNCE -> strings.releaseStatusAnnounce
            ReleaseStatus.UNKNOWN -> return
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(RELEASE_BADGE_RADIUS))
                .background(colors.overlay07)
                .border(RELEASE_BADGE_BORDER_WIDTH, colors.overlay16, RoundedCornerShape(RELEASE_BADGE_RADIUS))
                .padding(horizontal = RELEASE_BADGE_HORIZONTAL_PADDING, vertical = RELEASE_BADGE_VERTICAL_PADDING),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = RELEASE_BADGE_FONT_SIZE,
                    fontWeight = FontWeight.Bold,
                ),
            color = colors.textSecondary72,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("LongParameterList") // Пагинация + release-колбэки + статус-меню «⋮», см. CatalogResultsGrid.
@Composable
private fun CatalogList(
    items: List<Release>,
    isLoadingMore: Boolean,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    dimens: AnixDimens,
    onSetListStatus: (Int, ListStatus) -> Unit,
    onRemoveFromList: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    LazyColumn(
        // Liquid Glass (2026-09-11): нижний паддинг учитывает высоту плавающего таб-бара — этот
        // список рендерится и на Compact (где живёт bottom bar), и на Medium/Expanded (nav rail/
        // sidebar, `LocalGlassBottomInset` там `0.dp` по умолчанию, см. её KDoc) — читать её
        // безусловно безопасно на всех размерах окна. См. аналогичное место в `HomeScreen.kt`.
        contentPadding =
            PaddingValues(
                start = dimens.spaceM,
                end = dimens.spaceM,
                top = dimens.spaceM,
                bottom = dimens.spaceM + LocalGlassBottomInset.current,
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, release -> release.id }) { index, release ->
            if (index >= items.size - PREFETCH_THRESHOLD) onLoadMore()
            TitleCard(
                release = release,
                onClick = { onReleaseClick(release.id) },
                layout = TitleCardLayout.List,
                // Сверка Catalog (2026-09-08): мета-строка «N ep · ★ R» и синопсис под заголовком.
                meta = releaseMeta(strings, release),
                // Track A (сверка Compact-раскладки Catalog, 2026-09-04): макет рисует
                // мета-строку/синопсис под заголовком — `Release.description` уже несёт этот
                // текст, раньше subtitle сюда не пробрасывался вовсе.
                subtitle = release.description,
                trailing = {
                    ReleaseRowMenu(
                        release = release,
                        strings = strings,
                        onSetListStatus = { status -> onSetListStatus(release.id, status) },
                        onRemoveFromList = { onRemoveFromList(release.id) },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (isLoadingMore) {
            item { AnixLoadingState(Modifier.fillMaxWidth().padding(dimens.spaceM)) }
        }
    }
}

/** «24 ep · ★ 8.7» — формат макета (key catalogMetaFormat); null, когда данных нет. */
private fun releaseMeta(
    strings: Strings,
    release: Release,
): String? {
    val episodes = release.episodesReleased ?: release.episodesTotal ?: return null
    val grade = release.grade
    val rating =
        if (grade == null) {
            ""
        } else {
            val tenths = (grade * GRADE_TENTHS_SCALE).roundToInt()
            "${tenths / GRADE_TENTHS_SCALE}.${tenths % GRADE_TENTHS_SCALE}"
        }
    return strings.catalogMetaFormat(episodes, rating)
}

private const val GRADE_TENTHS_SCALE = 10

/**
 * Меню «⋮» строки результата (сверка Catalog, 2026-09-08): статусы списка через
 * [onSetListStatus]; «Убрать из списка» — когда релиз уже в списке ([Release.myListStatus]).
 * Три точки рисуются боксами (глифа more_vert нет в сабсете Material Symbols).
 */
@Composable
private fun ReleaseRowMenu(
    release: Release,
    strings: Strings,
    onSetListStatus: (ListStatus) -> Unit,
    onRemoveFromList: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Row(horizontalArrangement = Arrangement.spacedBy(MENU_DOT_GAP)) {
                repeat(MENU_DOT_COUNT) {
                    Box(
                        modifier =
                            Modifier
                                .size(MENU_DOT_SIZE)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    )
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ListStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.displayName(strings)) },
                    onClick = {
                        expanded = false
                        onSetListStatus(status)
                    },
                )
            }
            if (release.myListStatus != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(strings.libraryRemoveFromList) },
                    onClick = {
                        expanded = false
                        onRemoveFromList()
                    },
                )
            }
        }
    }
}

private const val MENU_DOT_COUNT = 3
private val MENU_DOT_SIZE = 3.dp
private val MENU_DOT_GAP = 2.dp

private const val PREFETCH_THRESHOLD = 6

private const val GRID_COLUMNS = 5
private val GRID_GAP = 16.dp
private val GRID_ITEM_VERTICAL_GAP = 7.dp
private val RATING_BADGE_INSET = 6.dp
private val GRID_TITLE_FONT_SIZE = 12.sp
private val GRID_TITLE_LINE_HEIGHT = 15.sp
private const val GRID_TITLE_MAX_LINES = 2
private val GRID_META_FONT_SIZE = 10.5.sp
private val RELEASE_BADGE_RADIUS = 20.dp
private val RELEASE_BADGE_BORDER_WIDTH = 1.dp
private val RELEASE_BADGE_HORIZONTAL_PADDING = 9.dp
private val RELEASE_BADGE_VERTICAL_PADDING = 3.dp
private val RELEASE_BADGE_FONT_SIZE = 9.5.sp
