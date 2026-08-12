package com.aniko.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.theme.AnixDimens
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Горизонтальная лента постеров: заголовок секции (+опциональное действие справа, например
 * "Все") над [LazyRow]. Замена приватной `HomeScreen.ReleaseSection` (Фаза 6, P6.T5) — рельса
 * не привязана к `Release`/`PagingState`, элемент рисует вызывающая сторона через слот [item],
 * а состояние списка приходит уже в виде [AnixContentState] (мост из `PagingState` — в
 * `composeApp`, см. `PagingStateAdapter.kt`).
 *
 * Пустая секция без ошибки и без [emptyMessage] не рисует вообще ничего (ни заголовок, ни
 * ряд) — сохраняет поведение исходной `ReleaseSection`: на главном экране могут быть данные
 * хотя бы в одной секции, и пустая ничем не должна "мигать".
 */
@Suppress("LongParameterList") // Публичная сигнатура зафиксирована брифом P6.T5: state/key/item
// обязательны, остальное — опциональные точки расширения (пустое сообщение/повтор/подгрузка/
// действие в заголовке/window size) с дефолтами. Группировка части параметров в конфиг-класс
// добавила бы косвенность ради обхода линта, а не ради читаемости вызывающего кода.
@Composable
fun <T : Any> HorizontalPosterRail(
    title: String,
    state: AnixContentState<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    prefetchThreshold: Int = 4,
    action: (@Composable () -> Unit)? = null,
    windowSize: AnixWindowSize = LocalAnixWindowSize.current,
    item: @Composable (T) -> Unit,
) {
    if (state.isEmpty && emptyMessage == null) return

    val dimens = AnixThemeTokens.dimens
    val placeholderHeight = railPlaceholderHeight(windowSize, dimens)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceM),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            action?.invoke()
        }

        when {
            state.isLoading && state.items.isEmpty() ->
                AnixLoadingState(modifier = Modifier.fillMaxWidth().height(placeholderHeight))

            state.errorMessage != null && state.items.isEmpty() ->
                AnixErrorState(
                    message = state.errorMessage,
                    modifier = Modifier.fillMaxWidth().height(placeholderHeight),
                    onRetry = onRetry,
                )

            state.isEmpty && emptyMessage != null ->
                AnixEmptyState(
                    message = emptyMessage,
                    modifier = Modifier.fillMaxWidth().height(placeholderHeight),
                )

            else -> {
                val listState = rememberLazyListState()

                if (onLoadMore != null) {
                    // Стандартный паттерн "подгрузить когда до конца списка < prefetchThreshold
                    // элементов": derivedStateOf сравнивает индекс последнего видимого элемента
                    // с общим количеством, LaunchedEffect дергает onLoadMore при пересечении
                    // порога (в т.ч. повторно при появлении новых элементов).
                    val shouldLoadMore by remember(state.items.size) {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            totalItems > 0 && lastVisibleIndex >= totalItems - prefetchThreshold
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) onLoadMore()
                    }
                }

                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                    contentPadding = PaddingValues(horizontal = dimens.spaceM),
                ) {
                    items(items = state.items, key = key) { value ->
                        item(value)
                    }
                }
            }
        }
    }
}

/**
 * Высота плейсхолдера loading/error/empty — под размер постера, который реально займёт эту
 * рельсу ([AnixDimens.posterWidthL] на Medium/Expanded, обычный [AnixDimens.posterWidth] на
 * Compact), чтобы секция не "прыгала" по высоте при переходе loading -> data.
 */
private fun railPlaceholderHeight(
    windowSize: AnixWindowSize,
    dimens: AnixDimens,
): Dp {
    val posterWidth = if (windowSize.isTwoPane) dimens.posterWidthL else dimens.posterWidth
    return posterWidth / dimens.posterAspectRatio
}
