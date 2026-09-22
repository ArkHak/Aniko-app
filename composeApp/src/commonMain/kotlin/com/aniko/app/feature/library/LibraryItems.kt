package com.aniko.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.data.paging.PagingState
import com.aniko.model.Release
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalGlassBottomInset
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ListStatusChip
import com.aniko.ui.component.ListStatusChipStyle
import com.aniko.ui.component.ProgressRow
import com.aniko.ui.component.TitleCard
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Всё, что нужно ячейке экрана «Мои списки» от экрана-владельца: вкладка, за которую она
 * отвечает, состояние контекстного меню (id релиза, для которого оно открыто) и действия над
 * релизом — клик, подгрузка следующей страницы и мутации через [viewModel]. Один объект вместо
 * пяти параметров у каждой ячейки каждого из видов (строки, плитки-постеры, карточки Expanded).
 */
internal class LibraryItemActions(
    val tab: LibraryTab,
    val menuReleaseId: Int?,
    val onMenuReleaseIdChange: (Int?) -> Unit,
    val onReleaseClick: (Int) -> Unit,
    val viewModel: LibraryViewModel,
) {
    /** Подгружает следующую страницу вкладки [tab]; пагинатор сам игнорирует лишние вызовы. */
    fun loadMore() = viewModel.loadMore(tab)
}

/** Раскладка ленивой сетки: сколько колонок, зазор между ячейками и поля вокруг содержимого. */
internal class LibraryGridLayout(
    val columns: GridCells,
    val gap: Dp,
    val contentPadding: PaddingValues,
)

/**
 * Общий ленивый контейнер всех видов экрана «Мои списки» — единственное место, где живёт
 * пагинация: как только на экран попадает ячейка из последних [LIBRARY_PREFETCH_THRESHOLD],
 * запрашивается следующая страница ([LibraryItemActions.loadMore]), а пока страница грузится,
 * внизу отдельной строкой на всю ширину рисуется индикатор. Вид ячейки задаёт [itemContent],
 * число колонок — [layout]: «Список» на Compact — одна колонка, на Medium — несколько, «Сетка
 * постеров» и карточки Expanded — по ширине окна.
 *
 * Обычная [LazyVerticalGrid] (одна колонка ничем не хуже `LazyColumn`), а не `FlowRow` в
 * `verticalScroll`: композируются только видимые ячейки, а не весь список сразу.
 */
@Composable
internal fun LibraryItemsGrid(
    pagingState: PagingState<Release>,
    layout: LibraryGridLayout,
    actions: LibraryItemActions,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Release) -> Unit,
) {
    LazyVerticalGrid(
        columns = layout.columns,
        contentPadding = layout.contentPadding,
        horizontalArrangement = Arrangement.spacedBy(layout.gap),
        verticalArrangement = Arrangement.spacedBy(layout.gap),
        modifier = modifier,
    ) {
        itemsIndexed(pagingState.items, key = { _, release -> release.id }) { index, release ->
            if (index >= pagingState.items.size - LIBRARY_PREFETCH_THRESHOLD) {
                actions.loadMore()
            }
            itemContent(release)
        }

        if (pagingState.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnixLoadingState(modifier = Modifier.fillMaxWidth().padding(vertical = LIBRARY_LOADING_FOOTER_PADDING))
            }
        }
    }
}

/**
 * Вид «Список» на Compact/Medium: строки [ProgressRow] (арт + название + прогресс + чип статуса).
 * Колонки [GridCells.Adaptive] от [LIBRARY_ROW_MIN_WIDTH]: на телефоне это всегда одна колонка, на
 * Medium (планшет/узкое окно) — две, а не одна строка, растянутая на весь экран.
 */
@Composable
internal fun LibraryRows(
    pagingState: PagingState<Release>,
    actions: LibraryItemActions,
) {
    val dimens = AnixThemeTokens.dimens

    LibraryItemsGrid(
        pagingState = pagingState,
        layout =
            LibraryGridLayout(
                columns = GridCells.Adaptive(minSize = LIBRARY_ROW_MIN_WIDTH),
                gap = dimens.spaceS,
                contentPadding = libraryContentPadding(horizontal = dimens.spaceS),
            ),
        actions = actions,
        modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.LIST_CONTENT),
    ) { release ->
        LibraryRowCell(release = release, actions = actions)
    }
}

/**
 * Вид «Сетка постеров» на любом размере окна: плитки [TitleCard] с бейджами рейтинга/избранного/
 * статуса. Колонок столько, сколько влезает по [minPosterWidth] (Compact — `posterWidth`, шире —
 * `posterWidthL`); постер занимает всю ширину своей ячейки (см. [LibraryPosterCell]).
 */
@Composable
internal fun LibraryPosterGrid(
    pagingState: PagingState<Release>,
    windowSize: AnixWindowSize,
    actions: LibraryItemActions,
) {
    val dimens = AnixThemeTokens.dimens
    val expanded = windowSize == AnixWindowSize.Expanded
    val minPosterWidth = if (windowSize.isTwoPane) dimens.posterWidthL else dimens.posterWidth

    LibraryItemsGrid(
        pagingState = pagingState,
        layout =
            LibraryGridLayout(
                // Ячейка = постер + рамка контейнера (spaceXs с каждой стороны).
                columns = GridCells.Adaptive(minSize = minPosterWidth + dimens.spaceXs * 2),
                gap = if (expanded) LIBRARY_EXPANDED_GAP else dimens.spaceS,
                contentPadding = libraryContentPadding(horizontal = if (expanded) dimens.spaceM else dimens.spaceS),
            ),
        actions = actions,
        modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.GRID_CONTENT),
    ) { release ->
        LibraryPosterCell(release = release, actions = actions)
    }
}

/**
 * Поля вокруг содержимого ленивого контейнера: [horizontal] по бокам, `spaceM` сверху и снизу. Внизу
 * добавлена высота плавающего таб-бара ([LocalGlassBottomInset], Liquid Glass 2026-09-11) — на
 * Compact он перекрывает низ экрана, а на Medium/Expanded (рельса/сайдбар) инсет нулевой. Раньше его
 * читали только строки Compact, теперь он нужен и сетке постеров на телефоне.
 */
@Composable
internal fun libraryContentPadding(horizontal: Dp): PaddingValues {
    val dimens = AnixThemeTokens.dimens
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = dimens.spaceM,
        bottom = dimens.spaceM + LocalGlassBottomInset.current,
    )
}

/**
 * Ячейка вида «Список» на Compact/Medium (P13.T4): компактный постер + название + подпись прогресса
 * слева, статус-чип списка справа — тот же [ProgressRow], что рисует «Продолжить смотреть» на Home, только
 * с [ListStatusChip] в `trailing`. Долгое нажатие открывает то же [LibraryContextMenu], что и у
 * остальных видов, — меняется только внешний вид, не логика.
 *
 * У истории и части «избранного» `release.myListStatus` может быть `null` (релиз не состоит ни в
 * одном статусном списке) — тогда чип справа просто не рисуется, а не подставляется выдуманный статус.
 */
@Composable
private fun LibraryRowCell(
    release: Release,
    actions: LibraryItemActions,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    Box {
        ProgressRow(
            posterUrl = release.posterUrl,
            title = release.title,
            watchedEpisodes = release.lastViewEpisode,
            totalEpisodes = release.episodesTotal,
            onClick = { actions.onReleaseClick(release.id) },
            onLongClick = { actions.onMenuReleaseIdChange(release.id) },
            trailing = {
                release.myListStatus?.let { status ->
                    ListStatusChip(status, style = ListStatusChipStyle.Full)
                }
            },
            // Track A (сверка Compact-раскладки, 2026-09-04): макет оборачивает строку "Мои списки" в
            // контейнер w045/w07/radius12 — снаружи через modifier, сам ProgressRow (общий и на Home
            // Continue Watching) не тронут.
            modifier =
                Modifier
                    .clip(RoundedCornerShape(dimens.cornerM))
                    .background(colors.overlay045)
                    .border(LIBRARY_CARD_BORDER_WIDTH, colors.overlay07, RoundedCornerShape(dimens.cornerM)),
        )
        LibraryReleaseContextMenu(release = release, actions = actions)
    }
}

/**
 * Ячейка вида «Сетка постеров» (Compact/Medium/Expanded): плитка [TitleCard] в том же
 * overlay-контейнере (`overlay045`/`overlay07`/`cornerM`, `spaceXs` внутри), что и строки.
 *
 * Постер занимает всю ширину ячейки: [TitleCard] умеет только фиксированную ширину, поэтому ей
 * отдаётся заведомо большая (`contentMaxWidth`) вместе с `fillMaxWidth()` — внешний размер
 * ячейки зажимает внутренний `width(...)` (тот же приём, что у `ProgressRow`). Иначе на телефоне
 * постер 120.dp сидел бы в ячейке ~180.dp с пустым полем справа.
 */
@Composable
private fun LibraryPosterCell(
    release: Release,
    actions: LibraryItemActions,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val cardShape = RoundedCornerShape(dimens.cornerM)

    Box {
        Box(
            modifier =
                Modifier
                    .clip(cardShape)
                    .background(colors.overlay045, cardShape)
                    .border(LIBRARY_CARD_BORDER_WIDTH, colors.overlay07, cardShape)
                    .padding(dimens.spaceXs),
        ) {
            TitleCard(
                release = release,
                onClick = { actions.onReleaseClick(release.id) },
                modifier = Modifier.fillMaxWidth(),
                onLongClick = { actions.onMenuReleaseIdChange(release.id) },
                posterWidth = dimens.contentMaxWidth,
            )
        }
        LibraryReleaseContextMenu(release = release, actions = actions)
    }
}

/**
 * [LibraryContextMenu] для одной карточки: смена статуса/избранное/удаление уходят во ViewModel
 * ([LibraryItemActions.viewModel]), после действия меню закрывается. Общая для всех видов и всех
 * размеров окна — долгое нажатие работает везде одинаково.
 */
@Composable
internal fun LibraryReleaseContextMenu(
    release: Release,
    actions: LibraryItemActions,
) {
    val viewModel = actions.viewModel
    LibraryContextMenu(
        expanded = actions.menuReleaseId == release.id,
        release = release,
        tab = actions.tab,
        onDismiss = { actions.onMenuReleaseIdChange(null) },
        onChangeStatus = { status ->
            viewModel.changeStatus(release, status)
            actions.onMenuReleaseIdChange(null)
        },
        onToggleFavorite = {
            viewModel.toggleFavorite(release)
            actions.onMenuReleaseIdChange(null)
        },
        onRemoveFromList = { status ->
            viewModel.removeFromList(release, status)
            actions.onMenuReleaseIdChange(null)
        },
        onRemoveFromHistory = {
            viewModel.removeFromHistory(release)
            actions.onMenuReleaseIdChange(null)
        },
    )
}

/** Минимальная ширина колонки вида «Список»: на телефоне (< 600.dp) всегда одна колонка, на Medium — две. */
private val LIBRARY_ROW_MIN_WIDTH = 300.dp

/** Вертикальный отступ индикатора подгрузки следующей страницы под последней строкой сетки. */
private val LIBRARY_LOADING_FOOTER_PADDING = 8.dp

/** Толщина рамки overlay-контейнера ячейки (строки и плитки). */
private val LIBRARY_CARD_BORDER_WIDTH = 1.dp

/** Зазор между ячейками на Expanded — точное значение desktop-мокапа (gap 14). */
internal val LIBRARY_EXPANDED_GAP = 14.dp
