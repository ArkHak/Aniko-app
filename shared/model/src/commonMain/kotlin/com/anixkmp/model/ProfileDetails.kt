package com.anixkmp.model

/**
 * Фаза 7 — «Профиль»: детальная карточка своего профиля.
 *
 * MVP-подмножество полей decompiled `database/entity/profile/Profile.java` (у него ~80 полей) —
 * сюда взято только то, что нужно для просмотра своего профиля (аватар/ник/статистика/статус
 * бана-спонсорства). Осознанно не включены темы (`theme*`), соцсети (`vkPage`/`tgPage`/...),
 * превью-списки (`friendsPreview`/`commentsPreview`/...) и `friendStatus` — вне объёма Фазы 7 MVP.
 */
data class ProfileDetails(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
    val status: String?,
    val isSponsor: Boolean,
    val sponsorshipExpires: Long?,
    val isBanned: Boolean,
    val isPermBanned: Boolean,
    val banReason: String?,
    val banExpires: Long?,
    val privilegeLevel: Int,
    val ratingScore: Int,
    val badgeName: String?,
    val badgeUrl: String?,
    val watchingCount: Int,
    val planCount: Int,
    val completedCount: Int,
    val holdOnCount: Int,
    val droppedCount: Int,
    val favoriteCount: Int,
    val commentCount: Int,
    val collectionCount: Int,
    val videoCount: Int,
    val friendCount: Int,
    val watchedEpisodeCount: Int,
    val watchedTime: Long,
    val registerDate: Long,
    val lastActivityTime: Long,
    val isOnline: Boolean,
    val isVerified: Boolean,
)
