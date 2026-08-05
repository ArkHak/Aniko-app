package com.anixkmp.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO релиза.
 *
 * ВНИМАНИЕ: имена полей — предварительные, взяты из паттернов Anixart-клиентов и
 * ещё не сверены с реальным трафиком. Это пункт R2/R3 из `docs/api/ENDPOINTS.md`:
 * перед первым живым запросом обязательно сверить каждое поле с ответом сервера.
 * `ignoreUnknownKeys = true` (см. `AnixJson`) гарантирует, что расхождения не уронят парсинг,
 * но приведут к `null` в модели.
 */
@Serializable
data class ReleaseDto(
    val id: Int = 0,
    @SerialName("title_ru") val titleRu: String? = null,
    @SerialName("title_original") val titleOriginal: String? = null,
    val image: String? = null,
    val description: String? = null,
    val year: String? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    @SerialName("episodes_released") val episodesReleased: Int? = null,
    val grade: Double? = null,
    val status: NamedRefDto? = null,
    val genres: String? = null,
    /** Статус в списке текущего пользователя: 1..5, см. `ListStatus`. */
    @SerialName("profile_list_status") val profileListStatus: Int? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
)

/** Частый в API вид `{ id, name }`. */
@Serializable
data class NamedRefDto(
    val id: Int = 0,
    val name: String? = null,
)

/** `ReleaseResponse` — `GET release/{r_id}`. */
@Serializable
data class ReleaseResponseDto(
    override val code: Int = 0,
    val release: ReleaseDto? = null,
) : ApiCodeAware
