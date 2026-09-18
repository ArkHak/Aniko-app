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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.data.paging.PagingState
import com.aniko.model.Release
import com.aniko.ui.component.AnixAsyncImage
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ExpandedScreenTitle
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Desktop (Expanded) layout for My Lists (мокап `isLists`, P13.T7): заголовок со shuffle,
 * обёртка чипов вкладок и 3-колоночная сетка горизонтальных карточек. Вынесено из
 * `LibraryScreen.kt` в отдельный файл (detekt `TooManyFunctions`) — раскладки Compact/Medium
 * этот файл не трогает, ими по-прежнему владеет `LibraryScreen.kt`.
 */
@Suppress("LongParameterList") // Координирующий блок: стейт пагинации + вкладка + меню + колбэки + ViewModel.
@Composable
internal fun LibraryExpandedContent(
    pagingState: PagingState<Release>,
    selectedTab: LibraryTab,
    isShuffled: Boolean,
    menuReleaseId: Int?,
    onMenuReleaseIdChange: (Int?) -> Unit,
    onReleaseClick: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    viewModel: LibraryViewModel,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    // Заголовок вынесен из общего Arrangement.spacedBy(dimens.spaceM)/горизонтального паддинга
    // колонки: ExpandedScreenTitle сам задаёт свой отступ (единый паттерн для всех
    // Expanded-заголовков, см. его KDoc), поэтому остаток контента (чипы + список) собран в
    // отдельную вложенную колонку со своим горизонтальным паддингом — иначе отступ бы удвоился
    // на границе заголовка и совпал бы с ним случайно только по горизонтали.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .widthIn(max = dimens.contentMaxWidth)
                .verticalScroll(rememberScrollState()),
    ) {
        LibraryExpandedHeader(
            title = strings.navLibrary,
            isShuffled = isShuffled,
            onShuffleClick = onShuffleClick,
        )

        Column(
            modifier = Modifier.padding(horizontal = dimens.spaceM).padding(bottom = dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            LibraryExpandedChips(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )

            LibraryExpandedListState(
                pagingState = pagingState,
                selectedTab = selectedTab,
                menuReleaseId = menuReleaseId,
                onMenuReleaseIdChange = onMenuReleaseIdChange,
                onReleaseClick = onReleaseClick,
                viewModel = viewModel,
            )
        }
    }
}

/**
 * Заголовок «My Lists» ([ExpandedScreenTitle]) + квадратная кнопка shuffle 32×32 radius 9
 * на фоне `overlay06` — тот же desktop-ряд мокапа. Кнопка получает собственный `end`-паддинг
 * [AnixThemeTokens.dimens.spaceM], чтобы её правый край совпадал с правым краем контента ниже
 * (тот же токен, только применённый на вложенной колонке в [LibraryExpandedContent]) — левый край
 * заголовка уже выровнен автоматически встроенным отступом [ExpandedScreenTitle].
 */
@Composable
private fun LibraryExpandedHeader(
    title: String,
    isShuffled: Boolean,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = AnixThemeTokens.colors
    val dimens = AnixThemeTokens.dimens

    Row(
        modifier = modifier.fillMaxWidth().padding(end = dimens.spaceM),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpandedScreenTitle(text = title)

        IconButton(
            onClick = onShuffleClick,
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.overlay06)
                    .semantics { contentDescription = strings.libraryShuffle },
        ) {
            AnixIcon(
                name = "shuffle",
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                filled = true,
                tint = if (isShuffled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
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
 * сама координирующая функция не разрасталась (detekt `LongMethod`).
 */
@Suppress("LongParameterList") // Тот же координирующий набор параметров, что у
// LibraryExpandedContent, минус header/chips-специфика.
@Composable
private fun LibraryExpandedListState(
    pagingState: PagingState<Release>,
    selectedTab: LibraryTab,
    menuReleaseId: Int?,
    onMenuReleaseIdChange: (Int?) -> Unit,
    onReleaseClick: (Int) -> Unit,
    viewModel: LibraryViewModel,
) {
    val strings = LocalStrings.current
    val items = pagingState.items

    when {
        pagingState.error != null && items.isEmpty() ->
            AnixErrorState(
                message = strings.libraryLoadError,
                onRetry = { viewModel.retry(selectedTab) },
                modifier = Modifier.fillMaxWidth().padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
            )

        items.isEmpty() && (pagingState.isLoading || pagingState.isRefreshing) ->
            AnixLoadingState(
                modifier = Modifier.fillMaxWidth().padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
            )

        pagingState.isEmpty ->
            LibraryExpandedEmpty(message = selectedTab.emptyMessage(strings))

        else ->
            LibraryExpandedGrid(
                items = items,
                pagingState = pagingState,
                selectedTab = selectedTab,
                menuReleaseId = menuReleaseId,
                onMenuReleaseIdChange = onMenuReleaseIdChange,
                onReleaseClick = onReleaseClick,
                viewModel = viewModel,
            )
    }
}

/**
 * 3-колоночная сетка (gap 14) горизонтальных карточек — единственная реальная ветка данных
 * [LibraryExpandedListState].
 */
@Suppress("LongParameterList")
@Composable
private fun LibraryExpandedGrid(
    items: List<Release>,
    pagingState: PagingState<Release>,
    selectedTab: LibraryTab,
    menuReleaseId: Int?,
    onMenuReleaseIdChange: (Int?) -> Unit,
    onReleaseClick: (Int) -> Unit,
    viewModel: LibraryViewModel,
) {
    FlowRow(
        maxItemsInEachRow = LIBRARY_EXPANDED_GRID_COLUMNS,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.forEachIndexed { index, release ->
            if (index >= items.size - LIBRARY_PREFETCH_THRESHOLD) {
                viewModel.loadMore(selectedTab)
            }
            LibraryExpandedListCard(
                release = release,
                selectedTab = selectedTab,
                menuReleaseId = menuReleaseId,
                onMenuReleaseIdChange = onMenuReleaseIdChange,
                onReleaseClick = onReleaseClick,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
        if (pagingState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnixLoadingState()
            }
        }
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
@Suppress("LongParameterList")
@Composable
private fun LibraryExpandedListCard(
    release: Release,
    selectedTab: LibraryTab,
    menuReleaseId: Int?,
    onMenuReleaseIdChange: (Int?) -> Unit,
    onReleaseClick: (Int) -> Unit,
    viewModel: LibraryViewModel,
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
            onReleaseClick = onReleaseClick,
            onMenuReleaseIdChange = onMenuReleaseIdChange,
        )

        LibraryContextMenu(
            expanded = menuReleaseId == release.id,
            release = release,
            tab = selectedTab,
            onDismiss = { onMenuReleaseIdChange(null) },
            onChangeStatus = { status ->
                viewModel.changeStatus(release, status)
                onMenuReleaseIdChange(null)
            },
            onToggleFavorite = {
                viewModel.toggleFavorite(release)
                onMenuReleaseIdChange(null)
            },
            onRemoveFromList = { status ->
                viewModel.removeFromList(release, status)
                onMenuReleaseIdChange(null)
            },
            onRemoveFromHistory = {
                viewModel.removeFromHistory(release)
                onMenuReleaseIdChange(null)
            },
        )
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

/** Число колонок сетки [LibraryExpandedGrid] — точное значение desktop-мокапа. */
private const val LIBRARY_EXPANDED_GRID_COLUMNS = 3

/** Вертикальный паддинг error/loading/empty-состояний Expanded — точное значение макета (60dp). */
private val EMPTY_STATE_VERTICAL_PADDING = 60.dp
