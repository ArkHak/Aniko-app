package com.anixkmp.model

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
)

/** Статус выхода релиза. */
enum class ReleaseStatus {
    ANNOUNCE,
    ONGOING,
    FINISHED,
    UNKNOWN,
}
