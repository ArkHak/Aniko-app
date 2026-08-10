package com.aniko.data.mapper

import com.aniko.data.dto.ProfileCompactDto
import com.aniko.data.dto.ReleaseCommentDto
import com.aniko.model.CommentAuthor
import com.aniko.model.ReleaseComment
import com.aniko.network.ApiConfig

fun ProfileCompactDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): CommentAuthor =
    CommentAuthor(
        id = id.toLong(),
        login = login,
        avatarUrl = avatar?.takeIf { it.isNotBlank() }?.toAbsoluteUrl(staticBaseUrl),
        banExpires = banExpires,
        banReason = banReason,
        privilegeLevel = privilegeLevel,
        badgeId = badgeId,
        badgeName = badgeName,
        badgeType = badgeType,
        badgeUrl = badgeUrl,
        isBanned = isBanned,
        isSponsor = isSponsor,
        isVerified = isVerified,
    )

fun ReleaseCommentDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): ReleaseComment =
    ReleaseComment(
        id = id,
        author = profile.toDomain(staticBaseUrl),
        message = message,
        timestamp = timestamp,
        type = type,
        vote = vote,
        parentCommentId = parentCommentId,
        voteCount = voteCount,
        likesCount = likesCount,
        isSpoiler = isSpoiler,
        isEdited = isEdited,
        isDeleted = isDeleted,
        isReply = isReply,
        replyCount = replyCount,
        canLike = canLike,
        postedAtEpisode = postedAtEpisode,
        release = release?.toDomain(staticBaseUrl),
    )
