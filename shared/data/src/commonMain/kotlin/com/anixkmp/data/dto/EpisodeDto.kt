package com.anixkmp.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `TypesResponse` — `GET episode/{releaseId}`. */
@Serializable
data class TypesResponseDto(
    override val code: Int = 0,
    val types: List<EpisodeTypeDto> = emptyList(),
) : ApiCodeAware

@Serializable
data class EpisodeTypeDto(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
    val workers: List<String> = emptyList(),
)

/** `SourcesResponse` — `GET episode/{releaseId}/{typeId}`. */
@Serializable
data class SourcesResponseDto(
    override val code: Int = 0,
    val sources: List<EpisodeSourceDto> = emptyList(),
) : ApiCodeAware

@Serializable
data class EpisodeSourceDto(
    val id: Int = 0,
    /** Человекочитаемое название вида «КОДиК HD» — для показа пользователю, не для логики. */
    val name: String? = null,
    /**
     * `[TODO: verify live / R2-R3]` Машинный ключ источника (`kodik`, `sibnet`, ...).
     *
     * Реального имени поля мы пока не знаем — `SourcesResponse` не сверен с живым API.
     * Заглушка стоит здесь, чтобы после верификации хватило поправить `@SerialName`,
     * не трогая ни маппер, ни домен. Сейчас поле приходит `null`, работает fallback по [name].
     */
    @SerialName("source_key") val sourceKey: String? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
)

/** `EpisodeResponse` — `GET episode/{releaseId}/{typeId}/{sourceId}`. */
@Serializable
data class EpisodesResponseDto(
    override val code: Int = 0,
    val episodes: List<EpisodeDto> = emptyList(),
) : ApiCodeAware

@Serializable
data class EpisodeDto(
    val position: Int = 0,
    val name: String? = null,
    @SerialName("is_watched") val isWatched: Boolean = false,
)

/**
 * `EpisodeTargetResponse` — `GET episode/target/{releaseId}/{sourceId}/{position}`.
 *
 * ЭТО КЛЮЧЕВОЙ DTO для плеера и одновременно самый неопределённый:
 * `[TODO: verify live]` — неизвестно, отдаёт ли сервер прямую ссылку на видео
 * или embed-страницу стороннего плеера (kodik/sibnet/...). См. `docs/api/ENDPOINTS.md`,
 * пункт 2 раздела «Что осталось сделать».
 */
@Serializable
data class EpisodeTargetResponseDto(
    override val code: Int = 0,
    val episode: EpisodeTargetDto? = null,
) : ApiCodeAware

@Serializable
data class EpisodeTargetDto(
    val position: Int = 0,
    val name: String? = null,
    /** Ссылка: либо прямой поток, либо iframe-страница — определяется по хосту. */
    val url: String? = null,
)
