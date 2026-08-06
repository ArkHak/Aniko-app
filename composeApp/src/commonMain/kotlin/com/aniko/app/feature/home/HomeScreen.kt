package com.aniko.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.data.paging.PagingState
import com.aniko.model.Release
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.AnixPoster
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Главный экран: секции «Продолжить смотреть» и «Рекомендации».
 *
 * Обе секции — горизонтальные ленты постеров поверх независимых пагинаторов
 * ([HomeViewModel.watchingState]/[HomeViewModel.recommendationsState]). Пустая секция (нет
 * ошибки, просто нечего показывать — например «Продолжить смотреть» у нового аккаунта) не
 * рисует пустой блок вовсе, а не [com.aniko.ui.component.AnixEmptyBox] на весь экран:
 * на главном экране могут быть данные хотя бы в одной секции.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val watchingState by viewModel.watchingState.collectAsStateWithLifecycle()
    val recommendationsState by viewModel.recommendationsState.collectAsStateWithLifecycle()
    val dimens = AnixThemeTokens.dimens

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
        ) {
            ReleaseSection(
                title = "Продолжить смотреть",
                state = watchingState,
                onReleaseClick = onReleaseClick,
                onRetry = viewModel::retryWatching,
                onLoadMore = viewModel::loadMoreWatching,
            )
            ReleaseSection(
                title = "Рекомендации",
                state = recommendationsState,
                onReleaseClick = onReleaseClick,
                onRetry = viewModel::retryRecommendations,
                onLoadMore = viewModel::loadMoreRecommendations,
            )
        }
    }
}

@Composable
private fun ReleaseSection(
    title: String,
    state: PagingState<Release>,
    onReleaseClick: (Int) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens

    // Первая загрузка ещё не начиналась/идёт и элементов нет — секция ничего не занимает,
    // пока стейт не определится (короткий "мигающий" лоадер на весь ряд предпочтительнее
    // пустоты, поэтому isLoading тоже рисует лоадер, а не пропускает секцию).
    if (state.items.isEmpty() && !state.isLoading && state.error == null) return

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        val error = state.error
        when {
            error != null && state.items.isEmpty() -> AnixErrorBox(
                message = error.message ?: "Не удалось загрузить",
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth().height(SECTION_HEIGHT),
            )

            state.items.isEmpty() && state.isLoading -> AnixLoadingBox(
                modifier = Modifier.fillMaxWidth().height(SECTION_HEIGHT),
            )

            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                contentPadding = PaddingValues(horizontal = dimens.spaceM),
            ) {
                itemsIndexed(state.items, key = { _, release -> release.id }) { index, release ->
                    if (index >= state.items.size - PREFETCH_THRESHOLD) {
                        onLoadMore()
                    }
                    AnixPoster(
                        url = release.posterUrl,
                        contentDescription = release.title,
                        modifier = Modifier.clickable { onReleaseClick(release.id) },
                    )
                }
            }
        }
    }
}

private const val PREFETCH_THRESHOLD = 4
private val SECTION_HEIGHT = 180.dp
