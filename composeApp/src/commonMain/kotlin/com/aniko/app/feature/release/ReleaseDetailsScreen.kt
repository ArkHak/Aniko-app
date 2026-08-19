package com.aniko.app.feature.release

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.feature.release.rating.ReleaseRatingSection
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.Episode
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.VideoHost
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.share.ShareResult
import com.aniko.ui.share.rememberShareController
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
@Suppress("LongParameterList") // 6 параметров: releaseId/modifier/viewModel — обязательный
// каркас экрана, pendingEpisode*/onEpisodeClick — deep link (P10.T7, см. их собственный KDoc);
// группировать deep-link-параметры в конфиг-класс добавило бы косвенность ради одной пары полей.
@Composable
fun ReleaseDetailsScreen(
    releaseId: Int,
    modifier: Modifier = Modifier,
    /** Заполняется ТОЛЬКО навигацией из deep link (P10.T7), см. KDoc
     * `AnixDestination.ReleaseDetails`. `null`/`null` — обычный вход на карточку тайтла. */
    pendingEpisodeSourceId: Int? = null,
    pendingEpisodePosition: Int? = null,
    onEpisodeClick: (releaseId: Int, sourceId: Int, position: Int, host: VideoHost) -> Unit = { _, _, _, _ -> },
    viewModel: ReleaseDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(releaseId) { viewModel.load(releaseId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val titleNavigator = LocalTitleNavigator.current
    val snackbarHostState = remember { SnackbarHostState() }
    val onShareClick = rememberShareReleaseHandler(snackbarHostState) { state.release }

    HandlePendingEpisodeDeepLink(
        releaseId = releaseId,
        release = state.release,
        sourceId = pendingEpisodeSourceId,
        position = pendingEpisodePosition,
        resolve = viewModel::resolveDeepLinkEpisode,
        onResolved = { target -> onEpisodeClick(releaseId, target.sourceId, target.position, target.host) },
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        onShareClick = onShareClick,
                    )
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * Deep link на серию (P10.T7) — резолвит `hostKey` (`ReleaseDetailsViewModel.
 * resolveDeepLinkEpisode`) и доходит до плеера сам, когда данные загрузятся, см. KDoc
 * `AnixDestination.ReleaseDetails`. Вынесена из [ReleaseDetailsScreen] отдельным composable
 * (detekt `LongMethod`/`ComplexCondition` — три независимых guard clauses читаются линейно,
 * общее `&&`-условие того же смысла detekt по умолчанию считает слишком сложным).
 *
 * Срабатывает максимум один раз на конкретную пару [sourceId]/[position] — без флага-защёлки
 * [consumed] эффект дёргался бы повторно при каждой рекомпозиции [release] (например, после
 * toggleFavorite/changeListStatus).
 */
@Suppress("LongParameterList", "ReturnCount") // 6 параметров ровно по числу входов (id/релиз/пара
// deep-link-параметров/резолвер/колбэк), 3 guard clauses читаются линейно — то же обоснование,
// что у `ReleaseDetailsViewModel.resolveDeepLinkEpisodeChain`.
@Composable
private fun HandlePendingEpisodeDeepLink(
    releaseId: Int,
    release: Release?,
    sourceId: Int?,
    position: Int?,
    resolve: suspend (sourceId: Int, position: Int) -> PlayTarget?,
    onResolved: (PlayTarget) -> Unit,
) {
    var consumed by remember(releaseId, sourceId, position) { mutableStateOf(false) }
    if (consumed) return
    if (sourceId == null || position == null) return
    if (release == null) return

    LaunchedEffect(releaseId, sourceId, position) {
        consumed = true
        val target = resolve(sourceId, position)
        if (target != null) onResolved(target)
    }
}

/**
 * Кнопка "Поделиться" (P10.T9) — `shareController.shareText` + Desktop-фоллбэк-снекбар, вынесена
 * из [ReleaseDetailsScreen] отдельной factory-функцией (detekt `LongMethod`).
 */
@Composable
private fun rememberShareReleaseHandler(
    snackbarHostState: SnackbarHostState,
    release: () -> Release?,
): () -> Unit {
    val shareController = rememberShareController()
    val scope = rememberCoroutineScope()
    val shareLinkCopiedMessage = LocalStrings.current.shareLinkCopiedMessage
    return {
        val current = release()
        if (current != null) {
            val result =
                shareController.shareText(
                    text = "${current.title}\n${releaseDeepLink(current.id)}",
                    subject = current.title,
                )
            // Android/iOS открывают нативный шер-диалог сами — там уже есть системная обратная
            // связь, снекбар не нужен (см. KDoc ShareResult). Desktop-фоллбэк ничего не
            // показывает пользователю сам по себе — экран обязан явно подтвердить копирование.
            if (result == ShareResult.COPIED_TO_CLIPBOARD) {
                scope.launch { snackbarHostState.showSnackbar(shareLinkCopiedMessage) }
            }
        }
    }
}

/** `aniko://release/{id}` — тот же формат, что разбирает `parseDeepLink` (`DeepLink.kt`, P10.T7). */
private fun releaseDeepLink(releaseId: Int): String = "aniko://release/$releaseId"

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
    onShareClick: () -> Unit,
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
            onShareClick = onShareClick,
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
