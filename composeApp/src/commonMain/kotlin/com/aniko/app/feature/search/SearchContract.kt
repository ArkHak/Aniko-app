package com.aniko.app.feature.search

import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.data.paging.PagingState
import com.aniko.model.AnixError
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseId

/**
 * Вкладки каталога (P7.T3) — маппятся на [CatalogSort], который реально едет в
 * `FilterRequestDto.sort` (см. `ReleaseRepository.filterPaginator`). "Новинки" — не полноценный
 * фид новых поступлений (см. P0.T3, строка "Вкладка New Arrivals" — **CUT**, у API нет
 * поля новизны), а сортировка каталога по недавно обновлённым (`SORT_UPDATED_DESC`) — ближайшее
 * доступное приближение, тот же компромисс, что уже принят фундаментом Фазы 7.
 */
enum class CatalogTab {
    All,
    New,
    ;

    val sort: CatalogSort
        get() =
            when (this) {
                All -> CatalogSort.POPULARITY
                New -> CatalogSort.RECENTLY_UPDATED
            }
}

/**
 * Состояние экрана Catalog/Search (P7.T3-T6).
 *
 * Один [pagingState] на оба режима выдачи (поиск по строке / фильтр каталога, см. KDoc
 * [SearchViewModel]) — экран не различает их источник, только показывает текущий список.
 * [filter] хранится БЕЗ учтённой вкладки: реальный `CatalogFilter.sort`, уходящий в
 * `ReleaseRepository.filterPaginator`, вычисляется во ViewModel как `filter.copy(sort = tab.sort)`
 * — экран не обязан знать об этой детали маппинга.
 */
data class SearchState(
    val query: String = "",
    val tab: CatalogTab = CatalogTab.All,
    val filter: CatalogFilter = CatalogFilter(),
    val pagingState: PagingState<Release> = PagingState(),
) : UiState {
    /**
     * `true` — активен режим поиска по строке (`ReleaseRepository.searchPaginator`), `false` —
     * режим фильтра каталога (`ReleaseRepository.filterPaginator`). Непустой (после `trim`) текст
     * поиска ВСЕГДА приоритетнее фильтров — см. KDoc [SearchViewModel] про то, почему `statusId`/
     * `genres` при этом не отправляются на сервер (эндпоинт поиска их не поддерживает).
     */
    val isSearchMode: Boolean get() = query.isNotBlank()

    /**
     * `true` — задан хотя бы один фильтр каталога: статус, жанры или скрытые в UI поля, которые
     * может принести deep link (годы, «Дунхуа», режим исключения жанров). По нему верхняя панель
     * показывает кнопку «Сбросить». `sort` не сравнивается: он приходит из вкладки
     * ([CatalogTab.sort]), а не из фильтра (см. KDoc [SearchState]).
     */
    val hasActiveFilters: Boolean
        get() = filter.copy(sort = NO_FILTERS.sort) != NO_FILTERS
}

private val NO_FILTERS = CatalogFilter()

/** Команды экрана Catalog/Search. */
sealed interface SearchIntent : UiIntent {
    data class QueryChanged(
        val query: String,
    ) : SearchIntent

    data class TabSelected(
        val tab: CatalogTab,
    ) : SearchIntent

    /** Клик по статус-чипу — повторный клик по уже выбранному статусу снимает выбор. */
    data class StatusToggled(
        val statusId: Int,
    ) : SearchIntent

    /** Клик по жанр-чипу — множественный выбор, см. [CatalogFilter.genres]. */
    data class GenreToggled(
        val genre: String,
    ) : SearchIntent

    /**
     * Кнопка «Сбросить» верхней панели: вернуть фильтр к значению по умолчанию (статус, жанры и
     * скрытые поля deep link). Текст поиска и вкладку «Все/Новинки» не трогает.
     */
    data object FiltersReset : SearchIntent

    /** Быстрый сброс чипа «Жанры» (✕ на чипе / «Сбросить» в шторке): снять все выбранные жанры. */
    data object GenresCleared : SearchIntent

    /** Catalog-меню «⋮» (сверка 2026-09-08): поставить релиз в список/сменить статус. */
    data class SetListStatus(
        val releaseId: ReleaseId,
        val status: ListStatus,
    ) : SearchIntent

    /** Catalog-меню «⋮»: убрать релиз из списка. */
    data class RemoveFromList(
        val releaseId: ReleaseId,
    ) : SearchIntent

    data object LoadMore : SearchIntent

    data object Retry : SearchIntent
}

/**
 * См. KDoc `HomeEffect` (тот же случай) — ошибка первой загрузки уже видна через
 * `SearchState.pagingState.error`, [ShowError] нужен только для неудачной подгрузки СЛЕДУЮЩЕЙ
 * страницы, когда список уже непустой.
 */
sealed interface SearchEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : SearchEffect
}
