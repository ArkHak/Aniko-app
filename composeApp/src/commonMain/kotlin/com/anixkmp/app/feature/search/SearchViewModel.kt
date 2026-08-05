package com.anixkmp.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anixkmp.data.paging.PagingState
import com.anixkmp.data.paging.Paginator
import com.anixkmp.data.repository.ReleaseRepository
import com.anixkmp.model.Release
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel экрана поиска.
 *
 * Каждый новый (debounced) запрос — новый [Paginator] (`ReleaseRepository.searchPaginator`),
 * т.к. пагинатор захватывает конкретный `query` в замыкании фетчера и не умеет менять его на
 * лету. `flatMapLatest` по debounced query — стандартный способ гарантированно отменить
 * предыдущий пагинатор/подписку при вводе нового символа, без ручного управления Job'ами.
 */
class SearchViewModel(
    private val releaseRepository: ReleaseRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Пагинатор активного запроса — нужен, чтобы [loadMore] знал, у кого просить следующую страницу. */
    private var activePaginator: Paginator<Release>? = null

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingState: StateFlow<PagingState<Release>> = _query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                activePaginator = null
                flowOf(PagingState())
            } else {
                val paginator = releaseRepository.searchPaginator(trimmed)
                activePaginator = paginator
                viewModelScope.launch { paginator.loadNext() }
                paginator.state
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PagingState())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun loadMore() {
        viewModelScope.launch { activePaginator?.loadNext() }
    }

    fun retry() {
        viewModelScope.launch { activePaginator?.loadNext() }
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 400L
private const val STOP_TIMEOUT_MILLIS = 5_000L
