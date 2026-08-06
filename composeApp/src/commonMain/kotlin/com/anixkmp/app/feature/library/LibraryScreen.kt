package com.anixkmp.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anixkmp.model.ListStatus
import com.anixkmp.model.Release
import com.anixkmp.ui.component.AnixEmptyBox
import com.anixkmp.ui.component.AnixErrorBox
import com.anixkmp.ui.component.AnixLoadingBox
import com.anixkmp.ui.component.ReleaseCard
import com.anixkmp.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран «Мои списки»: 7 вкладок (5 статусов [ListStatus] + избранное + история), каждая —
 * своя сетка [ReleaseCard] поверх независимого пагинатора вкладки (см. [LibraryViewModel]).
 *
 * Смена статуса/избранного/удаление сделаны через долгое нажатие на карточке (см.
 * [ReleaseCard.onLongClick]) и контекстное меню — сознательно не свайп, так решено в плане:
 * свайп на сетке (в отличие от списка) неоднозначен и конфликтует с горизонтальным скроллом
 * `ScrollableTabRow` выше.
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = AnixThemeTokens.dimens
    val selectedTab = uiState.selectedTab

    // Id релиза, для которого сейчас открыто контекстное меню — не Release целиком, чтобы меню
    // не "залипало" на устаревших данных карточки, если пагинатор успел обновить список.
    var menuReleaseId by remember { mutableStateOf<Int?>(null) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = LibraryTab.all.indexOf(selectedTab).coerceAtLeast(0)) {
                LibraryTab.all.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.title()) },
                    )
                }
            }

            val pagingState = uiState.pagingState
            when {
                pagingState.error != null && pagingState.items.isEmpty() -> AnixErrorBox(
                    message = pagingState.error?.message ?: "Не удалось загрузить список",
                    onRetry = { viewModel.retry(selectedTab) },
                    modifier = Modifier.fillMaxSize(),
                )

                pagingState.items.isEmpty() && (pagingState.isLoading || pagingState.isRefreshing) -> AnixLoadingBox(
                    modifier = Modifier.fillMaxSize(),
                )

                pagingState.isEmpty -> AnixEmptyBox(
                    message = selectedTab.emptyMessage(),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = dimens.posterWidth),
                    contentPadding = PaddingValues(vertical = dimens.spaceM, horizontal = dimens.spaceS),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(pagingState.items, key = { _, release -> release.id }) { index, release ->
                        if (index >= pagingState.items.size - LIBRARY_PREFETCH_THRESHOLD) {
                            viewModel.loadMore(selectedTab)
                        }
                        Box {
                            ReleaseCard(
                                release = release,
                                onClick = { onReleaseClick(release.id) },
                                onLongClick = { menuReleaseId = release.id },
                            )
                            LibraryContextMenu(
                                expanded = menuReleaseId == release.id,
                                release = release,
                                tab = selectedTab,
                                onDismiss = { menuReleaseId = null },
                                onChangeStatus = { status ->
                                    viewModel.changeStatus(release, status)
                                    menuReleaseId = null
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(release)
                                    menuReleaseId = null
                                },
                                onRemoveFromList = { status ->
                                    viewModel.removeFromList(release, status)
                                    menuReleaseId = null
                                },
                                onRemoveFromHistory = {
                                    viewModel.removeFromHistory(release)
                                    menuReleaseId = null
                                },
                            )
                        }
                    }

                    if (pagingState.isLoading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AnixLoadingBox(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/**
 * Меню долгого нажатия: смена статуса (кроме текущего), тоггл избранного и удаление,
 * контекстно зависящее от активной вкладки — «из списка» на вкладках статусов, «из истории»
 * на истории. На вкладке избранного отдельного пункта удаления нет — эту роль играет тоггл
 * избранного (снятие галочки убирает карточку ровно так же).
 */
@Composable
private fun LibraryContextMenu(
    expanded: Boolean,
    release: Release,
    tab: LibraryTab,
    onDismiss: () -> Unit,
    onChangeStatus: (ListStatus) -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromList: (ListStatus) -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ListStatus.entries.filter { it != release.myListStatus }.forEach { status ->
            DropdownMenuItem(
                text = { Text("В «${status.displayName()}»") },
                onClick = { onChangeStatus(status) },
            )
        }
        DropdownMenuItem(
            text = { Text(if (release.isFavorite) "Убрать из избранного" else "Добавить в избранное") },
            onClick = onToggleFavorite,
        )
        when (tab) {
            is LibraryTab.Status -> DropdownMenuItem(
                text = { Text("Удалить из списка") },
                onClick = { onRemoveFromList(tab.status) },
            )

            LibraryTab.History -> DropdownMenuItem(
                text = { Text("Удалить из истории") },
                onClick = onRemoveFromHistory,
            )

            LibraryTab.Favorites -> Unit
        }
    }
}

private fun LibraryTab.title(): String = when (this) {
    is LibraryTab.Status -> status.displayName()
    LibraryTab.Favorites -> "Избранное"
    LibraryTab.History -> "История"
}

private fun LibraryTab.emptyMessage(): String = when (this) {
    is LibraryTab.Status -> "Список пуст"
    LibraryTab.Favorites -> "В избранном пока ничего нет"
    LibraryTab.History -> "История просмотра пуста"
}

private fun ListStatus.displayName(): String = when (this) {
    ListStatus.WATCHING -> "Смотрю"
    ListStatus.PLANNED -> "В планах"
    ListStatus.COMPLETED -> "Просмотрено"
    ListStatus.ON_HOLD -> "Отложено"
    ListStatus.DROPPED -> "Брошено"
}

private const val LIBRARY_PREFETCH_THRESHOLD = 6
