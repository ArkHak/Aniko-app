package com.aniko.app.feature.release

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseStatus
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.AnixPoster
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Карточка релиза: постер, названия, описание, год/статус/жанры/оценка/счётчик серий, блок
 * «в список»/«избранное» (см. [FavoriteAndStatusRow]), плюс простой флоу выбора серии (тип
 * озвучки → источник → серия) для перехода в плеер.
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

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.release == null -> AnixLoadingBox(modifier = Modifier.fillMaxSize())

            state.errorMessage != null && state.release == null ->
                AnixErrorBox(
                    message = state.errorMessage.toReleaseMessage(strings),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

            state.release != null ->
                ReleaseDetailsContent(
                    release = state.release!!,
                    state = state,
                    onSelectVoiceType = viewModel::selectVoiceType,
                    onSelectSource = viewModel::selectSource,
                    onEpisodeClick = { sourceId, position, host ->
                        onEpisodeClick(releaseId, sourceId, position, host)
                    },
                    onChangeListStatus = viewModel::changeListStatus,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
        }
    }
}

@Composable
private fun ReleaseDetailsContent(
    release: Release,
    state: ReleaseDetailsUiState,
    onSelectVoiceType: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onEpisodeClick: (sourceId: Int, position: Int, host: VideoHost) -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
            AnixPoster(url = release.posterUrl, contentDescription = release.title)

            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                val originalTitle = release.originalTitle
                if (!originalTitle.isNullOrBlank() && originalTitle != release.title) {
                    Text(
                        text = originalTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

                InfoRow(label = strings.releaseInfoYear, value = release.year?.toString())
                InfoRow(label = strings.releaseInfoStatus, value = release.status.toDisplayName(strings))
                InfoRow(label = strings.releaseInfoEpisodesLabel, value = release.episodesLabel())
                InfoRow(label = strings.releaseInfoRating, value = release.grade?.let { formatGrade(it) })
            }
        }

        FavoriteAndStatusRow(
            release = release,
            onChangeListStatus = onChangeListStatus,
            onToggleFavorite = onToggleFavorite,
        )

        if (release.genres.isNotEmpty()) {
            Text(
                text = release.genres.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        val description = release.description
        if (!description.isNullOrBlank()) {
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }

        EpisodeSelectionSection(
            state = state,
            onSelectVoiceType = onSelectVoiceType,
            onSelectSource = onSelectSource,
            onEpisodeClick = onEpisodeClick,
        )
    }
}

/**
 * Блок «в список»/«избранное» под шапкой релиза: тоггл избранного (сердце) + [ChipRow] с
 * пятью статусами [ListStatus]. Повторный тап по уже выбранному статусу снимает его
 * ([ListStatus.myListStatus] становится `null`) — `ChipRow` для этого не нужно менять,
 * достаточно решить в обработчике клика конкретного чипа, какой статус передать дальше.
 */
@Composable
private fun FavoriteAndStatusRow(
    release: Release,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (release.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription =
                    if (release.isFavorite) strings.commonRemoveFromFavorites else strings.commonAddToFavorites,
                tint = if (release.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        ChipRow(
            items = ListStatus.entries,
            isSelected = { it == release.myListStatus },
            label = { it.displayName(strings) },
            onClick = { status -> onChangeListStatus(if (status == release.myListStatus) null else status) },
        )
    }
}

/** Флоу выбора серии: тип озвучки → источник → серия. Плоские списки/чипы — намеренно без вычурного UI. */
@Composable
private fun EpisodeSelectionSection(
    state: ReleaseDetailsUiState,
    onSelectVoiceType: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onEpisodeClick: (sourceId: Int, position: Int, host: VideoHost) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        Text(
            text = strings.releaseInfoEpisodesLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        if (state.voiceTypes.isNotEmpty()) {
            SectionLabel(strings.releaseSectionVoiceType)
            ChipRow(
                items = state.voiceTypes,
                isSelected = { it.id == state.selectedTypeId },
                label = VoiceType::name,
                onClick = { onSelectVoiceType(it.id) },
            )
        }

        if (state.sources.isNotEmpty()) {
            SectionLabel(strings.releaseSectionSource)
            ChipRow(
                items = state.sources,
                isSelected = { it.id == state.selectedSourceId },
                label = EpisodeSource::name,
                onClick = { onSelectSource(it.id) },
            )
        }

        when {
            // Не `AnixLoadingBox` (он `fillMaxSize()`) — эта секция живёт внутри уже
            // прокручиваемой колонки с неограниченной высотой, `fillMaxSize()` там уронит layout.
            state.isEpisodesStepLoading -> CircularProgressIndicator(modifier = Modifier.padding(dimens.spaceM))

            state.episodesStepError != null ->
                Text(
                    text = state.episodesStepError.toEpisodesMessage(strings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

            state.episodes.isNotEmpty() -> {
                SectionLabel(strings.releaseSectionEpisodesList)
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                    val sourceId = state.selectedSourceId
                    // Хост берём из уже отображённого списка источников текущего выбора — без
                    // отдельного кэша/повторного запроса, гонка состояния тут невозможна.
                    val host = state.sources.firstOrNull { it.id == sourceId }?.host ?: VideoHost.UNKNOWN
                    state.episodes.forEach { episode ->
                        EpisodeRow(
                            episode = episode,
                            onClick = { if (sourceId != null) onEpisodeClick(sourceId, episode.position, host) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(dimens.cornerS))
                .padding(dimens.spaceM),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        Text(
            text = episode.name ?: strings.releaseEpisodeFallbackName(episode.position),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (episode.isWatched) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return
    val dimens = AnixThemeTokens.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun Release.episodesLabel(): String? {
    val total = episodesTotal
    val released = episodesReleased
    return when {
        total == null && released == null -> null
        total == null -> released.toString()
        else -> "$released/$total"
    }
}

private fun ReleaseStatus.toDisplayName(strings: Strings): String? =
    when (this) {
        ReleaseStatus.ANNOUNCE -> strings.releaseStatusAnnounce
        ReleaseStatus.ONGOING -> strings.releaseStatusOngoing
        ReleaseStatus.FINISHED -> strings.releaseStatusFinished
        ReleaseStatus.UNKNOWN -> null
    }

private fun LoadError?.toReleaseMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.releaseLoadError
    }

private fun LoadError?.toEpisodesMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.releaseEpisodesLoadError
    }

private fun formatGrade(grade: Double): String {
    val rounded = (grade * 100).toInt() / 100.0
    return rounded.toString()
}
