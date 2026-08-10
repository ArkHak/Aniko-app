package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `FilterRequest` — тело `POST filter/{page}` (расширенный фильтр каталога).
 *
 * Сверено двумя способами:
 * - статически, decompiled `network/request/filter/FilterRequest.java` — имена полей и типы
 *   ниже взяты 1:1 оттуда;
 * - вживую (2026-08-10): `POST https://api-s.anixsekai.com/filter/0` с телом
 *   `{"sort":0,"genres":[],"types":[],"age_ratings":[],"profile_list_exclusions":[]}`,
 *   `Content-Type: application/json` → HTTP 200, обычный `PageableResponse<Release>`
 *   (`code`, `content`, `current_page`, `total_page_count`, `total_count`) — переиспользуется
 *   [PageableResponseDto] и [ReleaseDto], отдельного DTO ответа не заводится.
 *
 * Особенности полей:
 * - `sort`: 0=дата обновления убыв., 1=оценка убыв., 2=год убыв., 3=популярность убыв.,
 *   4=дата обновления возр., 5=оценка возр., 6=год возр., 7=популярность возр.
 * - `genresMode`: 0=ALL (все жанры), 1=ANY (любой из жанров), 2=EXCLUDE.
 * - `ageRatings` — судя по именам констант в декомпиле (`LESS_THAN_13=1`, `MORE_THAN_13=2`,
 *   `MORE_THAN_26=3`, `MORE_THAN_100=4`), это, вероятно, диапазоны **количества серий**, а не
 *   возрастной рейтинг, несмотря на название поля `age_ratings` в JSON и декомпиле — это
 *   наблюдение из статического анализа, назначение поля не подтверждено вживую и не
 *   переименовывается здесь, чтобы не разойтись с реальным JSON-ключом сервера.
 */
@Serializable
data class FilterRequestDto(
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("status_id") val statusId: Long? = null,
    @SerialName("start_year") val startYear: Int? = null,
    @SerialName("end_year") val endYear: Int? = null,
    val studio: String? = null,
    val source: String? = null,
    @SerialName("episodes_from") val episodesFrom: Int? = null,
    @SerialName("episodes_to") val episodesTo: Int? = null,
    val sort: Int = 0,
    val country: String? = null,
    val season: Int? = null,
    @SerialName("episode_duration_from") val episodeDurationFrom: Int? = null,
    @SerialName("episode_duration_to") val episodeDurationTo: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("profile_list_exclusions") val profileListExclusions: List<Int> = emptyList(),
    val types: List<Long> = emptyList(),
    @SerialName("age_ratings") val ageRatings: List<Int> = emptyList(),
    @SerialName("is_genres_exclude_mode_enabled") val isGenresExcludeModeEnabled: Boolean = false,
    @SerialName("genres_mode") val genresMode: Int? = null,
) {
    companion object {
        // sort
        const val SORT_UPDATED_DESC: Int = 0
        const val SORT_GRADE_DESC: Int = 1
        const val SORT_YEAR_DESC: Int = 2
        const val SORT_POPULARITY_DESC: Int = 3
        const val SORT_UPDATED_ASC: Int = 4
        const val SORT_GRADE_ASC: Int = 5
        const val SORT_YEAR_ASC: Int = 6
        const val SORT_POPULARITY_ASC: Int = 7

        // genresMode
        const val GENRES_MODE_ALL: Int = 0
        const val GENRES_MODE_ANY: Int = 1
        const val GENRES_MODE_EXCLUDE: Int = 2

        // ageRatings — см. KDoc класса: по декомпилу похоже на диапазоны кол-ва серий,
        // а не возрастной рейтинг.
        const val AGE_RATING_LESS_THAN_13: Int = 1
        const val AGE_RATING_MORE_THAN_13: Int = 2
        const val AGE_RATING_MORE_THAN_26: Int = 3
        const val AGE_RATING_MORE_THAN_100: Int = 4
    }
}
