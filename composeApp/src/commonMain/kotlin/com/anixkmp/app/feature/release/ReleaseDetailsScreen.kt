package com.anixkmp.app.feature.release

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anixkmp.model.Release
import com.anixkmp.model.ReleaseStatus
import com.anixkmp.ui.component.AnixErrorBox
import com.anixkmp.ui.component.AnixLoadingBox
import com.anixkmp.ui.component.AnixPoster
import com.anixkmp.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Карточка релиза: постер, названия, описание, год/статус/жанры/оценка/счётчик серий.
 *
 * Кнопки «в список»/«избранное» здесь намеренно нет — это Фаза 6 (`docs/plan`), заглушки под
 * неё не создаются, чтобы не плодить недоделанный UI.
 */
@Composable
fun ReleaseDetailsScreen(
    releaseId: Int,
    modifier: Modifier = Modifier,
    viewModel: ReleaseDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(releaseId) { viewModel.load(releaseId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = AnixThemeTokens.dimens

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.release == null -> AnixLoadingBox(modifier = Modifier.fillMaxSize())

            state.errorMessage != null && state.release == null -> AnixErrorBox(
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier.fillMaxSize(),
            )

            state.release != null -> ReleaseDetailsContent(release = state.release!!)
        }
    }
}

@Composable
private fun ReleaseDetailsContent(release: Release) {
    val dimens = AnixThemeTokens.dimens

    Column(
        modifier = Modifier
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

                InfoRow(label = "Год", value = release.year?.toString())
                InfoRow(label = "Статус", value = release.status.toDisplayName())
                InfoRow(label = "Серии", value = release.episodesLabel())
                InfoRow(label = "Оценка", value = release.grade?.let { formatGrade(it) })
            }
        }

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
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
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

private fun ReleaseStatus.toDisplayName(): String? = when (this) {
    ReleaseStatus.ANNOUNCE -> "Анонс"
    ReleaseStatus.ONGOING -> "Онгоинг"
    ReleaseStatus.FINISHED -> "Завершён"
    ReleaseStatus.UNKNOWN -> null
}

private fun formatGrade(grade: Double): String {
    val rounded = (grade * 100).toInt() / 100.0
    return rounded.toString()
}
