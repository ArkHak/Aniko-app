package com.aniko.data.mapper

import com.aniko.data.dto.BadgeDto
import com.aniko.data.dto.ProfileDetailsDto
import com.aniko.data.dto.ProfilePreferenceResponseDto
import com.aniko.model.Achievement
import com.aniko.model.FriendRequestVisibility
import com.aniko.model.PreferredGenre
import com.aniko.model.PrivacyVisibility
import com.aniko.model.ProfileDetails
import com.aniko.model.ProfilePrivacy
import com.aniko.model.WatchDynamicsPoint
import com.aniko.network.ApiConfig

/**
 * `Long` -> `Int` для `privilegeLevel`/`watchedEpisodeCount` — в decompiled `Profile.java` оба
 * поля объявлены `long`, но на практике это счётчики небольшого порядка (уровень привилегий,
 * число просмотренных серий), поэтому в domain-модели [ProfileDetails] они сознательно сужены
 * до `Int` по заданию Фазы 7. `[TODO: verify live]` — если у активного пользователя счётчик
 * серий когда-нибудь превысит `Int.MAX_VALUE`, здесь произойдёт усечение.
 */
fun ProfileDetailsDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): ProfileDetails =
    ProfileDetails(
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
        // Сортировка по timestamp — здесь, а не у потребителя: порядок элементов в самом массиве
        // ответа хронологии не отражает, см. KDoc `WatchDynamicsDto`.
        watchDynamics =
            watchDynamics
                .sortedBy { it.timestamp }
                .map { WatchDynamicsPoint(day = it.day, count = it.count, timestamp = it.timestamp) },
        preferredGenres =
            preferredGenres
                .filter { it.name.isNotBlank() }
                .map { PreferredGenre(name = it.name, percentage = it.percentage) },
        recentlyWatched = history.map { it.toDomain(staticBaseUrl) },
    )

/** `Badge.TYPE_ANIMATION = 1` (decompiled) — всё, что не `1`, трактуется как статичное. */
fun BadgeDto.toDomain(): Achievement =
    Achievement(
        id = id,
        name = name,
        badgeUrl = imageUrl,
        isAnimated = type == 1,
        earnedAt = timestamp,
    )

fun ProfilePreferenceResponseDto.toDomain(): ProfilePrivacy =
    ProfilePrivacy(
        stats = PrivacyVisibility.fromApiValue(privacyStats),
        counts = PrivacyVisibility.fromApiValue(privacyCounts),
        social = PrivacyVisibility.fromApiValue(privacySocial),
        friendRequests = FriendRequestVisibility.fromApiValue(privacyFriendRequests),
        isIncognito = isIncognito,
    )
