package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.ui.component.AnixEmptyBox
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.ReleaseCard
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/** Экран поиска релизов: поле запроса (с debounce во ViewModel) + сетка постеров. */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val pagingState by viewModel.pagingState.collectAsStateWithLifecycle()
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(dimens.spaceM)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(strings.searchPlaceholder) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Text("✕")
                        }
                    }
                },
            )

            when {
                query.isBlank() ->
                    AnixEmptyBox(
                        message = strings.searchEmptyPrompt,
                        modifier = Modifier.fillMaxSize(),
                    )

                pagingState.error != null && pagingState.items.isEmpty() ->
                    AnixErrorBox(
                        // P2.T10: не показываем `error.message` напрямую — это текст исключения
                        // AnixError (технический, на английском, только для логов/debug), не
                        // локализованный UI-текст. Всегда локализованный fallback.
                        message = strings.searchError,
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )

                pagingState.items.isEmpty() && pagingState.isLoading ->
                    AnixLoadingBox(
                        modifier = Modifier.fillMaxSize(),
                    )

                pagingState.isEmpty ->
                    AnixEmptyBox(
                        message = strings.searchNoResults,
                        modifier = Modifier.fillMaxSize(),
                    )

                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = dimens.posterWidth),
                        contentPadding = PaddingValues(vertical = dimens.spaceM),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(pagingState.items, key = { _, release -> release.id }) { index, release ->
                            if (index >= pagingState.items.size - SEARCH_PREFETCH_THRESHOLD) {
                                viewModel.loadMore()
                            }
                            ReleaseCard(
                                release = release,
                                onClick = { onReleaseClick(release.id) },
                            )
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

private const val SEARCH_PREFETCH_THRESHOLD = 6
