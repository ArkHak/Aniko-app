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
 *
 * Фаза 7 (P7.T7-T13): добавлены поля расширенной карточки (`extended_mode=true`), сверены
 * с `docs/api/samples/release_186_extended.json`. Два расхождения с типом в
 * `ReleaseDetails` (`shared/model`), задокументированные здесь намеренно:
 * - `age_rating` в JSON — `Int` (живьём `5`), а не строка; таблица расшифровки значений нигде
 *   не задокументирована (ни статически, ни вживую) — маппер (`ReleaseMapper.toReleaseDetails`)
 *   конвертирует в `String` через `toString()`. `[TODO: verify live]` — расшифровать реальные
 *   значения enum'а, если он вообще есть.
 * - `season` в JSON — `Int` (живьём `4`, порядковый номер сезона), маппер конвертирует в
 *   `String` через `toString()`, т.к. `ReleaseDetails.season` типизирован строкой по заданию.
 * - Также присутствует легаси-дубль `comments_count` рядом с `comment_count` (оба `0` в сэмпле,
 *   назначение различия не подтверждено) — используется только `comment_count`, как в задании.
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
    // --- Расширенные поля (Фаза 7, P7.T7-T13) — сверены с `docs/api/samples/release_186_extended.json` ---
    val poster: String? = null,
    val source: String? = null,
    val country: String? = null,
    val director: String? = null,
    val author: String? = null,
    val translators: String? = null,
    val studio: String? = null,
    val category: NamedRefDto? = null,
    val duration: Int? = null,
    val season: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("age_rating") val ageRating: Int? = null,
    @SerialName("title_alt") val titleAlt: String? = null,
    @SerialName("screenshot_images") val screenshotImages: List<String> = emptyList(),
    @SerialName("vote_1_count") val vote1Count: Int = 0,
    @SerialName("vote_2_count") val vote2Count: Int = 0,
    @SerialName("vote_3_count") val vote3Count: Int = 0,
    @SerialName("vote_4_count") val vote4Count: Int = 0,
    @SerialName("vote_5_count") val vote5Count: Int = 0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("your_vote") val yourVote: Int? = null,
    @SerialName("favorites_count") val favoritesCount: Int = 0,
    @SerialName("watching_count") val watchingCount: Int = 0,
    @SerialName("plan_count") val planCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0,
    @SerialName("hold_on_count") val holdOnCount: Int = 0,
    @SerialName("dropped_count") val droppedCount: Int = 0,
    @SerialName("collection_count") val collectionCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("related_count") val relatedCount: Int = 0,
    @SerialName("related_releases") val relatedReleases: List<ReleaseDto> = emptyList(),
    @SerialName("recommended_releases") val recommendedReleases: List<ReleaseDto> = emptyList(),
    @Serializable(with = LastViewEpisodeSerializer::class)
    @SerialName("last_view_episode")
    val lastViewEpisode: Int? = null,
    @SerialName("last_view_timestamp") val lastViewTimestamp: Long? = null,
    @Serializable(with = EpisodeLastUpdateSerializer::class)
    @SerialName("episode_last_update")
    val episodeLastUpdate: Long? = null,
    @SerialName("is_viewed") val isViewed: Boolean = false,
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
