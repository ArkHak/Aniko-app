package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO релиза.
 *
 * Сверено (R3, `docs/api/ENDPOINTS.md`): двумя независимыми способами — статически, по
 * `@JsonProperty` в decompiled `database/entity/release/Release.java` (Jackson, не Gson/Moshi —
 * значит имя JSON-поля равно либо аннотации, либо имени свойства as-is), и вживую curl'ом
 * (`GET discover/watching/0`, `GET release/{id}?extended_mode=true`, `POST search/releases/0`,
 * сэмплы в `docs/api/samples/`). Все поля ниже совпали в обоих источниках 1:1.
 *
 * Важные находки живой проверки:
 * - `image` в реальных ответах уже абсолютный URL (`https://s.anixmirai.com/...`), а не
 *   относительный путь — `toAbsoluteUrl()` в мэппере это уже корректно обрабатывает (no-op
 *   на строках с `http`), править не нужно.
 * - `status` (вложенный `{id, name}`) присутствует в `discover/watching` и `release/{id}`, но
 *   ОТСУТСТВУЕТ в ответе `search/releases/{page}` — там есть только плоский `status_id`,
 *   который **не совпадает** по значению с `status.id` в других ответах (наблюдалось
 *   `status.id=1` при `status_id=0` у одного и того же релиза), так что использовать
 *   `status_id` как замену вложенному `status` нельзя. Для результатов поиска
 *   `Release.status` в домене будет `UNKNOWN` — задокументированное ограничение MVP.
 * - `id` в decompiled-модели объявлен как `Long` (Room PK), а не `Int`; во всех живых
 *   сэмплах значения укладываются в Int32 (макс. наблюдалось `8013036` у `Interesting.id`).
 *   Оставлено как `Int` (см. `ReleaseId` в `shared/model`) — `[TODO: verify live]` если когда-то
 *   встретится реальный id вне диапазона Int32, конвертировать на `Long` придётся везде.
 *
 * `ignoreUnknownKeys = true` (см. `AnixJson`) по-прежнему гарантирует, что появление новых
 * полей в API не уронит парсинг.
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

/**
 * Частый в API вид `{ id, name }` — используется для `status` (`ReleaseStatus.java`) и
 * `category` (`Category.java`). Живая проверка: поля `id`/`name` без `@JsonProperty`,
 * значит совпадают с именем свойства as-is — сериализация верна без `@SerialName`.
 */
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
