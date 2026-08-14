package com.aniko.app.feature.release.rating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.chart.RatingHistogram
import com.aniko.ui.component.chart.RatingInput
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Секция рейтинга релиза на Title Detail (P7.T9/P7.T10 плана, Трек D):
 * - [RatingHistogram] + [RatingInput] (Фаза 6, P6.T11) — гистограмма оценок 1..5★ и ввод/снятие
 *   собственной оценки (D5-b: повторный тап по уже выбранной звезде снимает оценку);
 * - [CommunityListBar] (D5-a) — сегментная полоса распределения по спискам сообщества.
 *
 * Самодостаточна: грузит свои данные по [releaseId] через собственную [ReleaseRatingViewModel],
 * а не принимает их параметром — сознательный трейдофф брифа Трека D (ещё один сетевой вызов
 * независимо от `ReleaseDetailsViewModel` Трека C, который грузит карточку релиза для остального
 * контента экрана). Не оптимизируется в рамках этой задачи.
 *
 * Публичная сигнатура зафиксирована архитектором — не менять без координации с Треком C, который
 * вызывает эту секцию из `ReleaseDetailsScreen`.
 */
@Composable
fun ReleaseRatingSection(
    releaseId: Int,
    modifier: Modifier = Modifier,
    viewModel: ReleaseRatingViewModel = koinViewModel(key = "release-rating-$releaseId"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    LaunchedEffect(releaseId) {
        viewModel.dispatch(ReleaseRatingIntent.Load(releaseId))
    }

    // P2.T10/P5.T7: эффект несёт только доменную AnixError, локализованный текст — забота экрана,
    // не ViewModel. `SnackbarHostState` этой секции не доступен (вне территории Трека D — не
    // трогаем App.kt/каркас), см. тот же TODO в `HomeScreen.kt` (P5.T7, идентичный случай).
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ReleaseRatingEffect.ShowError -> Unit
        }
    }

    val loadError = state.loadError

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceL)) {
        when {
            loadError != null ->
                AnixErrorState(
                    message = loadError.toMessage(strings),
                    modifier = Modifier.fillMaxWidth(),
                    onRetry = { viewModel.dispatch(ReleaseRatingIntent.Load(releaseId)) },
                )

            state.isLoading -> AnixLoadingState(modifier = Modifier.fillMaxWidth())

            else -> {
                RatingHistogram(counts = state.voteCounts)
                RatingInput(
                    myRating = state.yourVote,
                    onRate = { stars -> viewModel.dispatch(ReleaseRatingIntent.Rate(releaseId, stars)) },
                )
                CommunityListBar(counts = state.communityLists)
            }
        }
    }
}

private fun RatingLoadError.toMessage(strings: Strings): String =
    when (this) {
        RatingLoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        RatingLoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        RatingLoadError.GENERIC -> strings.homeSectionLoadError
    }
