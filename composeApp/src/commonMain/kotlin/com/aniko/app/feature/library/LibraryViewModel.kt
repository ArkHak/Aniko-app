package com.aniko.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.librarypreferences.LibraryPreferencesStore
import com.aniko.data.librarypreferences.LibraryViewMode
import com.aniko.data.paging.Paginator
import com.aniko.data.paging.PagingState
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.repository.ProfileRepository
import com.aniko.model.ListMembership
import com.aniko.model.ListStatus
import com.aniko.model.ProfileDetails
import com.aniko.model.Release
import com.aniko.model.ReleaseId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
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
    /** Явно выбранный пользователем вид («Список»/«Сетка постеров»), общий для всех вкладок и
     *  размеров окна — см. [LibraryViewModel.setViewMode]. `null` — выбора не было: вид по умолчанию
     *  выбирает экран по размеру окна (ViewModel про окна не знает). */
    val viewMode: LibraryViewMode? = null,
    /** P9.T2: `sort=` вкладки [selectedTab] сейчас обратный (см. KDoc [LibraryViewModel.toggleReverse]). */
    val isReversed: Boolean = false,
    /** P9.T1: счётчики вкладок статусов/избранного — `null`, пока не загружены (или гость без
     *  сессии, см. [LibraryViewModel.refreshCounts]); тогда вкладки просто без числа. */
    val profile: ProfileDetails? = null,
)

/**
 * ViewModel экрана «Мои списки».
 *
 * ## Откуда берётся содержимое вкладки
 *
 * Источник правды о том, в каком списке лежит релиз, — локальная БД
 * ([LibraryRepository.observeListMemberships]), а не ответ сервера. Серверные страницы (пагинатор
 * на вкладку, см. [paginatorFor]) наполняют ту же БД (`LibraryRepository.syncToLocalCache`) и дают
 * порядок, пагинацию и состояния загрузки/ошибки, но НЕ решают сами, что показать. Список вкладки
 * собирается в [visibleItems] из двух непересекающихся частей:
 *
 * 1. элементы загруженных страниц, у которых членство в БД не противоречит вкладке
 *    ([keepsOnTab]) — так релиз, переложенный в другой список откуда угодно (карточка релиза,
 *    поиск, главная, само «Мои списки»), мгновенно исчезает из старой вкладки;
 * 2. «добавленные локально» ([extraReleasesFor]) — релизы, которые пользователь только что сам
 *    переложил сюда ([LibraryRepository.locallyEditedReleases]) и которых в загруженных страницах
 *    ещё нет; они встают в начало вкладки (в конец при [toggleReverse], как и на сервере с
 *    обратным `sort=`).
 *
 * Части по построению не пересекаются (вторая считается вычитанием id первой) и вычисляются в
 * одном `combine` из одного снимка БД — поэтому при появлении релиза в свежезапрошенной странице
 * он не мигает и не двоится, а просто перестаёт быть «добавленным локально». Гонка «оптимистичная
 * запись ↔ ответ сервера» исключена на уровне БД: страницы заводят членство через
 * `INSERT OR IGNORE` и не перетирают ещё не отправленную правку (см. KDoc `ListMembership.sq`).
 *
 * Отсутствие релиза в карте членства трактуется как «БД пока не знает», а не «не в списке»:
 * элемент страницы в таком случае остаётся видимым ([keepsOnTab]), иначе вкладка мигала бы
 * пустотой в первый кадр, пока запись страницы в БД не доедет до подписки.
 *
 * ## Что осталось поверх БД
 *
 * Пагинатор на вкладку создаётся лениво в [paginatorFor] при первом посещении и живёт в
 * [paginators] — переключение между уже посещёнными вкладками не бьёт по сети повторно, ровно как
 * `SearchViewModel` переиспользует `Paginator` для одного и того же (debounced) запроса.
 * [hiddenIdsByTab] остался только для истории просмотра ([removeFromHistory]): у неё нет членства
 * в списках, прятать элемент больше нечем.
 *
 * Гость без сессии и офлайн: мутации всё так же пишутся локально и уходят офлайн-очередью, поэтому
 * перекладывание карточек между вкладками работает и без сети; страницы при этом просто приходят
 * с ошибкой, и вкладка показывает то, что есть в БД (или экран ошибки, если нет ничего).
 */
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val profileRepository: ProfileRepository,
    private val libraryPreferences: LibraryPreferencesStore,
) : ViewModel() {
    private val paginators = mutableMapOf<LibraryTab, Paginator<Release>>()

    private val selectedTabState = MutableStateFlow(DEFAULT_TAB)

    /** Только для [LibraryTab.History] — см. KDoc класса и [removeFromHistory]. */
    private val hiddenIdsByTab = MutableStateFlow<Map<LibraryTab, Set<ReleaseId>>>(emptyMap())

    /** P9.T2: `sort=` (см. [LibraryRepository.SORT_RECENTLY_ADDED]/[LibraryRepository.SORT_REVERSED])
     *  на вкладку — отсутствие ключа значит "по умолчанию" (не реверс). */
    private val reversedByTab = MutableStateFlow<Map<LibraryTab, Boolean>>(emptyMap())

    /** P9.T1: счётчики вкладок, источник — `ProfileDetails` (тот же профиль, что `ProfileScreen`). */
    private val profileState = MutableStateFlow<ProfileDetails?>(null)

    /**
     * Полные [Release] тех карточек, по которым пользователь действовал прямо здесь, на экране
     * списков: у [changeStatus]/[toggleFavorite] объект уже на руках, и он богаче кэша
     * (`releaseEntity` намеренно не хранит прогресс просмотра, см. KDoc [Release]). Для правок с
     * других экранов такого объекта нет — там карточка гидрируется из кэша
     * ([LibraryRepository.observeCachedReleases]), и подпись прогресса подтянется при следующем
     * запросе страницы.
     */
    private val locallyMovedReleases = MutableStateFlow<Map<ReleaseId, Release>>(emptyMap())

    private val memberships: StateFlow<Map<ReleaseId, ListMembership>> =
        libraryRepository
            .observeListMemberships()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    /** Всё, что нужно знать про одну вкладку: страницы с сервера + членство + локальные добавления. */
    private data class TabContent(
        val tab: LibraryTab,
        val pagingState: PagingState<Release> = PagingState(),
        val memberships: Map<ReleaseId, ListMembership> = emptyMap(),
        val extras: List<Release> = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tabContent: StateFlow<TabContent> =
        selectedTabState
            .flatMapLatest { tab ->
                val paginator = paginatorFor(tab)
                viewModelScope.launch { paginator.loadNext() }
                combine(paginator.state, memberships, extraReleasesFor(tab)) { paging, membership, extras ->
                    TabContent(tab, paging, membership, extras)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TabContent(DEFAULT_TAB))

    val uiState: StateFlow<LibraryUiState> =
        combine(
            tabContent,
            hiddenIdsByTab,
            reversedByTab,
            profileState,
            libraryPreferences.viewMode,
        ) { content, hiddenByTab, reversedByTabValue, profile, viewMode ->
            val tab = content.tab
            val isReversed = reversedByTabValue[tab] == true
            val visibleItems = content.visibleItems(hiddenByTab[tab].orEmpty(), isReversed)
            LibraryUiState(
                selectedTab = tab,
                pagingState = content.pagingState.copy(items = visibleItems),
                viewMode = viewMode,
                isReversed = isReversed,
                profile = profile,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            // Сохранённый вид известен сразу (StateFlow), поэтому первый кадр уже рисуется им, а не видом
            // по умолчанию для размера окна, который через кадр сменился бы выбором пользователя.
            LibraryUiState(viewMode = libraryPreferences.viewMode.value),
        )

    init {
        refreshCounts()
        // Счётчики вкладок по-прежнему приходят из профиля (локальная БД знает только про
        // релизы, которые пользователь уже видел, и посчитать по ней размер списка целиком
        // нельзя) — но перезапрашиваются теперь на ЛЮБУЮ мутацию членства, включая сделанные на
        // других экранах. drop(1) — стартовое значение потока не является правкой.
        viewModelScope.launch {
            libraryRepository.locallyEditedReleases.drop(1).collect { refreshCounts() }
        }
    }

    /**
     * Релизы, которые пользователь сам переложил в [tab] и которых ещё нет в загруженных страницах.
     * Кандидаты берутся из [LibraryRepository.locallyEditedReleases] (правки из любого места
     * приложения), фильтруются по актуальному членству и гидрируются из кэша — либо из
     * [locallyMovedReleases], если карточка пришла с этого же экрана.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun extraReleasesFor(tab: LibraryTab): Flow<List<Release>> =
        combine(libraryRepository.locallyEditedReleases, memberships) { edited, membership ->
            edited.filter { membership[it].belongsTo(tab) }
        }.distinctUntilChanged()
            .flatMapLatest { ids ->
                combine(
                    libraryRepository.observeCachedReleases(ids),
                    locallyMovedReleases,
                ) { cached, moved -> ids.mapNotNull { moved[it] ?: cached[it] } }
            }

    /**
     * Видимый список вкладки — страницы, отфильтрованные членством, плюс локальные добавления.
     * Порядок добавлений повторяет серверный: «недавно изменённые» сверху, а при [isReversed]
     * (`sort=`[LibraryRepository.SORT_REVERSED]) — снизу.
     */
    private fun TabContent.visibleItems(
        hidden: Set<ReleaseId>,
        isReversed: Boolean,
    ): List<Release> {
        val pageItems =
            pagingState.items
                .filterNot { it.id in hidden }
                .filter { memberships.keepsOnTab(it, tab) }
                .map { it.withMembership(memberships) }
        val pageIds = pageItems.mapTo(mutableSetOf()) { it.id }
        val extraItems = extras.filterNot { it.id in pageIds }.map { it.withMembership(memberships) }
        return if (isReversed) pageItems + extraItems else extraItems + pageItems
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
     * НЕ клиентская перестановка: без похода на сервер порядок был
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
     * Запоминает выбранный пользователем вид экрана («Список»/«Сетка постеров») — один на все
     * вкладки и все размеры окна, переживает перезапуск ([LibraryPreferencesStore]). Клиентская
     * операция: список вкладки и запросы к API от вида не зависят.
     */
    fun setViewMode(mode: LibraryViewMode) {
        libraryPreferences.setViewMode(mode)
    }

    /**
     * P9.T1: перезапрашивает `ProfileDetails` для счётчиков вкладок. Дёргается при старте экрана и
     * на любую мутацию членства — в том числе сделанную на другом экране (подписка на
     * [LibraryRepository.locallyEditedReleases] в [init]).
     *
     * Счётчик — это размер ВСЕГО списка на сервере, а локальная БД знает только про уже виденные
     * релизы, поэтому посчитать его по [memberships] нельзя, и здесь остаётся перезапрос профиля.
     * Счётчики декоративны для вкладок, отдельной обработки ошибки под них план не предусматривает
     * — при сбое (в т.ч. у гостя без сессии) [profileState] просто остаётся как было, вкладки
     * показывают только название, без числа.
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
     * Ни прятать релиз вручную, ни инвалидировать вкладку нового статуса больше не нужно:
     * репозиторий синхронно пишет новое членство в БД, и обе вкладки пересобираются сами из
     * [memberships] — см. KDoc класса. Отката тоже нет и не должно быть: доставку на сервер (и
     * повторы при сбое) полностью ведёт офлайн-очередь, `runCatching` здесь — только чтобы
     * непредвиденный сбой записи не уронил `viewModelScope` целиком.
     */
    fun changeStatus(
        release: Release,
        newStatus: ListStatus,
    ) {
        rememberMoved(release)
        viewModelScope.launch { runCatching { libraryRepository.addToList(newStatus, release.id) } }
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
        rememberMoved(release)
        viewModelScope.launch { libraryRepository.removeFromList(release.id) }
    }

    fun toggleFavorite(release: Release) {
        val addingFavorite = !(memberships.value[release.id]?.isFavorite ?: release.isFavorite)
        rememberMoved(release)
        viewModelScope.launch {
            if (addingFavorite) {
                libraryRepository.addFavorite(release.id)
            } else {
                libraryRepository.removeFavorite(release.id)
            }
        }
    }

    /**
     * История просмотра — единственная вкладка без членства в списках (см. KDoc класса), поэтому
     * оптимистичное скрытие здесь всё ещё ручное, через [hiddenIdsByTab], с откатом при сбое.
     */
    fun removeFromHistory(release: Release) {
        val tab = selectedTabState.value
        hiddenIdsByTab.value = hiddenIdsByTab.value + (tab to (hiddenIdsByTab.value[tab].orEmpty() + release.id))
        viewModelScope.launch {
            runCatching { libraryRepository.removeFromHistory(release.id) }
                .onFailure {
                    hiddenIdsByTab.value =
                        hiddenIdsByTab.value + (tab to (hiddenIdsByTab.value[tab].orEmpty() - release.id))
                }
        }
    }

    private fun rememberMoved(release: Release) {
        locallyMovedReleases.value = locallyMovedReleases.value + (release.id to release)
    }
}

/**
 * Остаётся ли элемент страницы на вкладке [tab]. Прячем только при ЯВНОМ противоречии: если БД про
 * релиз ещё ничего не знает (ключа в карте нет), верим серверной странице, которая его и принесла
 * — см. KDoc [LibraryViewModel] про разницу «нет записи» и «запись со `status = null`».
 */
private fun Map<ReleaseId, ListMembership>.keepsOnTab(
    release: Release,
    tab: LibraryTab,
): Boolean {
    val local = this[release.id] ?: return true
    return when (tab) {
        is LibraryTab.Status -> local.status == tab.status
        LibraryTab.Favorites -> local.isFavorite
        LibraryTab.History -> true
    }
}

/**
 * Относится ли локально известное членство к вкладке [tab]. В отличие от [keepsOnTab], «записи
 * нет» здесь значит «нет», а не «верим серверу»: это правило отбора кандидатов на добавление
 * сверху, и добавлять по незнанию нечего. История локальных добавлений не имеет
 * ([LibraryViewModel.removeFromHistory] умеет только убирать).
 */
private fun ListMembership?.belongsTo(tab: LibraryTab): Boolean =
    when (tab) {
        is LibraryTab.Status -> this?.status == tab.status
        LibraryTab.Favorites -> this?.isFavorite == true
        LibraryTab.History -> false
    }

/**
 * Подменяет в карточке членство из серверного ответа на локальное, когда оно известно, — чтобы
 * чип статуса и пункты контекстного меню (`LibraryContextMenu` прячет текущий статус и
 * переключает подпись избранного) соответствовали тому, что пользователь только что выбрал, а не
 * тому, что было в момент запроса страницы.
 */
private fun Release.withMembership(memberships: Map<ReleaseId, ListMembership>): Release =
    memberships[id]?.let { copy(myListStatus = it.status, isFavorite = it.isFavorite) } ?: this

private val DEFAULT_TAB: LibraryTab = LibraryTab.Status(ListStatus.WATCHING)
private const val STOP_TIMEOUT_MILLIS = 5_000L
