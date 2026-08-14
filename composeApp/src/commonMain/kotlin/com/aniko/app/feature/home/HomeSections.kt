package com.aniko.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aniko.data.paging.PagingState
import com.aniko.model.Release
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ProgressRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * «Продолжить смотреть» (P7.T1) — вертикальный список [ProgressRow] (не горизонтальная рельса:
 * `ProgressRow` спроектирован как строка списка — `fillMaxWidth()` внутри, поза/подпись/прогресс
 * в один ряд, — а не как карточка карусели, см. её KDoc про трёх реальных потребителей: Continue
 * Watching/Мои списки/Расписание). Прогресс — `Release.lastViewEpisode`/`episodesTotal`.
 *
 * [TODO: verify live] `lastViewEpisode` может приходить `null` под авторизацией — это валидная
 * деградация: `ProgressRow` в таком случае просто рисует пустую полосу прогресса (0%), не падает.
 *
 * [maxVisibleItems] намеренно режет список: Home — витрина, а не полноценный экран истории
 * просмотра (эта функция не входит в объём P7.T1) — показывать весь пагинированный список здесь
 * незачем, `onRetry` относится только к первой загрузке (ошибка подгрузки следующей страницы
 * уже загруженного списка сюда не долетает — она отдельно уходит эффектом `HomeEffect.ShowError`,
 * см. `HomeViewModel`).
 */
@Suppress("LongParameterList") // Координирующий блок: заголовок + состояние + 2 колбэка + 2 настройки layout'а.
@Composable
fun ContinueWatchingSection(
    title: String,
    state: PagingState<Release>,
    onReleaseClick: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    maxVisibleItems: Int = 5,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    // Пустая секция без ошибки (например «Продолжить смотреть» у нового аккаунта) не рисует
    // пустой блок вовсе — тот же принцип, что и у остальных секций Home.
    val isSettledEmpty = state.items.isEmpty() && !state.isLoading && !state.isRefreshing && state.error == null
    if (isSettledEmpty) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        val error = state.error
        when {
            state.items.isEmpty() && (state.isLoading || state.isRefreshing) ->
                AnixLoadingState(modifier = Modifier.fillMaxWidth().height(PLACEHOLDER_HEIGHT))

            state.items.isEmpty() && error != null ->
                AnixErrorState(
                    message = error.toHomeMessage(strings),
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth().height(PLACEHOLDER_HEIGHT),
                )

            else ->
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                    state.items.take(maxVisibleItems).forEach { release ->
                        ProgressRow(
                            release = release,
                            watchedEpisodes = release.lastViewEpisode,
                            onClick = { onReleaseClick(release.id) },
                        )
                    }
                }
        }
    }
}

private val PLACEHOLDER_HEIGHT = 96.dp
