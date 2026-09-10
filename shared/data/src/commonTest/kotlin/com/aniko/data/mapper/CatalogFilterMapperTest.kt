package com.aniko.data.mapper

import com.aniko.model.CatalogContentType
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Маппинг UI-фильтра каталога в тело `POST filter/{page}` (P16.T1).
 *
 * Почему именно страна: табы «Аниме/Дунхуа» — пресет по `country`, и ошибка в этой строке не
 * падает и не видна в логах — она просто показывает в табе «Дунхуа» японские релизы. Остальные
 * поля едут в запрос как есть, их проверка здесь только страхует сортировку/режим жанров от
 * случайной перепутанной константы.
 */
class CatalogFilterMapperTest {
    @Test
    fun animeTabSendsJapan() {
        assertEquals("Япония", CatalogFilter(contentType = CatalogContentType.ANIME).toFilterRequestDto().country)
    }

    @Test
    fun donghuaTabSendsChina() {
        assertEquals("Китай", CatalogFilter(contentType = CatalogContentType.DONGHUA).toFilterRequestDto().country)
    }

    @Test
    fun defaultFilterIsAnimeTab() {
        // Дефолт совпадает с Anixart 10 (первый таб — «Аниме»): пустой фильтр не должен уезжать
        // без страны вовсе, иначе каталог показывал бы смешанную выдачу.
        assertEquals(CatalogContentType.ANIME, CatalogFilter().contentType)
        assertEquals("Япония", CatalogFilter().toFilterRequestDto().country)
    }

    @Test
    fun sortAndGenresModeReachRequest() {
        val request =
            CatalogFilter(
                sort = CatalogSort.RATING,
                genres = setOf("экшен"),
                genresExcludeMode = true,
            ).toFilterRequestDto()

        assertEquals(1, request.sort)
        assertEquals(listOf("экшен"), request.genres)
        assertEquals(true, request.isGenresExcludeModeEnabled)
        assertNull(request.statusId)
    }
}
