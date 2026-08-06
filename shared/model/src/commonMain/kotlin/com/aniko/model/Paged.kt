package com.aniko.model

/**
 * Страница выдачи. Anixart отдаёт `PageableResponse<T>` со схемой
 * `{ content, current_page, total_page_count, total_count }`.
 */
data class Paged<out T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val totalCount: Int? = null,
) {
    val hasNextPage: Boolean get() = currentPage + 1 < totalPages

    companion object {
        fun <T> empty(): Paged<T> = Paged(emptyList(), 0, 0, 0)
    }
}
