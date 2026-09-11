package com.aniko.app.feature.collections

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.app.ui.toContentState
import com.aniko.model.AnixCollection
import com.aniko.model.AnixError
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixPoster
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран публичных коллекций (P16.T16, MVP) — read-only список, `GET collection/all/{page}`,
 * публичный маршрут (не требует токена, см. KDoc `CollectionDto`). Тот же каркас, что
 * [com.aniko.app.feature.feed.FeedScreen].
 *
 * Чего здесь намеренно нет (объём — probe-задача, решение по CUT/MVP зафиксировано в отчёте
 * задачи, `docs/REELWAVE_PLAN.md`): открытия коллекции (список релизов внутри — `GET
 * collection/{id}/releases/{page}`), создания/редактирования своих коллекций, лайка, приватных
 * коллекций собственного аккаунта. Карточка не кликабельна.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.dispatch(CollectionsIntent.Load) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is CollectionsEffect.ShowError -> Unit
        }
    }

    val contentState = state.paging.toContentState { error -> error.toDisplayMessage(strings) }

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.COLLECTIONS_SCREEN_ROOT),
        topBar = {
            TopAppBar(
                title = { Text(strings.collectionsTitle) },
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
            emptyMessage = strings.collectionsEmpty,
            onRetry = { viewModel.dispatch(CollectionsIntent.Retry) },
        ) { collections ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = dimens.spaceS),
            ) {
                itemsIndexed(collections, key = { _, collection -> collection.id }) { index, collection ->
                    if (index >= collections.size - COLLECTIONS_PREFETCH_THRESHOLD) {
                        viewModel.dispatch(CollectionsIntent.LoadMore)
                    }
                    CollectionRow(collection = collection)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = dimens.spaceM))
                }
            }
        }
    }
}

/** Карточка одной коллекции: обложка + название/автор/описание + счётчики (избранное/
 *  комментарии). Не кликабельна (см. KDoc [CollectionsScreen]). */
@Composable
private fun CollectionRow(
    collection: AnixCollection,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth().padding(dimens.spaceM),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
    ) {
        AnixPoster(
            url = collection.imageUrl,
            contentDescription = null,
            width = COLLECTION_COVER_WIDTH,
            aspectRatio = 1f,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            collection.creator?.login?.let { login ->
                Text(
                    text = strings.collectionByCreator(login),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (collection.description.isNotBlank()) {
                Text(
                    text = collection.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = COLLECTION_DESCRIPTION_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CollectionCountIndicator(iconName = "favorite", count = collection.favoritesCount.toLong())
                CollectionCountIndicator(iconName = "visibility", count = collection.commentCount)
            }
        }
    }
}

@Composable
private fun CollectionCountIndicator(
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

private fun AnixError.toDisplayMessage(strings: Strings): String = strings.collectionsLoadError

private const val COLLECTIONS_PREFETCH_THRESHOLD = 6
private const val COLLECTION_DESCRIPTION_MAX_LINES = 2
private val COLLECTION_COVER_WIDTH = 88.dp
