package com.aniko.app.feature.collections

import androidx.lifecycle.viewModelScope
import com.aniko.app.mvi.BaseViewModel
import com.aniko.data.paging.Paginator
import com.aniko.data.repository.CollectionRepository
import com.aniko.model.AnixCollection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** ViewModel экрана коллекций (P16.T16, MVP) — тот же MVI-каркас, что [com.aniko.app.feature.
 *  feed.FeedViewModel]. */
class CollectionsViewModel(
    private val collectionRepository: CollectionRepository,
) : BaseViewModel<CollectionsState, CollectionsIntent, CollectionsEffect>(initialState = CollectionsState()) {
    private var paginator: Paginator<AnixCollection>? = null
    private var pagingCollectJob: Job? = null
    private var started = false

    override suspend fun handleIntent(intent: CollectionsIntent) {
        when (intent) {
            CollectionsIntent.Load -> load()
            CollectionsIntent.LoadMore -> loadNextAndReportIfMoreFailed()
            CollectionsIntent.Retry -> paginator?.refresh()
        }
    }

    private suspend fun load() {
        if (started) return
        started = true
        startPaginator()
    }

    private suspend fun startPaginator() {
        pagingCollectJob?.cancel()
        val newPaginator = collectionRepository.collectionsPaginator()
        paginator = newPaginator
        pagingCollectJob =
            newPaginator.state
                .onEach { paging -> updateState { copy(paging = paging) } }
                .launchIn(viewModelScope)
        newPaginator.loadNext()
    }

    private suspend fun loadNextAndReportIfMoreFailed() {
        val currentPaginator = paginator ?: return
        currentPaginator.loadNext()
        val pagingState = currentPaginator.state.value
        val error = pagingState.error
        if (error != null && pagingState.items.isNotEmpty()) {
            emitEffect(CollectionsEffect.ShowError(error))
        }
    }
}
