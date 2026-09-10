package com.aniko.model

/**
 * Короткий профиль автора комментария (или голосовавшего) — то, что API отдаёт вложенно в
 * `ReleaseComment` (и, предположительно, в списке `release/comment/votes/…`), а не полный
 * [Profile]/`ProfileDetails`. См. `ProfileCompactDto` в `shared/data`.
 */
data class CommentAuthor(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
    val banExpires: Long,
    val banReason: String?,
    val privilegeLevel: Long,
    val badgeId: Long?,
    val badgeName: String?,
    val badgeType: Int?,
    val badgeUrl: String?,
    val isBanned: Boolean,
    val isSponsor: Boolean,
    val isVerified: Boolean,
)

/**
 * Комментарий к релизу (`ReleaseCommentApi`, P3.T12).
 */
data class ReleaseComment(
    val id: Long,
    val author: CommentAuthor,
    val message: String,
    val timestamp: Long,
    /** Тип комментария — точная семантика значений не документирована статически. */
    val type: Int,
    /** Голос текущего пользователя за этот комментарий (лайк/дизлайк), `0` — не голосовал. */
    val vote: Int,
    val parentCommentId: Long?,
    val voteCount: Int,
    val likesCount: Int,
    val isSpoiler: Boolean,
    val isEdited: Boolean,
    val isDeleted: Boolean,
    val isReply: Boolean,
    val replyCount: Long,
    val canLike: Boolean,
    /** Серия, к которой привязан комментарий (спойлер-метка), `null` — не привязан. */
    val postedAtEpisode: Int?,
)
