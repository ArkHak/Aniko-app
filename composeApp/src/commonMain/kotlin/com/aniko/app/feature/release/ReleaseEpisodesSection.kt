package com.aniko.app.feature.release

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.ui.component.ChipRow
import com.aniko.ui.component.EpisodeGrid
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Сетка серий Title Detail (P7.T8, Трек C): флоу выбора типа озвучки → источника →
 * [EpisodeGrid] вместо прежнего плоского списка строк. Обычный тап по ячейке — играть серию
 * ([onEpisodeClick], существующая навигация на плеер), долгий тап — переключить watched/unwatched
 * ([onEpisodeLongClick], см. D4 и `ReleaseDetailsViewModel.toggleWatched`).
 *
 * `state.displayEpisodes` (не `state.episodes`) — уже смерженный с локальным оверрайдом список
 * (см. [displayEpisodes] в `ReleaseDetailsContract.kt`), сюда попадает специально не сырой список
 * с сервера.
 */
@Suppress("LongParameterList") // Координирующий блок: состояние + 2 колбэка выбора + 2 колбэка ячейки.
@Composable
fun ReleaseEpisodesSection(
    state: ReleaseDetailsUiState,
    onSelectVoiceType: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onEpisodeClick: (sourceId: Int, position: Int, host: VideoHost) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
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
            // Не `AnixLoadingState.fillMaxSize()` — эта секция живёт внутри уже прокручиваемой
            // колонки с неограниченной высотой, `fillMaxSize()` там уронит layout.
            state.isEpisodesStepLoading -> CircularProgressIndicator(modifier = Modifier.padding(dimens.spaceM))

            state.episodesStepError != null ->
                Text(
                    text = state.episodesStepError.toEpisodesMessage(strings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

            state.episodes.isNotEmpty() -> {
                SectionLabel(strings.releaseSectionEpisodesList)
                val sourceId = state.selectedSourceId
                // Хост берём из уже отображённого списка источников текущего выбора — без
                // отдельного кэша/повторного запроса, гонка состояния тут невозможна.
                val host = state.sources.firstOrNull { it.id == sourceId }?.host ?: VideoHost.UNKNOWN
                EpisodeGrid(
                    episodes = state.displayEpisodes,
                    onEpisodeClick = { episode ->
                        if (sourceId != null) onEpisodeClick(sourceId, episode.position, host)
                    },
                    currentPosition = state.release?.lastViewEpisode,
                    onEpisodeLongClick = onEpisodeLongClick,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
    )
}

private fun LoadError?.toEpisodesMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.releaseEpisodesLoadError
    }

private const val SECTION_LABEL_ALPHA = 0.6f
