package com.aniko.model

/** Идентификатор релиза в Anixart. */
typealias ReleaseId = Int

/**
 * Домен-модель релиза (аниме-тайтла).
 *
 * Сознательно уже, чем `ReleaseDto`: сюда попадают только поля, которые
 * реально используются UI. Расширять по мере появления экранов.
 */
data class Release(
    val id: ReleaseId,
    val title: String,
    val originalTitle: String? = null,
    val posterUrl: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val episodesTotal: Int? = null,
    val episodesReleased: Int? = null,
    val grade: Double? = null,
    val status: ReleaseStatus = ReleaseStatus.UNKNOWN,
    val genres: List<String> = emptyList(),
    /** Статус в списке текущего пользователя, `null` — не в списке. */
    val myListStatus: ListStatus? = null,
    val isFavorite: Boolean = false,
    /**
     * Прогресс просмотра и признак «новых серий» — [lastViewEpisode]/[lastViewTimestamp]/
     * [episodeLastUpdate]/[isViewed]. Эти четыре поля НЕ персистятся в `releaseEntity`
     * (SQLDelight-схема Фазы 4 намеренно не тронута этим фундаментом Фазы 7) — после гидрации
     * карточки из локального кэша (`ReleaseCacheStore`) они всегда будут `null`/`false`,
     * реальные значения доступны только сразу после сетевого ответа.
     */
    val lastViewEpisode: Int? = null,
    val lastViewTimestamp: Long? = null,
    val episodeLastUpdate: Long? = null,
    val isViewed: Boolean = false,
)

/** Статус выхода релиза. */
enum class ReleaseStatus {
    ANNOUNCE,
    ONGOING,
    FINISHED,
    UNKNOWN,
}
