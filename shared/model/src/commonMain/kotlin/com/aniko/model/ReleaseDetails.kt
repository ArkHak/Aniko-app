package com.aniko.model

/**
 * Расширенная карточка релиза — Title Detail (P7.T7-T13, `docs/REELWAVE_PLAN.md`).
 *
 * Отдельная от [Release] сетевая one-shot модель, **не кэшируется в БД** (SQLDelight-схема
 * Фазы 4 намеренно не тронута этим фундаментом Фазы 7 — только базовая карточка [Release]
 * персистится через `ReleaseCacheStore`). Источник — `GET release/{id}?extended_mode=true`
 * (см. `ReleaseDto`, `docs/api/samples/release_186_extended.json`).
 *
 * [release] несёт все ~20 полей базовой карточки (заголовок, постер, жанры, год, статус,
 * список пользователя и т.п.), чтобы UI Title Detail не дублировал их отдельными полями здесь.
 */
data class ReleaseDetails(
    val release: Release,
    // --- Метаданные ---
    val studio: String? = null,
    val country: String? = null,
    val author: String? = null,
    val director: String? = null,
    val season: String? = null,
    val releaseDate: String? = null,
    val ageRating: String? = null,
    val duration: Int? = null,
    val category: String? = null,
    val source: String? = null,
    val translators: String? = null,
    val titleAlt: String? = null,
    // --- Медиа ---
    val screenshotUrls: List<String> = emptyList(),
    // --- Голосование ---
    // Счётчики 1★..5★, ровно 5 элементов (индекс 0 = 1 звезда, индекс 4 = 5 звёзд).
    val voteCounts: List<Int> = List(VOTE_BUCKET_COUNT) { 0 },
    val voteCount: Int = 0,
    // Оценка текущего пользователя (1..5), `null`/`0` — не голосовал.
    val yourVote: Int? = null,
    // --- Распределение по спискам сообщества ---
    val communityLists: CommunityListCounts = CommunityListCounts(),
    // --- Похожее ---
    val relatedReleases: List<Release> = emptyList(),
    val recommendedReleases: List<Release> = emptyList(),
    val commentCount: Int = 0,
    val relatedCount: Int = 0,
) {
    companion object {
        const val VOTE_BUCKET_COUNT: Int = 5
    }
}

/**
 * Распределение пользователей Anixart по спискам конкретного релиза (гистограмма/donut на
 * Title Detail). Источник — `watching_count`/`plan_count`/`completed_count`/`hold_on_count`/
 * `dropped_count`/`favorites_count`/`collection_count` в `ReleaseDto` (подтверждено
 * `docs/api/samples/release_186_extended.json`, см. P0.T3 в плане — вопрос P7.T10 закрыт).
 */
data class CommunityListCounts(
    val watching: Int = 0,
    val plan: Int = 0,
    val completed: Int = 0,
    val holdOn: Int = 0,
    val dropped: Int = 0,
    val favorites: Int = 0,
    val collection: Int = 0,
)
