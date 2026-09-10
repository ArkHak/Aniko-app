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
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.app.ui.toContentState
import com.aniko.model.AnixError
import com.aniko.model.ReleaseComment
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
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
    val titleNavigator = LocalTitleNavigator.current

    // См. `HomeScreen` (P5.T8) — тот же нерешённый пока случай: `SnackbarHostState` сейчас приватен
    // `AnixSessionGate` (App.kt) и не прокинут в feature-пакеты; вне территории этого трека давать
    // ему выход наружу — эффект пока молча игнорируется, как и в `HomeScreen`.
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is CommentsEffect.ShowError -> Unit
        }
    }

    val contentState = state.paging.toContentState { error -> error.toDisplayMessage(strings) }

    Column(modifier = modifier.fillMaxSize().testTag(AnixTestTags.RELEASE_COMMENTS_SCREEN_ROOT)) {
        CommentsTopBar(title = strings.commentsTitle, onBack = { titleNavigator.back() })

        ChipRow(
            items = listOf(CommentsSort.NEWEST, CommentsSort.OLDEST),
            isSelected = { it == state.sort },
            label = { sort -> sort.label(strings) },
            onClick = { sort -> viewModel.dispatch(CommentsIntent.ChangeSort(sort)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceM),
        )

        // `weight(1f)`, а не `fillMaxSize()` (было до P16.T17): под списком теперь всегда есть
        // [CommentComposer] — без веса он бы прижался к полю ввода и стянул композер под контент,
        // который сам не знает своего размера (`AnixContentSlot`), либо вовсе вытолкнул его за
        // экран на длинных списках.
        AnixContentSlot(
            state = contentState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = dimens.spaceM),
            emptyMessage = strings.commentsEmpty,
            onRetry = { viewModel.dispatch(CommentsIntent.Retry) },
        ) { comments ->
            CommentsList(
                comments = comments,
                voteOverrides = state.voteOverrides,
                isRefreshing = state.paging.isRefreshing,
                onIntent = { intent -> viewModel.dispatch(intent) },
            )
        }

        CommentComposer(
            text = state.composerText,
            isPosting = state.isPostingComment,
            onTextChange = { text -> viewModel.dispatch(CommentsIntent.ChangeComposerText(text)) },
            onSubmit = { viewModel.dispatch(CommentsIntent.SubmitComment) },
        )
    }
}

/** Список комментариев + свайп-рефреш (P16.T17, `PullToRefreshBox` — material3 1.9.0, доступен в
 *  установленной версии CMP без бампа каталога версий). Свайп переиспользует ту же операцию, что
 *  и кнопка "повторить" в `AnixErrorState` — см. KDoc `CommentsIntent.Retry`. Вынесено из
 *  [ReleaseCommentsScreen] отдельной функцией (detekt `LongMethod`), а не оставлено внутри
 *  лямбды [AnixContentSlot].
 *
 * [onIntent] — единый диспетчер вместо отдельных `onRefresh`/`onLoadMore`/`onVoteClick` (было до
 * исправления): три раздельных лямбды в дополнение к `comments`/`voteOverrides`/`isRefreshing`
 * превышали порог detekt `LongParameterList` (6). Тот же приём, что уже применяет `CollectEffects`
 * с эффектами ViewModel — один канал вместо N разросшихся колбэков.
 */
@Composable
private fun CommentsList(
    comments: List<ReleaseComment>,
    voteOverrides: Map<Long, Int>,
    isRefreshing: Boolean,
    onIntent: (CommentsIntent) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onIntent(CommentsIntent.Retry) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = dimens.spaceM, vertical = dimens.spaceS),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            itemsIndexed(comments, key = { _, comment -> comment.id }) { index, comment ->
                if (index >= comments.size - COMMENTS_PREFETCH_THRESHOLD) {
                    onIntent(CommentsIntent.LoadMore)
                }
                CommentRow(
                    comment = comment,
                    effectiveVote = voteOverrides[comment.id] ?: comment.vote,
                    onVoteClick = { commentId, vote -> onIntent(CommentsIntent.Vote(commentId, vote)) },
                )
            }
        }
    }
}

/** Композер нового комментария (P16.T17, `ReleaseCommentApi.add`) — текстовое поле + кнопка
 *  отправки внизу экрана. Кнопка задизейблена, пока [isCommentMessageValid] не проходит
 *  клиентскую проверку длины черновика (см. её KDoc в `CommentsViewModel.kt`) — тот же приём
 *  "задизейбленный сабмит на невалидном вводе", что уже использует `LoginScreen`, а не отдельный
 *  видимый текст ошибки под полем: сервер и так продублирует отказ через `CommentsEffect.ShowError`
 *  при реальном отклонении (короткий/длинный текст/лимит), клиентская проверка только экономит
 *  заведомо бесполезный сетевой запрос.
 */
@Composable
private fun CommentComposer(
    text: String,
    isPosting: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(strings.commentsComposerPlaceholder) },
            enabled = !isPosting,
            maxLines = COMMENT_COMPOSER_MAX_LINES,
        )
        Button(
            onClick = onSubmit,
            enabled = isCommentMessageValid(text) && !isPosting,
        ) {
            Text(strings.commentsComposerSubmit)
        }
    }
}

/** Заголовок + кнопка "назад" — вынесена отдельно (detekt `LongMethod`). Раньше у экрана не было
 *  способа вернуться назад кроме системной кнопки Android — на iOS был тупик (нет edge-swipe в
 *  androidx.navigation.compose "из коробки"); `titleNavigator.back()` разбирает pane-стек/
 *  NavController сам, см. её KDoc. */
@Composable
private fun CommentsTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.clearAndSetSemantics { contentDescription = strings.backContentDescription },
        ) {
            AnixIcon(name = "arrow_back", contentDescription = null)
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
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

/** Композер — короткое поле, не заметка на полстраницы (P16.T17). */
private const val COMMENT_COMPOSER_MAX_LINES = 4
