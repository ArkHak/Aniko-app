package com.aniko.data.api

import com.aniko.data.dto.CommentAddRequestDto
import com.aniko.data.dto.CommentAddResponseDto
import com.aniko.data.dto.CommentDeleteResponseDto
import com.aniko.data.dto.CommentEditRequestDto
import com.aniko.data.dto.CommentEditResponseDto
import com.aniko.data.dto.CommentProcessRequestDto
import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ProfileCompactDto
import com.aniko.data.dto.ReleaseCommentDto
import com.aniko.data.dto.SimpleResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * `ReleaseCommentApi` — комментарии к релизу (P3.T12, `docs/REELWAVE_PLAN.md`).
 *
 * `token` в query добавлять вручную не нужно — [com.aniko.network.AnixTokenPlugin] дописывает
 * его в каждый запрос автоматически (тот же паттерн, что и у [ReleaseApi]/[SearchApi]).
 *
 * Список методов и путей сверен со decompiled
 * `network/api/ReleaseCommentApi.java` (`docs/api/jadx-out-21/...`). Живьём проверен (2026-08-10)
 * только [comments] (`GET release/comment/all/186/0?sort=0` → HTTP 200,
 * `PageableResponse<ReleaseComment>`) — остальные методы реализованы по декомпилу и ждут
 * отдельной живой перепроверки перед первым реальным использованием.
 */
class ReleaseCommentApi(
    private val client: HttpClient,
) {
    /**
     * `GET release/comment/all/{releaseId}/{page}?sort=&token=` — постраничный список
     * комментариев релиза. Живая проверка (2026-08-10): `sort=0` вернул `HTTP 200` с обычной
     * `PageableResponse<ReleaseComment>`.
     */
    suspend fun comments(
        releaseId: Long,
        page: Int,
        sort: Int = 0,
    ): PageableResponseDto<ReleaseCommentDto> =
        apiCall {
            client
                .get("release/comment/all/$releaseId/$page") {
                    parameter("sort", sort)
                }.body<PageableResponseDto<ReleaseCommentDto>>()
                .requireOk()
        }

    /**
     * `GET release/comment/{releaseId}?token=` — одиночный (не пагинированный) комментарий.
     *
     * Сигнатура в decompile странная: `@Path("releaseId") long releaseId` при пути
     * `release/comment/{releaseId}` — по имени параметра похоже на id релиза, но путь
     * `release/comment/{id}` (без `all`) больше похож по форме на «получить комментарий по его
     * собственному id» (аналогично `release/comment/delete/{commentId}`,
     * `release/comment/edit/{commentId}` рядом). Какой из двух смыслов верен, статически не
     * определить (в decompile метод называется просто `comment`, без другого контекста
     * использования в этой сессии) — реализовано как есть по декомпилу, семантика параметра
     * ждёт отдельной живой перепроверки.
     *
     * Без `.requireOk()`: в отличие от всех остальных методов этого класса, decompiled
     * сигнатура — `Observable<ReleaseComment>` напрямую, БЕЗ обёртки `Response`/`code`
     * (`ReleaseComment` не наследует `ApiCodeAware` — см. `ReleaseCommentDto`), поэтому здесь
     * нечего разворачивать через `requireOk()`.
     */
    suspend fun comment(id: Long): ReleaseCommentDto =
        apiCall {
            client.get("release/comment/$id").body<ReleaseCommentDto>()
        }

    /**
     * `POST release/comment/add/{releaseId}?token=`, body [CommentAddRequestDto].
     * Ошибки — см. KDoc [CommentAddResponseDto].
     */
    suspend fun add(
        releaseId: Long,
        request: CommentAddRequestDto,
    ): CommentAddResponseDto<ReleaseCommentDto> =
        apiCall {
            client
                .post("release/comment/add/$releaseId") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body<CommentAddResponseDto<ReleaseCommentDto>>()
                .requireOk()
        }

    /**
     * `POST release/comment/edit/{commentId}?token=`, body [CommentEditRequestDto].
     * Ошибки — см. KDoc [CommentEditResponseDto].
     */
    suspend fun edit(
        commentId: Long,
        request: CommentEditRequestDto,
    ): CommentEditResponseDto =
        apiCall {
            client
                .post("release/comment/edit/$commentId") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body<CommentEditResponseDto>()
                .requireOk()
        }

    /**
     * `GET release/comment/delete/{commentId}?token=`. Ошибки — см. KDoc [CommentDeleteResponseDto].
     */
    suspend fun delete(commentId: Long): CommentDeleteResponseDto =
        apiCall {
            client
                .get("release/comment/delete/$commentId")
                .body<CommentDeleteResponseDto>()
                .requireOk()
        }

    /** `GET release/comment/vote/{commentId}/{vote}?token=` — лайк/дизлайк комментария. */
    suspend fun vote(
        commentId: Long,
        vote: Int,
    ): SimpleResponseDto =
        apiCall {
            client
                .get("release/comment/vote/$commentId/$vote")
                .body<SimpleResponseDto>()
                .requireOk()
        }

    /** `POST release/comment/replies/{commentId}/{page}?sort=&token=` — ответы на комментарий. */
    suspend fun replies(
        commentId: Long,
        page: Int,
        sort: Int = 0,
    ): PageableResponseDto<ReleaseCommentDto> =
        apiCall {
            client
                .post("release/comment/replies/$commentId/$page") {
                    parameter("sort", sort)
                }.body<PageableResponseDto<ReleaseCommentDto>>()
                .requireOk()
        }

    /**
     * `GET release/comment/votes/{commentId}/{page}?sort=&token=` — профили проголосовавших
     * за комментарий.
     *
     * Decompile объявляет ответ как `PageableResponse<Profile>` (полный профиль, ~80 полей), но
     * это, скорее всего, неточность типизации в оригинальном клиенте (аналогично `profile` внутри
     * самого комментария, который на деле `ProfileCompact`, не полный `Profile`) — по аналогии с
     * остальными вложенными профилями в этом же API реализовано через [ProfileCompactDto] — не
     * перепроверено живым трафиком в этой сессии, ждёт отдельной живой проверки.
     */
    suspend fun votes(
        commentId: Long,
        page: Int,
        sort: Int? = null,
    ): PageableResponseDto<ProfileCompactDto> =
        apiCall {
            client
                .get("release/comment/votes/$commentId/$page") {
                    if (sort != null) parameter("sort", sort)
                }.body<PageableResponseDto<ProfileCompactDto>>()
                .requireOk()
        }

    /**
     * `GET release/comment/all/profile/{p_id}/{page}?sort=&token=` — комментарии конкретного
     * профиля (не обязательно текущего пользователя).
     */
    suspend fun profileComments(
        profileId: Long,
        page: Int,
        sort: Int = 0,
    ): PageableResponseDto<ReleaseCommentDto> =
        apiCall {
            client
                .get("release/comment/all/profile/$profileId/$page") {
                    parameter("sort", sort)
                }.body<PageableResponseDto<ReleaseCommentDto>>()
                .requireOk()
        }

    /**
     * `POST release/comment/process/{commentId}?token=`, body [CommentProcessRequestDto] —
     * модераторское действие (скрыть/удалить комментарий, забанить автора). НЕ для обычных
     * пользователей — реализовано по декомпилу для полноты API-слоя, без предположений о
     * коде ошибки при недостатке прав (не документирован статически, не тестировался вживую).
     */
    suspend fun process(
        commentId: Long,
        request: CommentProcessRequestDto,
    ): SimpleResponseDto =
        apiCall {
            client
                .post("release/comment/process/$commentId") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body<SimpleResponseDto>()
                .requireOk()
        }
}
