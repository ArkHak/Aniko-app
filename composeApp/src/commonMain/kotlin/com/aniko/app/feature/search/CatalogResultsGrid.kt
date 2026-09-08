package com.aniko.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aniko.app.ui.toContentState
import com.aniko.data.paging.PagingState
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.TitleCard
import com.aniko.ui.component.TitleCardLayout
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixDimens
import com.aniko.ui.theme.AnixThemeTokens
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

/**
 * Сетка/список результатов Catalog/Search (P7.T3-T6) поверх [PagingState] — общая для обоих
 * режимов выдачи (поиск по строке / фильтр каталога, см. KDoc `SearchViewModel`), т.к. экран не
 * различает их источник. Мост `PagingState -> AnixContentState` — [toContentState]
 * (`composeApp/.../ui/PagingStateAdapter.kt`, Фаза 6).
 *
 * Пагинация "вперёд" — тот же паттерн, что в `LibraryScreen`/старой `SearchScreen`
 * (`itemsIndexed` + проверка индекса относительно конца списка на каждый видимый элемент, без
 * отдельного `LazyGridState`/`snapshotFlow`) — сознательно не переизобретается.
 */
@Suppress("LongParameterList") // Координирующий блок: пагинированное состояние + режим + 5 колбэков.
@Composable
fun CatalogResultsGrid(
    pagingState: PagingState<Release>,
    viewMode: CatalogViewMode,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit = { _, _ -> },
    onRemoveFromList: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
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
        when (viewMode) {
            CatalogViewMode.Grid ->
                CatalogGrid(items, contentState.isLoading, onReleaseClick, onLoadMore, dimens)
            CatalogViewMode.List ->
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

@Composable
private fun CatalogGrid(
    items: List<Release>,
    isLoadingMore: Boolean,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    dimens: AnixDimens,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = dimens.posterWidth),
        contentPadding = PaddingValues(dimens.spaceM),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItemsIndexed(items, key = { _, release -> release.id }) { index, release ->
            if (index >= items.size - PREFETCH_THRESHOLD) onLoadMore()
            TitleCard(release = release, onClick = { onReleaseClick(release.id) }, layout = TitleCardLayout.Grid)
        }

        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnixLoadingState(Modifier.fillMaxWidth().padding(dimens.spaceM))
            }
        }
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
        contentPadding = PaddingValues(dimens.spaceM),
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
