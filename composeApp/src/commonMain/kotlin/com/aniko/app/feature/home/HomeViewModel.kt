package com.aniko.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.paging.PagingState
import com.aniko.data.repository.ReleaseRepository
import com.aniko.model.Release
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel главного экрана: секции «Продолжить смотреть» и «Рекомендации».
 *
 * Оба списка — независимые [com.aniko.data.paging.Paginator] из `ReleaseRepository`, каждый
 * грузит свою первую страницу сразу при создании ViewModel (см. `init`). `HomeScreen` сам решает,
 * когда просить следующую страницу (см. [loadMoreWatching]/[loadMoreRecommendations]) —
 * обычно это подскролл горизонтального списка к последним элементам.
 */
class HomeViewModel(
    private val releaseRepository: ReleaseRepository,
) : ViewModel() {
    private val watchingPaginator = releaseRepository.watchingPaginator()
    private val recommendationsPaginator = releaseRepository.recommendationsPaginator()

    val watchingState: StateFlow<PagingState<Release>> = watchingPaginator.state
    val recommendationsState: StateFlow<PagingState<Release>> = recommendationsPaginator.state

    init {
        viewModelScope.launch { watchingPaginator.loadNext() }
        viewModelScope.launch { recommendationsPaginator.loadNext() }
    }

    fun retryWatching() {
        viewModelScope.launch { watchingPaginator.loadNext() }
    }

    fun retryRecommendations() {
        viewModelScope.launch { recommendationsPaginator.loadNext() }
    }

    fun loadMoreWatching() {
        viewModelScope.launch { watchingPaginator.loadNext() }
    }

    fun loadMoreRecommendations() {
        viewModelScope.launch { recommendationsPaginator.loadNext() }
    }
}
