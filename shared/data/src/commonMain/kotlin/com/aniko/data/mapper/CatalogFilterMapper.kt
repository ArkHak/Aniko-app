package com.aniko.data.mapper

import com.aniko.data.dto.FilterRequestDto
import com.aniko.model.CatalogContentType
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort

/**
 * `CatalogFilter` → тело `POST filter/{page}` (P16.T1).
 *
 * Живёт в `mapper`, а не приватным методом `ReleaseRepository`: маппинг — чистая функция над
 * доменной моделью, и именно здесь возможна ошибка, которая не падает, а молча показывает в табе
 * «Дунхуа» японские релизы (см. `CatalogFilterMapperTest`).
 */
internal fun CatalogFilter.toFilterRequestDto(): FilterRequestDto =
    FilterRequestDto(
        statusId = statusId?.toLong(),
        startYear = startYear,
        endYear = endYear,
        sort = sort.toApiSort(),
        country = contentType.toApiCountry(),
        genres = genres.toList(),
        isGenresExcludeModeEnabled = genresExcludeMode,
        genresMode =
            if (genresExcludeMode) {
                FilterRequestDto.GENRES_MODE_EXCLUDE
            } else {
                FilterRequestDto.GENRES_MODE_ALL
            },
    )

private fun CatalogSort.toApiSort(): Int =
    when (this) {
        CatalogSort.RECENTLY_UPDATED -> FilterRequestDto.SORT_UPDATED_DESC
        CatalogSort.RATING -> FilterRequestDto.SORT_GRADE_DESC
        CatalogSort.YEAR -> FilterRequestDto.SORT_YEAR_DESC
        CatalogSort.POPULARITY -> FilterRequestDto.SORT_POPULARITY_DESC
    }

/**
 * Страна релиза для таба «Аниме/Дунхуа» — значения самого API (`country` в `FilterRequest`), те же
 * строки, что рисует Anixart 10 (`country_japan`/`country_china` в его ресурсах). Локализовать
 * нельзя: сервер матчит их ровно в таком виде (тот же прецедент, что у `AnixGenres`).
 */
@Suppress("ForbiddenCyrillicStringLiteral")
private fun CatalogContentType.toApiCountry(): String =
    when (this) {
        CatalogContentType.ANIME -> "Япония"
        CatalogContentType.DONGHUA -> "Китай"
    }
