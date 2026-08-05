package com.anixkmp.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Общая обёртка Anixart: любой ответ несёт `code`, где `0` — успех.
 *
 * `[TODO: verify live]` — набор ненулевых кодов и их смысл пока не подтверждён,
 * см. `docs/api/ENDPOINTS.md`, пункт R3.
 */
interface ApiCodeAware {
    val code: Int
}

/** `PageableResponse<T>` — постраничная выдача. */
@Serializable
data class PageableResponseDto<T>(
    override val code: Int = 0,
    val content: List<T> = emptyList(),
    @SerialName("current_page") val currentPage: Int = 0,
    @SerialName("total_page_count") val totalPageCount: Int = 0,
    @SerialName("total_count") val totalCount: Int? = null,
) : ApiCodeAware

/** Ответ без полезной нагрузки (add/delete в списках, watch/unwatch и т.п.). */
@Serializable
data class SimpleResponseDto(
    override val code: Int = 0,
) : ApiCodeAware
