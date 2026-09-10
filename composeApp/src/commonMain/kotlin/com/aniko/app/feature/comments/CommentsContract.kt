package com.aniko.app.feature.comments

import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.data.paging.PagingState
import com.aniko.model.AnixError
import com.aniko.model.ReleaseComment

/**
 * Сортировка списка комментариев — прокидывается в `CommentRepository.commentsPaginator(sort=)`
 * / `ReleaseCommentApi.comments(sort=)`. Точная серверная семантика значений `sort` НЕ
 * задокументирована статически (не подтверждена живой проверкой, см. `docs/api/ANIXART_API.md`,
 * раздел `ReleaseCommentApi`) — `NEWEST = 0` взят как дефолт существующего кода (Фаза 5
 * `CommentsViewModel`/`ReleaseCommentApi.comments(sort = 0)`), `OLDEST = 1` — по аналогии с
 * обычным порядком величин в остальных `Sort`-перечислениях Anixart (см. заметку про
 * `AniAnglia`/`Sort` enum в `docs/REELWAVE_PLAN.md`, P0.T3). Если окажется перевёрнуто —
 * тривиальный фикс одной константы, найденной живой проверкой.
 */
enum class CommentsSort(
    val apiValue: Int,
) {
    NEWEST(0),
    OLDEST(1),
}

/**
 * Состояние экрана комментариев релиза (P7.T12).
 *
 * [voteOverrides] — локальный оверрайд `ReleaseComment.vote` (голос текущего пользователя) после
 * отправки [CommentsIntent.Vote], ключ — `ReleaseComment.id`. Намеренно НЕ пересчитывает
 * `likesCount`/`voteCount` локально: точная формула агрегации лайк/дизлайк на сервере не
 * задокументирована и не подтверждена вживую, подделывать её на клиенте рискованнее, чем оставить
 * счётчик как есть до следующего `refresh()`/подгрузки страницы — обновляется только подсветка
 * кнопки «текущий голос» (см. `CommentRow`).
 *
 * [composerText]/[isPostingComment] — черновик нового комментария (P16.T17). Живёт в
 * `CommentsState`, а не в локальном Compose-состоянии экрана: [CommentsViewModel] обязан сам
 * очистить поле после успешной публикации (`SubmitComment`), а UI-композабл не знает, когда
 * запрос завершился успехом — тот же MVI-паттерн полей формы, что у `LoginUiState`/`LoginScreen`.
 */
data class CommentsState(
    val paging: PagingState<ReleaseComment> = PagingState(),
    val sort: CommentsSort = CommentsSort.NEWEST,
    val voteOverrides: Map<Long, Int> = emptyMap(),
    val composerText: String = "",
    val isPostingComment: Boolean = false,
) : UiState

/**
 * Команды экрана комментариев.
 *
 * [Load] — releaseId не идёт в конструктор ViewModel через Koin (тот же паттерн, что у
 * `ReleaseDetailsViewModel`/старого `CommentsViewModel`, см. их KDoc) — экран сам присылает его
 * из `LaunchedEffect(releaseId)`. Повторный [Load] с тем же `releaseId`, пока уже есть
 * загруженный/загружающийся пагинатор для него — no-op (см. `CommentsViewModel.handleIntent`).
 *
 * [Retry] переиспользован и для pull-to-refresh жеста над списком (P16.T17) — семантика та же
 * самая операция (`Paginator.refresh()`), отдельный `Refresh`-интент был бы бессмысленным
 * дублем: и кнопка "повторить" в `AnixErrorState`, и свайп-рефрешу нужен ровно один и тот же
 * сброс-и-перезагрузка первой страницы.
 */
sealed interface CommentsIntent : UiIntent {
    data class Load(
        val releaseId: Int,
    ) : CommentsIntent

    data object LoadMore : CommentsIntent

    data object Retry : CommentsIntent

    data class ChangeSort(
        val sort: CommentsSort,
    ) : CommentsIntent

    /** Голосование за комментарий (`CommentRepository.vote`) — опционально по заданию P7.T12,
     *  реализовано: `vote = 1` поставить лайк, `vote = 0` снять. */
    data class Vote(
        val commentId: Long,
        val vote: Int,
    ) : CommentsIntent

    /** Правка черновика нового комментария (P16.T17) — держит `CommentsState.composerText`
     *  в синхроне с полем ввода, тот же паттерн, что `SearchScreen`/`state.query`. */
    data class ChangeComposerText(
        val text: String,
    ) : CommentsIntent

    /** Публикация черновика (P16.T17, `ReleaseCommentApi.add`). UI обязан не слать этот интент,
     *  пока `isCommentMessageValid(state.composerText)` не `true` (кнопка отправки задизейблена) —
     *  сам ViewModel всё равно перепроверяет длину перед сетевым вызовом (см. `submitComment`). */
    data object SubmitComment : CommentsIntent
}

/** См. KDoc `HomeEffect.ShowError` — тот же случай: неудачная подгрузка следующей страницы
 *  комментариев, когда список уже непустой (ошибка первой загрузки видна через
 *  `state.paging.error`/`AnixContentSlot` и отдельного эффекта не требует). */
sealed interface CommentsEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : CommentsEffect
}
