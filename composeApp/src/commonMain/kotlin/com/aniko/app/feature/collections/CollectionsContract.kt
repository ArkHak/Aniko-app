package com.aniko.app.feature.collections

import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.data.paging.PagingState
import com.aniko.model.AnixCollection
import com.aniko.model.AnixError

/** Состояние экрана коллекций (P16.T16, MVP) — тот же объём решения, что у [com.aniko.app.
 *  feature.feed.FeedState]: один пагинированный список, без сортировки/фильтров. */
data class CollectionsState(
    val paging: PagingState<AnixCollection> = PagingState(),
) : UiState

sealed interface CollectionsIntent : UiIntent {
    data object Load : CollectionsIntent

    data object LoadMore : CollectionsIntent

    data object Retry : CollectionsIntent
}

sealed interface CollectionsEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : CollectionsEffect
}
