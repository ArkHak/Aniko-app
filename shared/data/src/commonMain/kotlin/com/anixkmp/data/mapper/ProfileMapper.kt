package com.anixkmp.data.mapper

import com.anixkmp.data.dto.ProfileDetailsDto
import com.anixkmp.data.dto.ProfilePreferenceResponseDto
import com.anixkmp.model.FriendRequestVisibility
import com.anixkmp.model.PrivacyVisibility
import com.anixkmp.model.ProfileDetails
import com.anixkmp.model.ProfilePrivacy
import com.anixkmp.network.ApiConfig

/**
 * `Long` -> `Int` для `privilegeLevel`/`watchedEpisodeCount` — в decompiled `Profile.java` оба
 * поля объявлены `long`, но на практике это счётчики небольшого порядка (уровень привилегий,
 * число просмотренных серий), поэтому в domain-модели [ProfileDetails] они сознательно сужены
 * до `Int` по заданию Фазы 7. `[TODO: verify live]` — если у активного пользователя счётчик
 * серий когда-нибудь превысит `Int.MAX_VALUE`, здесь произойдёт усечение.
 */
fun ProfileDetailsDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): ProfileDetails = ProfileDetails(
    id = id,
    login = login,
    avatarUrl = avatar?.takeIf { it.isNotBlank() }?.toAbsoluteUrl(staticBaseUrl),
    status = status,
    isSponsor = isSponsor,
    sponsorshipExpires = sponsorshipExpires,
    isBanned = isBanned,
    isPermBanned = isPermBanned,
    banReason = banReason,
    banExpires = banExpires,
    privilegeLevel = privilegeLevel.toInt(),
    ratingScore = ratingScore,
    badgeName = badgeName,
    badgeUrl = badgeUrl,
    watchingCount = watchingCount,
    planCount = planCount,
    completedCount = completedCount,
    holdOnCount = holdOnCount,
    droppedCount = droppedCount,
    favoriteCount = favoriteCount,
    commentCount = commentCount,
    collectionCount = collectionCount,
    videoCount = videoCount,
    friendCount = friendCount,
    watchedEpisodeCount = watchedEpisodeCount.toInt(),
    watchedTime = watchedTime,
    registerDate = registerDate,
    lastActivityTime = lastActivityTime,
    isOnline = isOnline,
    isVerified = isVerified,
)

fun ProfilePreferenceResponseDto.toDomain(): ProfilePrivacy = ProfilePrivacy(
    stats = PrivacyVisibility.fromApiValue(privacyStats),
    counts = PrivacyVisibility.fromApiValue(privacyCounts),
    social = PrivacyVisibility.fromApiValue(privacySocial),
    friendRequests = FriendRequestVisibility.fromApiValue(privacyFriendRequests),
    isIncognito = isIncognito,
)
