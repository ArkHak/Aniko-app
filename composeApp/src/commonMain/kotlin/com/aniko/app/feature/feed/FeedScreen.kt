package com.aniko.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.app.ui.toContentState
import com.aniko.model.AnixError
import com.aniko.model.Article
import com.aniko.ui.component.AnixAvatar
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран ленты (P16.T3, MVP) — read-only список постов каналов/блогов, `GET feed/latest/all/
 * {page}`. Тот же каркас `AnixContentSlot`/`LazyColumn`/prefetch, что [com.aniko.app.feature.
 * notifications.NotificationsScreen].
 *
 * Чего здесь намеренно нет (см. KDoc `ArticleDto` в `shared/data` — решение зафиксировано в
 * отчёте задачи, `docs/REELWAVE_PLAN.md`): открытия отдельной статьи (детального экрана нет),
 * комментариев к посту, голосования, репостов, полного rich-рендера контента (картинки/embed-
 * видео/цитаты/списки — только plain-текст paragraph/header-блоков). Карточка кликабельна только
 * до ленты — тапа по ней самой нет, счётчики (комментарии/репосты/голоса) — просто цифры, не
 * кнопки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.dispatch(FeedIntent.Load) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    // См. `NotificationsScreen`/`ReleaseCommentsScreen` — тот же нерешённый пока случай:
    // `SnackbarHostState` не прокинут в feature-пакеты, эффект пока молча игнорируется.
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is FeedEffect.ShowError -> Unit
        }
    }

    val contentState = state.paging.toContentState { error -> error.toDisplayMessage(strings) }

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.FEED_SCREEN_ROOT),
        topBar = {
            TopAppBar(
                title = { Text(strings.feedTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier =
                            Modifier.clearAndSetSemantics {
                                contentDescription = strings.backContentDescription
                            },
                    ) {
                        AnixIcon(name = "arrow_back", contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        AnixContentSlot(
            state = contentState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            emptyMessage = strings.feedEmpty,
            onRetry = { viewModel.dispatch(FeedIntent.Retry) },
        ) { articles ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = dimens.spaceS),
            ) {
                itemsIndexed(articles, key = { _, article -> article.id }) { index, article ->
                    if (index >= articles.size - FEED_PREFETCH_THRESHOLD) {
                        viewModel.dispatch(FeedIntent.LoadMore)
                    }
                    ArticleRow(article = article)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = dimens.spaceM))
                }
            }
        }
    }
}

/** Карточка одной строки ленты: аватар канала/автора + название + превью-текст + счётчики
 *  (комментарии/репосты/голоса). Не кликабельна (см. KDoc [FeedScreen] — детального экрана нет
 *  в MVP). */
@Composable
private fun ArticleRow(
    article: Article,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val displayName = article.channel?.title ?: article.author?.login.orEmpty()
    val avatarUrl = article.channel?.avatarUrl ?: article.author?.avatarUrl

    Column(
        modifier = modifier.fillMaxWidth().padding(dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnixAvatar(
                avatarUrl = avatarUrl,
                login = displayName,
                modifier = Modifier.size(dimens.space48),
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (article.previewText.isNotBlank()) {
            Text(
                text = article.previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = FEED_PREVIEW_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "visibility"/"share" — ближайшие нейтральные из 31-глифового набора (точных
            // "комментарий"/"репост" иконок нет, тот же приём, что `NotificationsScreen.iconName`
            // для COMMENT/ARTICLE, см. её KDoc).
            FeedCountIndicator(iconName = "visibility", count = article.commentCount)
            FeedCountIndicator(iconName = "share", count = article.repostCount)
            FeedCountIndicator(iconName = "favorite", count = article.voteCount.toLong())
        }
    }
}

/** Иконка + число — без кнопки/клика (голосование/репост не входят в MVP, см. KDoc
 *  [FeedScreen]). */
@Composable
private fun FeedCountIndicator(
    iconName: String,
    count: Long,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnixIcon(
            name = iconName,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.spaceM),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** См. KDoc `NotificationsScreen.toDisplayMessage` — тот же приём: `AnixError.message` —
 *  технический текст для логов, не для UI. */
private fun AnixError.toDisplayMessage(strings: Strings): String = strings.feedLoadError

private const val FEED_PREFETCH_THRESHOLD = 6
private const val FEED_PREVIEW_MAX_LINES = 3
