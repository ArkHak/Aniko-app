package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET profile/preference/my` — только privacy-подмножество полей MVP Фазы 7.
 *
 * Имена сверены с getter'ами `ProfilePreferenceResponse.java`: `privacy_stats`,
 * `privacy_counts`, `privacy_social`, `privacy_friend_requests`, `is_incognito`. Остальные поля
 * ответа (`avatar`, `status`, `vkPage`/`tgPage`, `episode_channel_widgets_hidden`, соцсети,
 * смена email/пароля/логина, темы, `badge`, `pinned_section_id` и т.п.) сюда не заведены —
 * игнорируются благодаря `AnixJson { ignoreUnknownKeys = true }` (см. `AnixHttpClient.kt`).
 */
@Serializable
data class ProfilePreferenceResponseDto(
    override val code: Int = 0,
    @SerialName("privacy_stats") val privacyStats: Int = 0,
    @SerialName("privacy_counts") val privacyCounts: Int = 0,
    @SerialName("privacy_social") val privacySocial: Int = 0,
    @SerialName("privacy_friend_requests") val privacyFriendRequests: Int = 0,
    @SerialName("is_incognito") val isIncognito: Boolean = false,
) : ApiCodeAware

/** Тело `POST profile/preference/privacy/{stats,counts,social,friendRequests}/edit`. */
@Serializable
data class PrivacyEditRequestDto(
    val permission: Int,
)
