package com.aniko.app.feature.home

import androidx.lifecycle.viewModelScope
import com.aniko.app.mvi.BaseViewModel
import com.aniko.data.paging.Paginator
import com.aniko.data.repository.ReleaseRepository
import com.aniko.model.Release
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel главного экрана: секции «Продолжить смотреть» и «Рекомендации» (MVI-контракт,
 * см. `HomeContract.kt`, P5.T8 — эталонный экран для миграции на [BaseViewModel]).
 *
 * Оба списка — независимые [com.aniko.data.paging.Paginator] из `ReleaseRepository`, каждый
 * грузит свою первую страницу сразу при создании ViewModel (см. `init`). Их `PagingState`
 * сведены в один [HomeState] через `combine`, поэтому `HomeScreen` собирает один `StateFlow`
 * вместо двух. `HomeScreen` сам решает, когда просить следующую страницу (см.
 * [HomeIntent.LoadMoreWatching]/[HomeIntent.LoadMoreRecommendations]) — обычно это подскролл
 * горизонтального списка к последним элементам.
 */
class HomeViewModel(
    private val releaseRepository: ReleaseRepository,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>(initialState = HomeState()) {
    private val watchingPaginator = releaseRepository.watchingPaginator()
    private val recommendationsPaginator = releaseRepository.recommendationsPaginator()

    init {
        combine(watchingPaginator.state, recommendationsPaginator.state, ::HomeState)
            .onEach { merged -> updateState { merged } }
            .launchIn(viewModelScope)

        dispatch(HomeIntent.LoadMoreWatching)
        dispatch(HomeIntent.LoadMoreRecommendations)
    }

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RetryWatching, HomeIntent.LoadMoreWatching ->
                loadNextAndReportIfMoreFailed(watchingPaginator)

            HomeIntent.RetryRecommendations, HomeIntent.LoadMoreRecommendations ->
                loadNextAndReportIfMoreFailed(recommendationsPaginator)
        }
    }

    /**
     * `Paginator.loadNext()` при ошибке кладёт её в свой `PagingState.error`, ничего не удаляя
     * из уже загруженных [com.aniko.data.paging.PagingState.items]. Если список к этому моменту
     * был пуст — ошибка и так видна через `HomeState.watching.error`/`.recommendations.error`
     * (см. `ReleaseSection` в `HomeScreen`, ветка `AnixErrorBox`). Если список уже непустой —
     * `ReleaseSection` рисует `LazyRow` и это поле ошибки не показывает никак, поэтому здесь
     * дополнительно шлём [HomeEffect.ShowError] для одноразового уведомления (снекбар).
     */
    private suspend fun loadNextAndReportIfMoreFailed(paginator: Paginator<Release>) {
        paginator.loadNext()
        val state = paginator.state.value
        val error = state.error
        if (error != null && state.items.isNotEmpty()) {
            emitEffect(HomeEffect.ShowError(error))
        }
    }
}
