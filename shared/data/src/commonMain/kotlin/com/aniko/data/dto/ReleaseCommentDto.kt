package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `ProfileCompact` — короткий профиль автора комментария.
 *
 * Источник: decompiled `database/entity/profile/ProfileCompact.java` (Jackson, `@JsonProperty`
 * только на полях, где имя JSON расходится с camelCase-именем свойства). Сверено 1:1 с живым
 * `profile{}` внутри `ReleaseComment` (2026-08-10, `GET release/comment/all/186/0?sort=0`).
 *
 * НЕ путать с [ProfileDto]/`ProfileDetailsDto` — это отдельная, более узкая форма профиля,
 * которую API отдаёт вложенно в комментариях (и, предположительно, в `release/comment/votes/…`,
 * см. KDoc [ReleaseCommentApi.votes]).
 */
@Serializable
data class ProfileCompactDto(
    val id: Int = 0,
    val login: String = "",
    val avatar: String? = null,
    @SerialName("ban_expires") val banExpires: Long = 0,
    @SerialName("ban_reason") val banReason: String? = null,
    @SerialName("privilege_level") val privilegeLevel: Long = 0,
    @SerialName("badge_id") val badgeId: Long? = null,
    @SerialName("badge_name") val badgeName: String? = null,
    @SerialName("badge_type") val badgeType: Int? = null,
    @SerialName("badge_url") val badgeUrl: String? = null,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("is_sponsor") val isSponsor: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

/**
 * `ReleaseComment` — комментарий к релизу.
 *
 * Источник: decompiled `database/entity/comment/Comment.java` (базовый generic-класс,
 * `id/profile/parentCommentId/message/voteCount/likesCount/timestamp/isSpoiler/isEdited/
 * isDeleted/isReply/type/replyCount/canLike/vote`) + `database/entity/comment/release/
 * ReleaseComment.java` (добавляет `release`, `postedAtEpisode`). Оба помечены
 * `@JsonIgnoreProperties(ignoreUnknown = true)` — сервер может присылать больше полей.
 *
 * Сверено вживую 2026-08-10 (`GET release/comment/all/186/0?sort=0` → HTTP 200,
 * `PageableResponse<ReleaseComment>`) — форма полей совпала 1:1 с decompile.
 *
 * `release` — намеренно nullable, а не обязательное поле: живой сэмпл `release/comment/all/…`
 * всегда возвращает его вложенным, но пути `replies`/`votes`/`all/profile/{p_id}` не
 * перепроверены живьём в этой сессии (требует отдельной живой проверки). `ignoreUnknownKeys` +
 * `coerceInputValues` в `AnixJson` всё равно не уронят парсинг, если поле когда-то пропадёт.
 */
@Serializable
data class ReleaseCommentDto(
    val id: Long = 0,
    val profile: ProfileCompactDto = ProfileCompactDto(),
    val message: String = "",
    val timestamp: Long = 0,
    val type: Int = 0,
    val vote: Int = 0,
    @SerialName("parent_comment_id") val parentCommentId: Long? = null,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("is_spoiler") val isSpoiler: Boolean = false,
    @SerialName("is_edited") val isEdited: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("is_reply") val isReply: Boolean = false,
    @SerialName("reply_count") val replyCount: Long = 0,
    @SerialName("can_like") val canLike: Boolean = true,
    @SerialName("posted_at_episode") val postedAtEpisode: Int? = null,
    val release: ReleaseDto? = null,
)

/**
 * `CommentAddRequest` — тело `POST release/comment/add/{releaseId}`.
 *
 * Источник: decompiled `network/request/comments/CommentAddRequest.java` — поля объявлены БЕЗ
 * `@JsonProperty` (Jackson-конвертер, как и у `SearchRequest.java`, см. `SearchRequestDto`),
 * значит имя JSON-поля равно имени Kotlin-свойства as-is: `parentCommentId`, `replyToProfileId`,
 * `message`, `spoiler` — camelCase, не snake_case. Тело не отправлялось вживую в этой сессии
 * (эндпоинт мутирующий, не хотелось оставлять тестовые комментарии в проде) — требует отдельной
 * живой проверки при первом реальном использовании.
 */
@Serializable
data class CommentAddRequestDto(
    val parentCommentId: Long? = null,
    val replyToProfileId: Long? = null,
    val message: String,
    val spoiler: Boolean = false,
)

/**
 * `CommentEditRequest` — тело `POST release/comment/edit/{commentId}`.
 *
 * Источник: decompiled `network/request/comments/CommentEditRequest.java` — те же соображения
 * по неймингу, что и у [CommentAddRequestDto]: `message`/`spoiler` camelCase, без `@JsonProperty`.
 */
@Serializable
data class CommentEditRequestDto(
    val message: String,
    val spoiler: Boolean = false,
)

/**
 * `CommentProcessRequest` — тело `POST release/comment/process/{commentId}` — модераторское
 * действие (скрыть/удалить/забанить автора за конкретный комментарий).
 *
 * Источник: decompiled `network/request/comments/CommentProcessRequest.java`. В отличие от
 * [CommentAddRequestDto]/[CommentEditRequestDto], три булевых поля ЕСТЬ с явным `@JsonProperty`
 * (`is_spoiler`, `is_deleted`, `is_banned`) — снейк-кейс; остальные (`message`, `reason`,
 * `banReason`, `banExpires`) объявлены без аннотации, значит camelCase as-is.
 *
 * Не для обычных пользователей — обычный токен, скорее всего, получит `code` типа "недостаточно
 * прав" (точный код не документирован статически, модерация не тестировалась вживую).
 */
@Serializable
data class CommentProcessRequestDto(
    val message: String? = null,
    val reason: String? = null,
    val banReason: String? = null,
    val banExpires: Long? = null,
    @SerialName("is_spoiler") val isSpoiler: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("is_banned") val isBanned: Boolean = false,
)

/**
 * `CommentAddResponse<T>` — ответ `POST release/comment/add/{releaseId}`.
 *
 * Коды ошибок (decompiled `CommentAddResponse.java`, константы класса):
 * `2` = EMBEDDABLE_NOT_FOUND (релиз не найден), `3` = COMMENT_NOT_FOUND (родительский комментарий
 * для reply не найден), `4` = PROFILE_NOT_FOUND (`replyToProfileId` не найден), `5` =
 * COMMENT_IS_TOO_SHORT, `6` = COMMENT_IS_TOO_LONG, `7` = COMMENT_LIMIT_REACHED, `8` =
 * IN_BLOCKLIST (автор родительского комментария заблокировал текущего пользователя).
 */
@Serializable
data class CommentAddResponseDto<T>(
    override val code: Int = 0,
    val comment: T? = null,
) : ApiCodeAware

/**
 * `CommentEditResponse` — ответ `POST release/comment/edit/{commentId}`, без полезной нагрузки.
 *
 * Коды ошибок (decompiled `CommentEditResponse.java`): `2` = COMMENT_NOT_FOUND, `3` =
 * COMMENT_IS_TOO_SHORT, `4` = COMMENT_IS_TOO_LONG, `5` = COMMENT_NOT_OWNED (комментарий чужой),
 * `6` = COMMENT_WAS_DELETED, `7` = EMBEDDABLE_NOT_FOUND (релиз комментария удалён).
 */
@Serializable
data class CommentEditResponseDto(
    override val code: Int = 0,
) : ApiCodeAware

/**
 * `CommentDeleteResponse` — ответ `GET release/comment/delete/{commentId}`, без полезной нагрузки.
 *
 * Коды ошибок (decompiled `CommentDeleteResponse.java`): `2` = COMMENT_NOT_FOUND, `3` =
 * COMMENT_NOT_OWNED.
 */
@Serializable
data class CommentDeleteResponseDto(
    override val code: Int = 0,
) : ApiCodeAware
