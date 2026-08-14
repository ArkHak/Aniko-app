package com.aniko.model

/**
 * Страница выдачи. Anixart отдаёт `PageableResponse<T>` со схемой
 * `{ content, current_page, total_page_count, total_count }`.
 *
 * [hasNextPage]: живая проверка API (2026-08-12) показала, что листинги релизов
 * (`filter/{page}`, `search/releases/{page}`, `discover/watching/{page}`) **всегда** отдают
 * `total_page_count: 0`, независимо от реального числа страниц — подтверждено сэмплами
 * `docs/api/samples/discover_watching_page0.json`,
 * `docs/api/samples/search_releases_page0_no_api_version_header.json`,
 * `docs/api/samples/filter_page0.json`. Со старой формулой (`currentPage + 1 < totalPages`)
 * это означало `hasNextPage == false` уже на первой странице — пагинация останавливалась
 * сразу везде, где она опирается на эти три эндпоинта. Не баг клиента, а особенность сервера.
 * У комментариев (`release/comment/all/{releaseId}/{page}`) `total_page_count` приходит
 * корректным, поэтому для случаев с надёжным `totalPages` формула не меняется.
 * Признак конца выдачи при `totalPages == 0` — пустая страница (`content: []`).
 */
data class Paged<out T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val totalCount: Int? = null,
) {
    val hasNextPage: Boolean get() = if (totalPages > 0) currentPage + 1 < totalPages else items.isNotEmpty()

    companion object {
        fun <T> empty(): Paged<T> = Paged(emptyList(), 0, 0, 0)
    }
}
