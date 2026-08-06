package com.anixkmp.data.dto

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
)

/** `GET profile/{id}` — обёртка с `code` (см. `ProfileResponse.java`, поле `profile` без `@JsonProperty`). */
@Serializable
data class ProfileResponseDto(
    override val code: Int = 0,
    val profile: ProfileDetailsDto? = null,
) : ApiCodeAware
