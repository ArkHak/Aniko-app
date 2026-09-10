package com.aniko.data.repository

import com.aniko.data.api.ReleaseCommentApi
import com.aniko.data.dto.CommentAddRequestDto
import com.aniko.data.mapper.toDomain
import com.aniko.data.paging.Paginator
import com.aniko.model.ReleaseComment

/**
 * Обёртка над `ReleaseCommentApi` (P3.T12) для экрана комментариев (P7.T13).
 * Чтение, голосование и публикация нового комментария (P16.T17, `addComment`) — редактирование/
 * удаление не входят в объём этого трека, `ReleaseCommentApi` уже реализует эти методы напрямую,
 * если следующим трекам понадобится их прокинуть.
 */
class CommentRepository(
    private val commentApi: ReleaseCommentApi,
) {
    /** Готовый пагинатор комментариев релиза. `sort` — см. KDoc `ReleaseCommentApi.comments`. */
    fun commentsPaginator(
        releaseId: Int,
        sort: Int = 0,
    ): Paginator<ReleaseComment> =
        Paginator { page ->
            commentApi.comments(releaseId.toLong(), page, sort).toDomain { it.toDomain() }
        }

    /** `GET release/comment/vote/{commentId}/{vote}` — лайк/дизлайк комментария. */
    suspend fun vote(
        commentId: Long,
        vote: Int,
    ) {
        commentApi.vote(commentId, vote)
    }

    /**
     * `POST release/comment/add/{releaseId}` — публикация нового комментария (P16.T17). Ответы,
     * реплаи (`parentCommentId`/`replyToProfileId`) и спойлер-флаг не входят в объём этой задачи
     * (только композер верхнеуровневого комментария на `ReleaseCommentsScreen`) — сигнатура
     * умышленно принимает только `message`, а не весь [CommentAddRequestDto].
     */
    suspend fun addComment(
        releaseId: Int,
        message: String,
    ) {
        commentApi.add(releaseId.toLong(), CommentAddRequestDto(message = message))
    }

    /**
     * Первые [limit] комментариев релиза — инлайн-превью на Title Detail (P13.T12), НЕ полный
     * список: та же страница 0/`sort=0`, что и первая страница [commentsPaginator], но без
     * собственного [Paginator]-состояния — превью не умеет и не должно грузить следующие страницы,
     * это остаётся за точкой входа «показать все» (`ReleaseCommentsScreen`).
     */
    suspend fun previewComments(
        releaseId: Int,
        limit: Int,
    ): List<ReleaseComment> =
        commentApi
            .comments(releaseId.toLong(), page = 0, sort = 0)
            .toDomain { it.toDomain() }
            .items
            .take(limit)
}
