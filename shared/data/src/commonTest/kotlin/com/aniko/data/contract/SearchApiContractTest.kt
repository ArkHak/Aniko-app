package com.aniko.data.contract

import com.aniko.data.api.SearchApi
import com.aniko.data.fixtures.ApiFixtures
import com.aniko.data.mapper.toDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Контрактный тест `SearchApi` (P11.T1) — покрывает фикстуру
 * `searchReleasesPage0NoApiVersionHeader`.
 */
class SearchApiContractTest {
    @Test
    fun releaseSearch_page0_mapsToPagedReleasesWithRealValues() =
        runTest {
            val client =
                mockAnixClient("/search/releases/0", ApiFixtures.searchReleasesPage0NoApiVersionHeader)
            val api = SearchApi(client)

            val page = api.releaseSearch(page = 0, query = "naruto")

            assertEquals(0, page.code)
            assertEquals(0, page.currentPage)
            assertEquals(0, page.totalPageCount)
            assertEquals(25, page.totalCount)
            assertEquals(25, page.content.size)

            val first = page.content.first().toDomain()
            assertEquals(609, first.id)
            assertEquals("Наруто", first.title) // title_ru
            assertEquals("Naruto", first.originalTitle)
            assertEquals(4.662797143, first.grade)
            assertEquals(2002, first.year)
        }
}
