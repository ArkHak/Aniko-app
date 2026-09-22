package com.aniko.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.data.librarypreferences.LibraryViewMode
import com.aniko.data.paging.PagingState
import com.aniko.model.Release
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.component.AnixAsyncImage
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ExpandedScreenTitle
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Desktop (Expanded) layout for My Lists (мокап `isLists`, P13.T7): заголовок с кнопкой вида
 * «Список» ↔ «Сетка постеров», обёртка чипов вкладок и под ними сетка — 3-колоночные горизонтальные
 * карточки ([LibraryExpandedListCard]) в виде «Список» либо плитки-постеры ([LibraryPosterGrid]) в
 * виде «Сетка постеров». Вынесено из `LibraryScreen.kt` в отдельный файл (detekt
 * `TooManyFunctions`) — раскладки Compact/Medium этот файл не трогает, ими по-прежнему владеет
 * `LibraryScreen.kt`.
 *
 * Заголовок и чипы стоят над сеткой и не прокручиваются вместе с ней (как тулбар и чипы на
 * Compact/Medium): сетка — ленивая [LibraryItemsGrid] с собственным скроллом и подгрузкой страниц,
 * а вложить ленивый контейнер в `verticalScroll` нельзя (бесконечная высота), поэтому прежний
 * «скроллится вся страница целиком» заменён на «скроллится только список».
 */
@Composable
internal fun LibraryExpandedContent(
    pagingState: PagingState<Release>,
    viewMode: LibraryViewMode,
    actions: LibraryItemActions,
    onViewModeClick: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    // Заголовок вынесен из горизонтального паддинга колонки: ExpandedScreenTitle сам задаёт свой
    // отступ (единый паттерн для всех Expanded-заголовков, см. его KDoc), поэтому чипы получают
    // собственный горизонтальный паддинг, а сетка — свой через contentPadding.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .widthIn(max = dimens.contentMaxWidth),
    ) {
        LibraryExpandedHeader(
            title = strings.navLibrary,
            viewMode = viewMode,
            onViewModeClick = onViewModeClick,
        )

        LibraryExpandedChips(
            selectedTab = actions.tab,
            onTabSelected = onTabSelected,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibraryExpandedListState(
                pagingState = pagingState,
                viewMode = viewMode,
                actions = actions,
            )
        }
    }
}

/**
 * Заголовок «My Lists» ([ExpandedScreenTitle]) + кнопка вида «Список» ↔ «Сетка постеров»
 * ([LibraryViewModeButton], квадрат 32×32 radius 9 на фоне `overlay06`) — desktop-ряд мокапа.
 * Кнопка — 48×48 (зона нажатия) с видимым квадратом по центру, поэтому её `end`-паддинг —
 * [AnixThemeTokens.dimens.spaceS] (16 − 8 запаса на зону нажатия): правый край видимого квадрата
 * совпадает с правым краем контента ниже (`spaceM`) — левый край заголовка уже выровнен
 * автоматически встроенным отступом [ExpandedScreenTitle].
 */
@Composable
private fun LibraryExpandedHeader(
    title: String,
    viewMode: LibraryViewMode,
    onViewModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Row(
        modifier = modifier.fillMaxWidth().padding(end = dimens.spaceS),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpandedScreenTitle(text = title)

        LibraryViewModeButton(
            currentMode = viewMode,
            onClick = onViewModeClick,
            visualSize = LIBRARY_VIEW_MODE_BUTTON_SIZE_EXPANDED,
            cornerRadius = LIBRARY_EXPANDED_BUTTON_CORNER,
        )
    }
}

/**
 * Ряд чипов вкладок для Expanded: wrap, padding 8/16, radius 20, 12.5sp/600, бордер
 * `overlay09`; активный — акцентная плашка `primary.copy(alpha = 0.22f)` + бордер
 * `primary.copy(alpha = 0.55f)`.
 */
@Composable
private fun LibraryExpandedChips(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = AnixThemeTokens.colors
    val primary = MaterialTheme.colorScheme.primary

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryTab.all.forEach { tab ->
            val selected = tab == selectedTab
            val label =
                when (tab) {
                    is LibraryTab.Status -> tab.status.displayName(strings)
                    LibraryTab.Favorites -> strings.libraryTabFavorites
                    LibraryTab.History -> strings.libraryTabHistory
                }
            FilterChip(
                selected = selected,
                onClick = { onTabSelected(tab) },
                label = {
                    Text(
                        text = label,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = primary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = colors.overlay09,
                        selectedBorderColor = primary.copy(alpha = 0.55f),
                    ),
                modifier = Modifier.height(32.dp),
            )
        }
    }
}

/**
 * Ветвление error/loading/empty/данные для [LibraryExpandedContent] — вынесено отдельно, чтобы
 * сама координирующая функция не разрасталась (detekt `LongMethod`). Данные — по виду [viewMode]:
 * «Список» — [LibraryExpandedCards], «Сетка постеров» — [LibraryPosterGrid].
 */
@Composable
private fun LibraryExpandedListState(
    pagingState: PagingState<Release>,
    viewMode: LibraryViewMode,
    actions: LibraryItemActions,
) {
    val strings = LocalStrings.current
    val tab = actions.tab
    val items = pagingState.items

    when {
        pagingState.error != null && items.isEmpty() ->
            AnixErrorState(
                message = strings.libraryLoadError,
                onRetry = { actions.viewModel.retry(tab) },
                modifier = Modifier.fillMaxWidth().padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
            )

        items.isEmpty() && (pagingState.isLoading || pagingState.isRefreshing) ->
            AnixLoadingState(
                modifier = Modifier.fillMaxWidth().padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
            )

        pagingState.isEmpty ->
            LibraryExpandedEmpty(message = tab.emptyMessage(strings))

        viewMode == LibraryViewMode.List -> LibraryExpandedCards(pagingState = pagingState, actions = actions)

        else -> LibraryPosterGrid(pagingState = pagingState, windowSize = AnixWindowSize.Expanded, actions = actions)
    }
}

/**
 * Вид «Список» на Expanded: 3 колонки (gap 14) горизонтальных карточек [LibraryExpandedListCard].
 * Ленивая сетка с равными колонками: последняя неполная строка не растягивается на всю ширину,
 * как растягивалась при прежней раскладке `FlowRow` с `weight(1f)`.
 */
@Composable
private fun LibraryExpandedCards(
    pagingState: PagingState<Release>,
    actions: LibraryItemActions,
) {
    val dimens = AnixThemeTokens.dimens

    LibraryItemsGrid(
        pagingState = pagingState,
        layout =
            LibraryGridLayout(
                columns = GridCells.Fixed(LIBRARY_EXPANDED_GRID_COLUMNS),
                gap = LIBRARY_EXPANDED_GAP,
                contentPadding = libraryContentPadding(horizontal = dimens.spaceM),
            ),
        actions = actions,
        modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.LIST_CONTENT),
    ) { release ->
        LibraryExpandedListCard(release = release, actions = actions)
    }
}

/**
 * Пустое состояние списка на Expanded: padding 60dp, по центру, 13sp `textSecondary55`.
 */
@Composable
private fun LibraryExpandedEmpty(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = AnixThemeTokens.colors

    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textSecondary55,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Ячейка 3-колоночной сетки Expanded: row padding 12, radius 14, bg `overlay045`,
 * border `overlay07`; арт 52×52 radius 11; имя 13sp/600 Manrope; подпись прогресса
 * 11sp `textSecondary60`; gap 12. Арт/текстовый столбец вынесены в [LibraryExpandedCardArt]/
 * [LibraryExpandedCardInfo] — иначе тело превышает detekt `LongMethod`.
 */
@Composable
private fun LibraryExpandedListCard(
    release: Release,
    actions: LibraryItemActions,
    modifier: Modifier = Modifier,
) {
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    val cardShape = RoundedCornerShape(14.dp)
    val progressLabel =
        strings.progressEpisodesOf(
            release.lastViewEpisode ?: 0,
            release.episodesTotal ?: 0,
        )

    Box(
        modifier =
            modifier
                .clip(cardShape)
                .background(colors.overlay045, cardShape)
                .border(1.dp, colors.overlay07, cardShape),
    ) {
        LibraryExpandedCardRow(
            release = release,
            progressLabel = progressLabel,
            onReleaseClick = actions.onReleaseClick,
            onMenuReleaseIdChange = actions.onMenuReleaseIdChange,
        )

        LibraryReleaseContextMenu(release = release, actions = actions)
    }
}

/**
 * Кликабельная строка ячейки [LibraryExpandedListCard] — вынесена отдельным composable (detekt
 * `LongMethod` на карточке): семантику/клики/раскладку арт+текст держит она, а карточка отвечает
 * только за контейнер и контекстное меню.
 */
@Suppress("LongParameterList") // Ровно параметры, нужные строке: релиз + подпись прогресса + 2 колбэка.
@Composable
private fun LibraryExpandedCardRow(
    release: Release,
    progressLabel: String,
    onReleaseClick: (Int) -> Unit,
    onMenuReleaseIdChange: (Int?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onReleaseClick(release.id) },
                    onLongClick = { onMenuReleaseIdChange(release.id) },
                ).semantics(mergeDescendants = true) {
                    contentDescription = "${release.title}, $progressLabel"
                }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryExpandedCardArt(posterUrl = release.posterUrl)
        LibraryExpandedCardInfo(
            title = release.title,
            progressLabel = progressLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Арт ячейки [LibraryExpandedListCard]: 52×52 radius 11. */
@Composable
private fun LibraryExpandedCardArt(
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(52.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AnixAsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Текстовый столбец ячейки [LibraryExpandedListCard]: имя 13sp/600 + подпись прогресса 11sp. */
@Composable
private fun LibraryExpandedCardInfo(
    title: String,
    progressLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = AnixThemeTokens.colors

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = colors.textSecondary60,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Число колонок вида «Список» на Expanded ([LibraryExpandedCards]) — точное значение desktop-мокапа. */
private const val LIBRARY_EXPANDED_GRID_COLUMNS = 3

/** Радиус скругления кнопки вида в заголовке Expanded — desktop-мокап (radius 9). */
private val LIBRARY_EXPANDED_BUTTON_CORNER = 9.dp

/** Вертикальный паддинг error/loading/empty-состояний Expanded — точное значение макета (60dp). */
private val EMPTY_STATE_VERTICAL_PADDING = 60.dp
