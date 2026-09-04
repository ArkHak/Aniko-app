package com.aniko.app.feature.search

import androidx.lifecycle.viewModelScope
import com.aniko.app.mvi.BaseViewModel
import com.aniko.data.paging.Paginator
import com.aniko.data.repository.ReleaseRepository
import com.aniko.model.CatalogFilter
import com.aniko.model.Release
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel экрана Catalog/Search (P7.T3-T6, MVI-контракт — см. `SearchContract.kt`,
 * миграция по критерию P5.T7: экран и так переписывается под мокап Фазы 7, эталон — `HomeViewModel`).
 *
 * ## Два независимых режима выдачи
 * Один пагинатор в моменте — [activePaginator] — либо из `ReleaseRepository.searchPaginator`
 * (текст поиска непустой после `trim`), либо из `ReleaseRepository.filterPaginator` (текст пуст —
 * "Все"/"Новинки" + статус/жанр-чипы). Решение — в [requestKeys.flatMapLatest][flatMapLatest]
 * ниже, по тому же принципу, что был в исходном `SearchViewModel` (P7 до миграции): новый режим —
 * новый [Paginator], `flatMapLatest` отменяет подписку на предыдущий.
 *
 * Приоритет у текста поиска: пока он непустой, `statusId`/`genres` из [filterState] НЕ уходят на
 * сервер — `POST search/releases/{page}` (см. `SearchApi`) не принимает фильтр вообще, только
 * `query`. Чипы при этом остаются на экране в прежнем выбранном состоянии (не сбрасываются), но
 * не влияют на результат, пока не очищено поле поиска — это то самое "где осмысленно" из брифа
 * P7, задокументированное здесь как принятое решение, а не забытый край.
 *
 * `tab` тоже применяется только в режиме фильтра (маппится в `CatalogFilter.sort` — см.
 * [CatalogTab.sort]) — у поиска по строке нет параметра сортировки на сервере.
 *
 * ## Debounce
 * Дебаунсится ТОЛЬКО текст поиска, и не фиксированным окном, а через
 * `debounce { timeoutMillis }`: пустая строка (стартовое состояние экрана и очистка поля крестиком)
 * получает `0`, непустая — стандартные 400мс. Без этого разветвления запрос браузинга каталога
 * по умолчанию (пустой запрос, вкладка "Все") ждал бы полные 400мс на старте экрана просто потому,
 * что `debounce` с фиксированным окном не различает "первое значение потока" и "пользователь
 * печатает" — оператор всегда ждёт `timeoutMillis` тишины после любого значения, включая самое
 * первое. Смена вкладки/чипов НЕ дебаунсится вовсе (не текстовый ввод, а дискретный тап).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val releaseRepository: ReleaseRepository,
) : BaseViewModel<SearchState, SearchIntent, SearchEffect>(initialState = SearchState()) {
    private val queryState = MutableStateFlow("")
    private val tabState = MutableStateFlow(CatalogTab.All)
    private val filterState = MutableStateFlow(CatalogFilter())

    /** Пагинатор активного режима — нужен, чтобы [handleIntent] знал, у кого просить страницу. */
    private var activePaginator: Paginator<Release>? = null

    init {
        val debouncedQuery =
            queryState
                .debounce { raw -> if (raw.isBlank()) NO_DEBOUNCE_MILLIS else SEARCH_DEBOUNCE_MILLIS }
                .distinctUntilChanged()

        combine(debouncedQuery, tabState, filterState) { query, tab, filter ->
            RequestKey(query = query.trim(), effectiveFilter = filter.copy(sort = tab.sort))
        }.distinctUntilChanged()
            .flatMapLatest { key ->
                val paginator =
                    if (key.query.isNotEmpty()) {
                        releaseRepository.searchPaginator(key.query)
                    } else {
                        releaseRepository.filterPaginator(key.effectiveFilter)
                    }
                activePaginator = paginator
                viewModelScope.launch { paginator.loadNext() }
                paginator.state
            }.onEach { paging -> updateState { copy(pagingState = paging) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                updateState { copy(query = intent.query) }
                queryState.value = intent.query
            }

            is SearchIntent.TabSelected -> {
                updateState { copy(tab = intent.tab) }
                tabState.value = intent.tab
            }

            is SearchIntent.StatusToggled ->
                updateFilter {
                    copy(statusId = if (statusId == intent.statusId) null else intent.statusId)
                }

            is SearchIntent.GenreToggled ->
                updateFilter {
                    copy(genres = if (intent.genre in genres) genres - intent.genre else genres + intent.genre)
                }

            SearchIntent.FiltersReset -> updateFilter { CatalogFilter() }

            is SearchIntent.ViewModeChanged -> updateState { copy(viewMode = intent.viewMode) }

            SearchIntent.LoadMore, SearchIntent.Retry -> loadNextAndReportIfMoreFailed()
        }
    }

    private fun updateFilter(reducer: CatalogFilter.() -> CatalogFilter) {
        val updated = state.value.filter.reducer()
        updateState { copy(filter = updated) }
        filterState.value = updated
    }

    /** См. KDoc `HomeViewModel.loadNextAndReportIfMoreFailed` — тот же приём для одного пагинатора. */
    private suspend fun loadNextAndReportIfMoreFailed() {
        val paginator = activePaginator ?: return
        paginator.loadNext()
        val pagingState = paginator.state.value
        val error = pagingState.error
        if (error != null && pagingState.items.isNotEmpty()) {
            emitEffect(SearchEffect.ShowError(error))
        }
    }
}

private data class RequestKey(
    val query: String,
    val effectiveFilter: CatalogFilter,
)

private const val SEARCH_DEBOUNCE_MILLIS = 400L
private const val NO_DEBOUNCE_MILLIS = 0L
