package com.aniko.data.contract

import com.aniko.data.api.FilterApi
import com.aniko.data.dto.FilterRequestDto
import com.aniko.data.fixtures.ApiFixtures
import com.aniko.data.mapper.toDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Контрактный тест `FilterApi` (P11.T1) — покрывает фикстуру `filterPage0`.
 */
class FilterApiContractTest {
    @Test
    fun filter_page0_mapsToPagedReleasesWithRealValues() =
        runTest {
            val client = mockAnixClient("/filter/0", ApiFixtures.filterPage0)
            val api = FilterApi(client)

            val page = api.filter(page = 0, request = FilterRequestDto())

            assertEquals(0, page.code)
            assertEquals(0, page.currentPage)
            assertEquals(0, page.totalPageCount)
            assertEquals(25, page.totalCount)
            assertEquals(1, page.content.size)

            val first = page.content.single().toDomain()
            assertEquals(20277, first.id)
            assertEquals("Копэн", first.title) // title_ru
            assertEquals("Koupen-chan", first.originalTitle)
            assertEquals(4.567712318, first.grade)
            assertEquals(2025, first.year)
        }
}
