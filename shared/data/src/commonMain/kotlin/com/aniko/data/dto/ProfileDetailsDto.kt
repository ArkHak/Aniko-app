package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `ProfileDetailsDto` — MVP-подмножество decompiled
 * `database/entity/profile/Profile.java` (см. `profile/{id}`, обёрнут в [ProfileResponseDto]).
 *
 * Имена полей сверены построчно с getter'ами `Profile.java`: где на getter'е висит
 * `@JsonProperty("snake_case")` — используем это имя (`ban_expires`, `privilege_level`,
 * `rating_score`, `*_count`, `watched_episode_count`, `watched_time`, `register_date`,
 * `last_activity_time`, `is_banned`, `is_perm_banned`, `is_sponsor`, `is_online`, `is_verified`,
 * `badge_name`, `badge_url`). Где аннотации НЕТ (`id`, `login`, `avatar`, `status`,
 * `sponsorshipExpires`) — Jackson в этом клиенте сериализует по имени поля as-is (в проекте нет
 * глобальной `PropertyNamingStrategy`, см. `DaggerApp_HiltComponents_SingletonC$SingletonCImpl`),
 * поэтому `sponsorshipExpires` — единственное здесь поле без `_` в JSON, это не опечатка.
 */
@Serializable
data class ProfileDetailsDto(
    val id: Long,
    val login: String,
    val avatar: String? = null,
    val status: String? = null,
    @SerialName("is_sponsor") val isSponsor: Boolean = false,
    val sponsorshipExpires: Long? = null,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("is_perm_banned") val isPermBanned: Boolean = false,
    @SerialName("ban_reason") val banReason: String? = null,
    @SerialName("ban_expires") val banExpires: Long? = null,
    @SerialName("privilege_level") val privilegeLevel: Long = 0,
    @SerialName("rating_score") val ratingScore: Int = 0,
    @SerialName("badge_name") val badgeName: String? = null,
    @SerialName("badge_url") val badgeUrl: String? = null,
    @SerialName("watching_count") val watchingCount: Int = 0,
    @SerialName("plan_count") val planCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0,
    @SerialName("hold_on_count") val holdOnCount: Int = 0,
    @SerialName("dropped_count") val droppedCount: Int = 0,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("collection_count") val collectionCount: Int = 0,
    @SerialName("video_count") val videoCount: Int = 0,
    @SerialName("friend_count") val friendCount: Int = 0,
    @SerialName("watched_episode_count") val watchedEpisodeCount: Long = 0,
    @SerialName("watched_time") val watchedTime: Long = 0,
    @SerialName("register_date") val registerDate: Long = 0,
    @SerialName("last_activity_time") val lastActivityTime: Long = 0,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
    // --- Фаза 9 (P9.T8/T9/T11) — поля, которые ответ отдавал и раньше, но они не мапились ---
    @SerialName("watch_dynamics") val watchDynamics: List<WatchDynamicsDto> = emptyList(),
    @SerialName("preferred_genres") val preferredGenres: List<PreferredEntryDto> = emptyList(),
    /**
     * «Недавно смотрели» — 5 последних релизов, приходят прямо в `profile/{id}` полным
     * [ReleaseDto] (живая проверка 2026-08-18). Поэтому отдельный запрос `GET history/{page}`
     * ленте профиля не нужен — он остаётся источником полноценной пагинируемой истории в
     * `LibraryRepository`.
     *
     * В отличие от `watch_dynamics`, порядок здесь осмысленный: сервер отдаёт список уже
     * отсортированным по `last_view_timestamp` по убыванию (проверено живьём), поэтому маппер
     * ничего не переупорядочивает — первым в ленте идёт самый свежий просмотр.
     */
    val history: List<ReleaseDto> = emptyList(),
)

/**
 * Точка `watch_dynamics` из `profile/{id}`.
 *
 * Живая проверка 2026-08-18 (`GET https://api-s.anixsekai.com/profile/1000001`, без токена):
 * приходит ровно 31 объект вида `{"id": 83955914, "day": 31, "count": 18, "timestamp": 1769807382}`
 * — кольцевой буфер по одному слоту на число месяца, в произвольном порядке и с «протухшими»
 * слотами (у неактивных дней остаётся timestamp прошлого месяца). Порядок элементов в массиве
 * бессмысленен, хронологию задаёт только `timestamp`. Поле `id` — серверный PK записи, домену
 * не нужен и не мапится.
 */
@Serializable
data class WatchDynamicsDto(
    val day: Int = 0,
    val count: Int = 0,
    val timestamp: Long = 0,
)

/**
 * Элемент `preferred_genres` (и однотипных `preferred_audiences`/`preferred_themes`, которые
 * v1 не показывает) — `{"name": "экшен", "percentage": 10}`. Проценты считает сервер, локального
 * агрегата по истории не требуется (вердикт P0.T4).
 */
@Serializable
data class PreferredEntryDto(
    val name: String = "",
    val percentage: Int = 0,
)

/** `GET profile/{id}` — обёртка с `code` (см. `ProfileResponse.java`, поле `profile` без `@JsonProperty`). */
@Serializable
data class ProfileResponseDto(
    override val code: Int = 0,
    val profile: ProfileDetailsDto? = null,
) : ApiCodeAware
