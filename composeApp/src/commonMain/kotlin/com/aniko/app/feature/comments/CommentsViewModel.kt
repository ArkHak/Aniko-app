package com.aniko.app.feature.comments

import androidx.lifecycle.viewModelScope
import com.aniko.app.mvi.BaseViewModel
import com.aniko.data.paging.Paginator
import com.aniko.data.repository.CommentRepository
import com.aniko.model.AnixError
import com.aniko.model.ReleaseComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel экрана комментариев к релизу (P7.T12, MVI-контракт см. `CommentsContract.kt`) —
 * полноценная замена заглушки Фазы 5 (P5.T2): пагинация, переключение сортировки, спойлеры
 * (обрабатываются в UI по решению D10, см. `CommentRow`), голосование.
 *
 * `releaseId` не идёт в конструктор через Koin — см. KDoc [CommentsIntent.Load]. Пагинатор
 * пересоздаётся при каждом [CommentsIntent.Load] с новым `releaseId` и при каждом
 * [CommentsIntent.ChangeSort] (у `CommentRepository.commentsPaginator` нет метода "поменять сорт
 * у уже созданного пагинатора" — свежий список для нового порядка сортировки и есть ожидаемое
 * поведение, а не баг).
 */
class CommentsViewModel(
    private val commentRepository: CommentRepository,
) : BaseViewModel<CommentsState, CommentsIntent, CommentsEffect>(initialState = CommentsState()) {
    private var releaseId: Int? = null
    private var paginator: Paginator<ReleaseComment>? = null
    private var pagingCollectJob: Job? = null

    override suspend fun handleIntent(intent: CommentsIntent) {
        when (intent) {
            is CommentsIntent.Load -> load(intent.releaseId)
            CommentsIntent.LoadMore -> loadNextAndReportIfMoreFailed()
            CommentsIntent.Retry -> paginator?.refresh()
            is CommentsIntent.ChangeSort -> changeSort(intent.sort)
            is CommentsIntent.Vote -> vote(intent.commentId, intent.vote)
            is CommentsIntent.ChangeComposerText -> updateState { copy(composerText = intent.text) }
            CommentsIntent.SubmitComment -> submitComment()
        }
    }

    private suspend fun load(releaseId: Int) {
        if (this.releaseId == releaseId && paginator != null) return
        this.releaseId = releaseId
        startPaginator(releaseId, state.value.sort)
    }

    private suspend fun changeSort(sort: CommentsSort) {
        val currentReleaseId = releaseId ?: return
        if (state.value.sort == sort) return
        updateState { copy(sort = sort) }
        startPaginator(currentReleaseId, sort)
    }

    private suspend fun startPaginator(
        releaseId: Int,
        sort: CommentsSort,
    ) {
        pagingCollectJob?.cancel()
        val newPaginator = commentRepository.commentsPaginator(releaseId = releaseId, sort = sort.apiValue)
        paginator = newPaginator
        pagingCollectJob =
            newPaginator.state
                .onEach { paging -> updateState { copy(paging = paging) } }
                .launchIn(viewModelScope)
        newPaginator.loadNext()
    }

    /** См. KDoc `HomeViewModel.loadNextAndReportIfMoreFailed` — тот же смысл: подгрузка следующей
     *  страницы уже непустого списка отдельно сигналит об ошибке эффектом, т.к. `AnixContentSlot`
     *  в этом случае рисует контент, а не `AnixErrorState`, и `state.paging.error` иначе не увидят. */
    private suspend fun loadNextAndReportIfMoreFailed() {
        val currentPaginator = paginator ?: return
        currentPaginator.loadNext()
        val pagingState = currentPaginator.state.value
        val error = pagingState.error
        if (error != null && pagingState.items.isNotEmpty()) {
            emitEffect(CommentsEffect.ShowError(error))
        }
    }

    /**
     * Оптимистичный оверрайд подсветки голоса (см. KDoc `CommentsState.voteOverrides`), откатывается
     * при ошибке запроса. `TooGenericExceptionCaught`: `CommentRepository.vote` может бросить
     * произвольный `AnixError`-наследник (сетевая/HTTP/API-ошибка) — здесь важен сам факт неудачи
     * (откатить оверрайд + уведомить), не конкретный тип, `CancellationException` пробрасывается
     * отдельно, чтобы не глушить отмену корутины.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun vote(
        commentId: Long,
        vote: Int,
    ) {
        val previousOverride = state.value.voteOverrides[commentId]
        updateState { copy(voteOverrides = voteOverrides + (commentId to vote)) }
        try {
            commentRepository.vote(commentId, vote)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateState {
                copy(
                    voteOverrides =
                        if (previousOverride == null) {
                            voteOverrides - commentId
                        } else {
                            voteOverrides + (commentId to previousOverride)
                        },
                )
            }
            emitEffect(CommentsEffect.ShowError(e as? AnixError ?: AnixError.Unknown(e)))
        }
    }

    /**
     * Публикация нового комментария (P16.T17, `CommentRepository.addComment`). Клиентская
     * проверка длины [isCommentMessageValid] здесь — не только защита UI-кнопки (она и так
     * задизейблена на невалидном черновике, см. `CommentComposer`), а страховка от гонки:
     * `dispatch` асинхронный, кнопка могла быть нажата за миг до того, как текст стал невалидным
     * извне (сейчас такого пути в UI нет, но контракт интента этого не гарантирует — см. KDoc
     * `CommentsIntent.SubmitComment`). Сервер — конечный источник истины (коды `5`/`6` в
     * `CommentAddResponseDto`), этот чек — только чтобы не тратить впустую сетевой запрос на
     * заведомо отклоняемый сервером ввод.
     *
     * `TooGenericExceptionCaught`: тот же случай, что и `vote` выше — `CommentRepository`
     * пробрасывает произвольный `AnixError`-наследник, здесь важен сам факт неудачи.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun submitComment() {
        val currentReleaseId = releaseId ?: return
        val message = state.value.composerText.trim()
        if (!isCommentMessageValid(message)) return
        updateState { copy(isPostingComment = true) }
        try {
            commentRepository.addComment(currentReleaseId, message)
            updateState { copy(composerText = "", isPostingComment = false) }
            paginator?.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateState { copy(isPostingComment = false) }
            emitEffect(CommentsEffect.ShowError(e as? AnixError ?: AnixError.Unknown(e)))
        }
    }
}

/**
 * Клиентская нижняя граница длины комментария (P16.T17) — сервер сам возвращает
 * `CommentAddResponseDto.code == 5` (`COMMENT_IS_TOO_SHORT`) при слишком коротком сообщении, но
 * точный порог сервера статически не задокументирован (decompile не содержит числовой константы).
 * `2` — заведомо безопасный клиентский порог ниже любого разумного серверного минимума: отсекает
 * только очевидный мусор (пустая строка/один пробел/один символ) до сетевого запроса, не рискуя
 * ложно отклонить валидный короткий комментарий, который сервер бы принял.
 */
internal fun isCommentMessageValid(message: String): Boolean = message.trim().length >= MIN_COMMENT_MESSAGE_LENGTH

private const val MIN_COMMENT_MESSAGE_LENGTH = 2
