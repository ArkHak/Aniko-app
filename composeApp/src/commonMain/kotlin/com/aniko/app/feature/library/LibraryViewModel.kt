package com.aniko.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.paging.Paginator
import com.aniko.data.paging.PagingState
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.repository.ProfileRepository
import com.aniko.model.ListStatus
import com.aniko.model.ProfileDetails
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
    /** P9.T2: активна ли клиентская перетасовка вкладки [selectedTab] прямо сейчас — см. KDoc
     *  [LibraryViewModel.toggleShuffle]. */
    val isShuffled: Boolean = false,
    /** P9.T2: `sort=` вкладки [selectedTab] сейчас обратный (см. KDoc [LibraryViewModel.toggleReverse]). */
    val isReversed: Boolean = false,
    /** P9.T1: счётчики вкладок статусов/избранного — `null`, пока не загружены (или гость без
     *  сессии, см. [LibraryViewModel.refreshCounts]); тогда вкладки просто без числа. */
    val profile: ProfileDetails? = null,
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
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val paginators = mutableMapOf<LibraryTab, Paginator<Release>>()

    private val selectedTabState = MutableStateFlow(DEFAULT_TAB)
    private val hiddenIdsByTab = MutableStateFlow<Map<LibraryTab, Set<Int>>>(emptyMap())

    /** P9.T2: `sort=` (см. [LibraryRepository.SORT_RECENTLY_ADDED]/[LibraryRepository.SORT_REVERSED])
     *  на вкладку — отсутствие ключа значит "по умолчанию" (не реверс). */
    private val reversedByTab = MutableStateFlow<Map<LibraryTab, Boolean>>(emptyMap())

    /** P9.T2: id элементов вкладки в порядке клиентской перетасовки — см. [toggleShuffle]. */
    private val shuffleOrderByTab = MutableStateFlow<Map<LibraryTab, List<Int>>>(emptyMap())

    /** P9.T1: счётчики вкладок, источник — `ProfileDetails` (тот же профиль, что `ProfileScreen`). */
    private val profileState = MutableStateFlow<ProfileDetails?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tabPagingState: StateFlow<Pair<LibraryTab, PagingState<Release>>> =
        selectedTabState
            .flatMapLatest { tab ->
                val paginator = paginatorFor(tab)
                viewModelScope.launch { paginator.loadNext() }
                paginator.state.map { tab to it }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DEFAULT_TAB to PagingState())

    val uiState: StateFlow<LibraryUiState> =
        combine(
            tabPagingState,
            hiddenIdsByTab,
            shuffleOrderByTab,
            reversedByTab,
            profileState,
        ) { (tab, pagingState), hiddenByTab, shuffleByTab, reversedByTabValue, profile ->
            val hidden = hiddenByTab[tab].orEmpty()
            val visibleItems =
                if (hidden.isEmpty()) pagingState.items else pagingState.items.filterNot { it.id in hidden }
            // Перетасовка вкладки считается активной, только пока набор видимых id не изменился
            // относительно момента тоггла (см. KDoc toggleShuffle) — подгрузка страницы/смена
            // статуса/удаление естественно "гасят" её здесь, без явного сброса состояния.
            val shuffleOrder = shuffleByTab[tab]
            val visibleIds = visibleItems.map { it.id }
            val activeShuffleOrder = shuffleOrder?.takeIf { it.toSet() == visibleIds.toSet() }
            val isShuffled = activeShuffleOrder != null
            val orderedItems =
                if (activeShuffleOrder != null) {
                    val indexById = activeShuffleOrder.withIndex().associate { (index, id) -> id to index }
                    visibleItems.sortedBy { indexById.getValue(it.id) }
                } else {
                    visibleItems
                }
            LibraryUiState(
                selectedTab = tab,
                pagingState = pagingState.copy(items = orderedItems),
                isShuffled = isShuffled,
                isReversed = reversedByTabValue[tab] == true,
                profile = profile,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryUiState())

    init {
        refreshCounts()
    }

    private fun paginatorFor(tab: LibraryTab): Paginator<Release> =
        paginators.getOrPut(tab) {
            when (tab) {
                is LibraryTab.Status -> libraryRepository.listPaginator(tab.status) { sortFor(tab) }
                LibraryTab.Favorites -> libraryRepository.favoritesPaginator { sortFor(tab) }
                LibraryTab.History -> libraryRepository.historyPaginator()
            }
        }

    private fun sortFor(tab: LibraryTab): Int =
        if (reversedByTab.value[tab] == true) {
            LibraryRepository.SORT_REVERSED
        } else {
            LibraryRepository.SORT_RECENTLY_ADDED
        }

    fun selectTab(tab: LibraryTab) {
        selectedTabState.value = tab
    }

    /**
     * P9.T2: переключает `sort=` вкладки [tab] и перезапрашивает её с первой страницы —
     * НЕ клиентская перестановка (в отличие от [toggleShuffle]): без похода на сервер порядок был
     * бы верным только для уже загруженных страниц, а не для списка целиком. [Paginator] не
     * пересоздаётся — [sortFor] читается заново при каждой его загрузке страницы (см. KDoc
     * [LibraryRepository.listPaginator]), поэтому здесь достаточно поменять [reversedByTab] и
     * дёрнуть [Paginator.refresh].
     *
     * Недоступно для [LibraryTab.History] — `HistoryApi` параметр `sort` не принимает
     * (см. её KDoc), `LibraryScreen` для этой вкладки кнопку не рисует; проверка здесь — на
     * случай прямого вызова.
     */
    fun toggleReverse(tab: LibraryTab) {
        if (tab == LibraryTab.History) return
        reversedByTab.value = reversedByTab.value + (tab to !(reversedByTab.value[tab] ?: false))
        viewModelScope.launch { paginatorFor(tab).refresh() }
    }

    /**
     * P9.T2: клиентская перетасовка уже загруженных элементов вкладки [tab] — без запроса к API
     * (в отличие от [toggleReverse]). Порядок фиксируется как список id на момент нажатия и живёт
     * в [shuffleOrderByTab]; [uiState] считает его активным, только пока набор видимых id вкладки
     * не поменялся (см. комбинирование в [uiState]) — так подгрузка следующей страницы (набор
     * вырос), смена статуса/избранного/удаление (набор сократился) или переключение [toggleReverse]
     * (новый серверный порядок) естественно "гасят" перетасовку без явной инвалидации здесь.
     *
     * Читает [uiState] (уже отфильтрованный по скрытым id список), а не сырой `Paginator.state`,
     * — иначе набор id для сравнения в [uiState] разъехался бы с тем, что реально видит пользователь.
     */
    fun toggleShuffle(tab: LibraryTab) {
        val currentIds =
            uiState.value.pagingState.items
                .map { it.id }
        val activeOrder = shuffleOrderByTab.value[tab]
        val isActive = activeOrder != null && activeOrder.toSet() == currentIds.toSet()
        shuffleOrderByTab.value =
            if (isActive) {
                shuffleOrderByTab.value - tab
            } else {
                shuffleOrderByTab.value + (tab to currentIds.shuffled())
            }
    }

    /**
     * P9.T1: перезапрашивает `ProfileDetails` для счётчиков вкладок. Дёргается при старте экрана
     * и после успешных мутаций, меняющих счётчик статуса/избранного ([changeStatus]/
     * [removeFromList]/[toggleFavorite]) — не полноценный реактивный поток (как, например,
     * [com.aniko.data.repository.LibraryRepository.observeListStatus]), а простой перезапрос по
     * образцу остального `LibraryViewModel` (см. KDoc класса про [invalidate]): счётчики
     * декоративны для вкладок, отдельной обработки ошибки под них план не предусматривает — при
     * сбое (в т.ч. у гостя без сессии) [profileState] просто остаётся как было, вкладки показывают
     * только название, без числа.
     */
    private fun refreshCounts() {
        viewModelScope.launch {
            runCatching { profileRepository.myProfile() }.onSuccess { profileState.value = it }
        }
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
                .onSuccess {
                    invalidate(LibraryTab.Status(newStatus))
                    refreshCounts()
                }.onFailure { unhideInCurrentTab(release.id) }
        }
    }

    /**
     * [status] больше не передаётся в `LibraryRepository.removeFromList` (P4.T7: снятие статуса
     * теперь уходит через офлайн-очередь без URL-параметра статуса, см. `SyncQueueWorker`) —
     * параметр остался в сигнатуре только потому, что вызывающая сторона (кнопка «Убрать» в
     * контекстном меню конкретной вкладки статуса, `LibraryScreen`) естественно оперирует
     * статусом текущей вкладки.
     */
    fun removeFromList(
        release: Release,
        @Suppress("UNUSED_PARAMETER") status: ListStatus,
    ) {
        hideInCurrentTab(release.id)
        viewModelScope.launch {
            runCatching { libraryRepository.removeFromList(release.id) }
                .onSuccess { refreshCounts() }
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
            }.onSuccess {
                invalidate(LibraryTab.Favorites)
                refreshCounts()
            }.onFailure { if (shouldHideLocally) unhideInCurrentTab(release.id) }
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
