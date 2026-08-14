package com.aniko.app.feature.release

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.feature.release.rating.ReleaseRatingSection
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.Episode
import com.aniko.model.ListStatus
import com.aniko.model.VideoHost
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Title Detail (P7.T7-T13, Трек C плана): постер, метаданные/жанры/скриншоты, кнопка "Смотреть",
 * статус в списке/избранное, синопсис ([ReleaseHeaderSection]), сетка серий watched/unwatched
 * ([ReleaseEpisodesSection]), похожие/рекомендуемые тайтлы ([ReleaseRelatedSection]), ссылка на
 * комментарии.
 *
 * Вертикальный скролл — один корневой `Column(verticalScroll)`, БЕЗ вложенных `LazyColumn`/
 * `LazyVerticalGrid` того же направления (см. D6 задания трека C и KDoc `EpisodeGrid` про тот же
 * класс проблемы: вложенный ленивый список с неограниченной высотой роняет measure). Именно
 * поэтому комментарии здесь — простая кликабельная строка-ссылка на отдельный экран
 * ([LocalTitleNavigator.openComments]), а не встроенный список.
 */
@Composable
fun ReleaseDetailsScreen(
    releaseId: Int,
    modifier: Modifier = Modifier,
    onEpisodeClick: (releaseId: Int, sourceId: Int, position: Int, host: VideoHost) -> Unit = { _, _, _, _ -> },
    viewModel: ReleaseDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(releaseId) { viewModel.load(releaseId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val titleNavigator = LocalTitleNavigator.current

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.release == null -> AnixLoadingState(modifier = Modifier.fillMaxSize())

            state.errorMessage != null && state.release == null ->
                AnixErrorState(
                    message = state.errorMessage.toReleaseMessage(strings),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

            state.release != null ->
                ReleaseDetailsContent(
                    state = state,
                    onWatchTargetResolved = { target ->
                        onEpisodeClick(releaseId, target.sourceId, target.position, target.host)
                    },
                    resolvePlayTarget = viewModel::resolvePlayTarget,
                    onSelectVoiceType = viewModel::selectVoiceType,
                    onSelectSource = viewModel::selectSource,
                    onEpisodeClick = { sourceId, position, host ->
                        onEpisodeClick(releaseId, sourceId, position, host)
                    },
                    onEpisodeLongClick = viewModel::toggleWatched,
                    onChangeListStatus = viewModel::changeListStatus,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRetryDetails = viewModel::retryDetails,
                    onOpenTitle = titleNavigator::openTitle,
                    onOpenComments = titleNavigator::openComments,
                )
        }
    }
}

@Suppress("LongParameterList") // Экран-оркестратор — каждый параметр это отдельный обязательный
// колбэк одной из независимых секций (шапка/серии/похожее/комментарии), см. KDoc файла.
@Composable
private fun ReleaseDetailsContent(
    state: ReleaseDetailsUiState,
    onWatchTargetResolved: (PlayTarget) -> Unit,
    resolvePlayTarget: suspend () -> PlayTarget?,
    onSelectVoiceType: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onEpisodeClick: (sourceId: Int, position: Int, host: VideoHost) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryDetails: () -> Unit,
    onOpenTitle: (Int) -> Unit,
    onOpenComments: (Int) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val release = state.release ?: return
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
    ) {
        ReleaseHeaderSection(
            release = release,
            details = state.details,
            detailsError = state.detailsError,
            isResolvingPlay = state.isResolvingPlay,
            onWatchClick = {
                scope.launch {
                    val target = resolvePlayTarget()
                    if (target != null) onWatchTargetResolved(target)
                }
            },
            onChangeListStatus = onChangeListStatus,
            onToggleFavorite = onToggleFavorite,
            onRetryDetails = onRetryDetails,
        )

        ReleaseRatingSection(releaseId = release.id)

        ReleaseEpisodesSection(
            state = state,
            onSelectVoiceType = onSelectVoiceType,
            onSelectSource = onSelectSource,
            onEpisodeClick = onEpisodeClick,
            onEpisodeLongClick = onEpisodeLongClick,
        )

        ReleaseRelatedSection(
            related = state.details?.relatedReleases.orEmpty(),
            recommended = state.details?.recommendedReleases.orEmpty(),
            onOpenTitle = onOpenTitle,
        )

        CommentsLinkRow(
            commentCount = state.details?.commentCount ?: 0,
            onClick = { onOpenComments(release.id) },
        )
    }
}

/**
 * Строка-ссылка на комментарии (P7.T12 вход, D6) — НЕ встроенный список, см. KDoc файла.
 * `Strings.releaseCommentsTitle(count)` уже несёт число, поэтому текст самодостаточен без
 * дополнительной подписи.
 */
@Composable
private fun CommentsLinkRow(
    commentCount: Int,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = strings.releaseCommentsTitle(commentCount), style = MaterialTheme.typography.titleMedium)
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}
