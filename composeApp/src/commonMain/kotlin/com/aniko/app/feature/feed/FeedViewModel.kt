package com.aniko.app.feature.feed

import androidx.lifecycle.viewModelScope
import com.aniko.app.mvi.BaseViewModel
import com.aniko.data.paging.Paginator
import com.aniko.data.repository.FeedRepository
import com.aniko.model.Article
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** ViewModel экрана ленты (P16.T3, MVP) — тот же MVI-каркас, что
 *  [com.aniko.app.feature.notifications.NotificationsViewModel], без бейджа непрочитанных
 *  (лента не отслеживает "виденность" в MVP). */
class FeedViewModel(
    private val feedRepository: FeedRepository,
) : BaseViewModel<FeedState, FeedIntent, FeedEffect>(initialState = FeedState()) {
    private var paginator: Paginator<Article>? = null
    private var pagingCollectJob: Job? = null
    private var started = false

    override suspend fun handleIntent(intent: FeedIntent) {
        when (intent) {
            FeedIntent.Load -> load()
            FeedIntent.LoadMore -> loadNextAndReportIfMoreFailed()
            FeedIntent.Retry -> paginator?.refresh()
        }
    }

    private suspend fun load() {
        if (started) return
        started = true
        startPaginator()
    }

    private suspend fun startPaginator() {
        pagingCollectJob?.cancel()
        val newPaginator = feedRepository.feedPaginator()
        paginator = newPaginator
        pagingCollectJob =
            newPaginator.state
                .onEach { paging -> updateState { copy(paging = paging) } }
                .launchIn(viewModelScope)
        newPaginator.loadNext()
    }

    /** См. KDoc `NotificationsViewModel.loadNextAndReportIfMoreFailed` — тот же смысл. */
    private suspend fun loadNextAndReportIfMoreFailed() {
        val currentPaginator = paginator ?: return
        currentPaginator.loadNext()
        val pagingState = currentPaginator.state.value
        val error = pagingState.error
        if (error != null && pagingState.items.isNotEmpty()) {
            emitEffect(FeedEffect.ShowError(error))
        }
    }
}
