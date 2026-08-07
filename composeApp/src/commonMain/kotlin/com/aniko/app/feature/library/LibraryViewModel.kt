package com.aniko.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.paging.Paginator
import com.aniko.data.paging.PagingState
import com.aniko.data.repository.LibraryRepository
import com.aniko.model.ListStatus
import com.aniko.model.Release
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Вкладка экрана «Мои списки»: 5 статусов [ListStatus] + избранное + история просмотра.
 *
 * Локальная обёртка фичи (не часть `shared/model`) — [ListStatus] описывает только серверные
 * статусы списка, а избранное/история для Anixart API — отдельные сущности без статуса.
 */
sealed class LibraryTab {
    data class Status(
        val status: ListStatus,
    ) : LibraryTab()

    data object Favorites : LibraryTab()

    data object History : LibraryTab()

    companion object {
        /** Порядок вкладок в `TabRow` — статусы в порядке объявления [ListStatus], затем избранное и история. */
        val all: List<LibraryTab> = ListStatus.entries.map(::Status) + Favorites + History
    }
}

data class LibraryUiState(
    val selectedTab: LibraryTab = DEFAULT_TAB,
    val pagingState: PagingState<Release> = PagingState(),
)

/**
 * ViewModel экрана «Мои списки».
 *
 * Пагинатор на вкладку создаётся лениво в [paginatorFor] при первом посещении и живёт в
 * [paginators], пока вкладку явно не инвалидировали (см. [invalidate]) — переключение между уже
 * посещёнными вкладками не бьёт по сети повторно, ровно как `SearchViewModel` переиспользует
 * `Paginator` для одного и того же (debounced) запроса.
 *
 * Внутри `Paginator` нет способа удалить элемент из уже загруженного списка (только
 * `loadNext`/`refresh`), поэтому оптимистичное «убери из текущей вкладки» реализовано поверх —
 * [hiddenIdsByTab] держит id, скрытые в конкретной вкладке до её следующей полной перезагрузки.
 * Это самое узкое место интеграции с `Paginator`, каким его сделали остальные ViewModel проекта.
 */
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val paginators = mutableMapOf<LibraryTab, Paginator<Release>>()

    private val selectedTabState = MutableStateFlow(DEFAULT_TAB)
    private val hiddenIdsByTab = MutableStateFlow<Map<LibraryTab, Set<Int>>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tabPagingState: StateFlow<Pair<LibraryTab, PagingState<Release>>> =
        selectedTabState
            .flatMapLatest { tab ->
                val paginator = paginatorFor(tab)
                viewModelScope.launch { paginator.loadNext() }
                paginator.state.map { tab to it }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DEFAULT_TAB to PagingState())

    val uiState: StateFlow<LibraryUiState> =
        combine(tabPagingState, hiddenIdsByTab) { (tab, pagingState), hiddenByTab ->
            val hidden = hiddenByTab[tab].orEmpty()
            val visibleState =
                if (hidden.isEmpty()) {
                    pagingState
                } else {
                    pagingState.copy(items = pagingState.items.filterNot { it.id in hidden })
                }
            LibraryUiState(selectedTab = tab, pagingState = visibleState)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryUiState())

    private fun paginatorFor(tab: LibraryTab): Paginator<Release> =
        paginators.getOrPut(tab) {
            when (tab) {
                is LibraryTab.Status -> libraryRepository.listPaginator(tab.status)
                LibraryTab.Favorites -> libraryRepository.favoritesPaginator()
                LibraryTab.History -> libraryRepository.historyPaginator()
            }
        }

    fun selectTab(tab: LibraryTab) {
        selectedTabState.value = tab
    }

    fun loadMore(tab: LibraryTab) {
        viewModelScope.launch { paginatorFor(tab).loadNext() }
    }

    fun retry(tab: LibraryTab) {
        loadMore(tab)
    }

    /**
     * Меняет статус релиза в списке. `addToList` на сервере эксклюзивен (см.
     * `LibraryRepository.addToList`), поэтому явный `removeFromList` со старым статусом не нужен.
     *
     * Релиз оптимистично прячется из текущей вкладки (он мог наблюдаться на любой вкладке —
     * не только на вкладке своего старого статуса, статус можно сменить и, например, из
     * избранного). Вкладка нового статуса, если её `Paginator` уже был создан раньше,
     * выбрасывается из кэша, чтобы при следующем визите перезапросить список с сервера.
     */
    fun changeStatus(
        release: Release,
        newStatus: ListStatus,
    ) {
        hideInCurrentTab(release.id)
        viewModelScope.launch {
            runCatching { libraryRepository.addToList(newStatus, release.id) }
                .onSuccess { invalidate(LibraryTab.Status(newStatus)) }
                .onFailure { unhideInCurrentTab(release.id) }
        }
    }

    fun removeFromList(
        release: Release,
        status: ListStatus,
    ) {
        hideInCurrentTab(release.id)
        viewModelScope.launch {
            runCatching { libraryRepository.removeFromList(status, release.id) }
                .onFailure { unhideInCurrentTab(release.id) }
        }
    }

    fun toggleFavorite(release: Release) {
        val addingFavorite = !release.isFavorite
        // Тоггл избранного не обязательно означает уход релиза с текущей вкладки (кроме вкладки
        // "Избранное", где снятие галочки как раз и должно убрать карточку из списка) — прячем
        // локально только если это действительно вкладка избранного.
        val currentTab = selectedTabState.value
        val shouldHideLocally = currentTab == LibraryTab.Favorites && !addingFavorite
        if (shouldHideLocally) {
            hideInCurrentTab(release.id)
        }
        viewModelScope.launch {
            runCatching {
                if (addingFavorite) {
                    libraryRepository.addFavorite(release.id)
                } else {
                    libraryRepository.removeFavorite(release.id)
                }
            }.onSuccess { invalidate(LibraryTab.Favorites) }
                .onFailure { if (shouldHideLocally) unhideInCurrentTab(release.id) }
        }
    }

    fun removeFromHistory(release: Release) {
        hideInCurrentTab(release.id)
        viewModelScope.launch {
            runCatching { libraryRepository.removeFromHistory(release.id) }
                .onFailure { unhideInCurrentTab(release.id) }
        }
    }

    /**
     * Выбрасывает `Paginator` вкладки [tab] из кэша, чтобы при следующем визите на неё список
     * запросился заново. Если [tab] — это вкладка, на которой пользователь находится прямо
     * сейчас, инвалидация пропускается: `flatMapLatest` в [tabPagingState] не переподпишется, пока
     * не сменится [selectedTabState], поэтому пересоздание пагинатора здесь ничего не изменило бы в
     * уже отрисованном списке — а [hideInCurrentTab] уже держит текущую вкладку консистентной.
     */
    private fun invalidate(tab: LibraryTab) {
        if (tab == selectedTabState.value) return
        paginators.remove(tab)
        hiddenIdsByTab.value = hiddenIdsByTab.value - tab
    }

    private fun hideInCurrentTab(releaseId: Int) {
        val tab = selectedTabState.value
        hiddenIdsByTab.value = hiddenIdsByTab.value + (tab to (hiddenIdsByTab.value[tab].orEmpty() + releaseId))
    }

    private fun unhideInCurrentTab(releaseId: Int) {
        val tab = selectedTabState.value
        val updated = hiddenIdsByTab.value[tab].orEmpty() - releaseId
        hiddenIdsByTab.value = hiddenIdsByTab.value + (tab to updated)
    }
}

private val DEFAULT_TAB: LibraryTab = LibraryTab.Status(ListStatus.WATCHING)
private const val STOP_TIMEOUT_MILLIS = 5_000L
