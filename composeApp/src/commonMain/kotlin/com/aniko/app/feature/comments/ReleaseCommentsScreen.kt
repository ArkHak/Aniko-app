package com.aniko.app.feature.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.app.ui.toContentState
import com.aniko.model.AnixError
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран комментариев к релизу (P7.T12) — полная версия взамен заглушки Фазы 5 (P5.T2): пагинация
 * через `CommentRepository.commentsPaginator`, переключатель сортировки новые/старые, пустое
 * состояние/ошибка через `AnixContentSlot` (Фаза 6), спойлеры без blur (D10, см. KDoc `CommentRow`).
 *
 * Маршрутизация не меняется — тот же публичный сигнатурный контракт (`releaseId`/`modifier`/
 * `viewModel` с дефолтом на `koinViewModel()`), которым уже пользуются `App.kt` (compact-навигация)
 * и `ListDetailHost` (detail-pane, P5.T3) — оба вне территории этого трека, трогать не нужно.
 */
@Composable
fun ReleaseCommentsScreen(
    releaseId: Int,
    modifier: Modifier = Modifier,
    viewModel: CommentsViewModel = koinViewModel(),
) {
    LaunchedEffect(releaseId) { viewModel.dispatch(CommentsIntent.Load(releaseId)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    // См. `HomeScreen` (P5.T8) — тот же нерешённый пока случай: `SnackbarHostState` сейчас приватен
    // `AnixSessionGate` (App.kt) и не прокинут в feature-пакеты; вне территории этого трека давать
    // ему выход наружу — эффект пока молча игнорируется, как и в `HomeScreen`.
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is CommentsEffect.ShowError -> Unit
        }
    }

    val contentState = state.paging.toContentState { error -> error.toDisplayMessage(strings) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = strings.commentsTitle, style = MaterialTheme.typography.titleLarge)
        }

        ChipRow(
            items = listOf(CommentsSort.NEWEST, CommentsSort.OLDEST),
            isSelected = { it == state.sort },
            label = { sort -> sort.label(strings) },
            onClick = { sort -> viewModel.dispatch(CommentsIntent.ChangeSort(sort)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceM),
        )

        AnixContentSlot(
            state = contentState,
            modifier = Modifier.fillMaxSize().padding(top = dimens.spaceM),
            emptyMessage = strings.commentsEmpty,
            onRetry = { viewModel.dispatch(CommentsIntent.Retry) },
        ) { comments ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.spaceM, vertical = dimens.spaceS),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
            ) {
                itemsIndexed(comments, key = { _, comment -> comment.id }) { index, comment ->
                    if (index >= comments.size - COMMENTS_PREFETCH_THRESHOLD) {
                        viewModel.dispatch(CommentsIntent.LoadMore)
                    }
                    CommentRow(
                        comment = comment,
                        effectiveVote = state.voteOverrides[comment.id] ?: comment.vote,
                        onVoteClick = { commentId, vote ->
                            viewModel.dispatch(CommentsIntent.Vote(commentId, vote))
                        },
                    )
                }
            }
        }
    }
}

private fun CommentsSort.label(strings: Strings): String =
    when (this) {
        CommentsSort.NEWEST -> strings.commentsSortNewest
        CommentsSort.OLDEST -> strings.commentsSortOldest
    }

/** `P2.T10`: `AnixError.message` — технический текст для логов, не для UI (см. KDoc `AnixError`).
 *  `homeSectionLoadError` ("Couldn't load" / "Не удалось загрузить") — тот же нейтральный generic-
 *  фолбэк, что уже используют `HomeScreen`/`LibraryScreen` для непредвиденных ошибок. */
private fun AnixError.toDisplayMessage(strings: Strings): String =
    when (this) {
        is AnixError.Network -> strings.commonErrorNoConnection
        is AnixError.Unauthorized -> strings.commonErrorUnauthorized
        else -> strings.homeSectionLoadError
    }

private const val COMMENTS_PREFETCH_THRESHOLD = 6
