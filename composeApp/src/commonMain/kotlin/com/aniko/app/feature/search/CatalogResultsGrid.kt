package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.app.ui.toContentState
import com.aniko.data.paging.PagingState
import com.aniko.model.Release
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.TitleCard
import com.aniko.ui.component.TitleCardLayout
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixDimens
import com.aniko.ui.theme.AnixThemeTokens
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
@Suppress("LongParameterList") // Координирующий блок: пагинированное состояние + режим + 3 колбэка.
@Composable
fun CatalogResultsGrid(
    pagingState: PagingState<Release>,
    viewMode: CatalogViewMode,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
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
                CatalogList(items, contentState.isLoading, onReleaseClick, onLoadMore, dimens)
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

@Composable
private fun CatalogList(
    items: List<Release>,
    isLoadingMore: Boolean,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    dimens: AnixDimens,
) {
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
                // Track A (сверка Compact-раскладки Catalog, 2026-09-04): макет рисует
                // мета-строку/синопсис под заголовком — `Release.description` уже несёт этот
                // текст, раньше subtitle сюда не пробрасывался вовсе.
                subtitle = release.description,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (isLoadingMore) {
            item { AnixLoadingState(Modifier.fillMaxWidth().padding(dimens.spaceM)) }
        }
    }
}

private const val PREFETCH_THRESHOLD = 6
