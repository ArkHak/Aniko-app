package com.aniko.data.repository

import com.aniko.data.api.ReleaseCommentApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.paging.Paginator
import com.aniko.model.ReleaseComment

/**
 * Обёртка над `ReleaseCommentApi` (P3.T12) для экрана комментариев (P7.T13).
 * Только чтение + голосование — добавление/редактирование/удаление комментариев не входит
 * в объём фундамента Фазы 7, `ReleaseCommentApi` уже реализует эти методы напрямую, если
 * следующим трекам понадобится их прокинуть.
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
