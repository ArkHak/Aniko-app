package com.aniko.app.feature.search

import com.aniko.model.CatalogContentType
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SearchState.hasActiveFilters` решает, показывать ли кнопку «Сбросить» в верхней панели Каталога.
 * Ошибка здесь молча прячет сброс (или, наоборот, рисует его без причины), поэтому границы
 * зафиксированы отдельно, включая поля фильтра без собственного UI, которые приносит deep link.
 */
class SearchStateTest {
    @Test
    fun defaultStateHasNoActiveFilters() {
        assertFalse(SearchState().hasActiveFilters)
    }

    @Test
    fun statusMakesFiltersActive() {
        assertTrue(SearchState(filter = CatalogFilter(statusId = 2)).hasActiveFilters)
    }

    @Test
    fun genresMakeFiltersActive() {
        assertTrue(SearchState(filter = CatalogFilter(genres = setOf("драма"))).hasActiveFilters)
    }

    @Test
    fun hiddenDeepLinkFiltersAreStillActive() {
        // Годы, «Дунхуа» и режим исключения не имеют своего чипа, но реально фильтруют выдачу —
        // без «Сбросить» пользователь не смог бы их снять.
        assertTrue(SearchState(filter = CatalogFilter(startYear = 2020)).hasActiveFilters)
        assertTrue(SearchState(filter = CatalogFilter(endYear = 2024)).hasActiveFilters)
        assertTrue(SearchState(filter = CatalogFilter(contentType = CatalogContentType.DONGHUA)).hasActiveFilters)
        assertTrue(SearchState(filter = CatalogFilter(genresExcludeMode = true)).hasActiveFilters)
    }

    @Test
    fun sortAloneIsNotAFilter() {
        // sort приходит из вкладки «Все/Новинки» (CatalogTab.sort), а не из фильтра.
        assertFalse(SearchState(filter = CatalogFilter(sort = CatalogSort.RATING)).hasActiveFilters)
    }

    @Test
    fun queryAndTabDoNotCountAsFilters() {
        assertFalse(SearchState(query = "Наруто", tab = CatalogTab.New).hasActiveFilters)
    }
}
