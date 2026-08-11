package com.aniko.app.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.Release
import com.aniko.model.Schedule
import com.aniko.model.WeekDay
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.ReleaseCard
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран расписания выхода эпизодов по дням недели (P5.T2/P9.T4-задел). Секция на каждый день из
 * [WeekDay.entries] — горизонтальная лента [ReleaseCard], день без релизов не рисует пустую секцию
 * (тот же приём, что и [com.aniko.app.feature.home.HomeScreen] для пустых секций).
 *
 * Клик по карточке идёт напрямую через [LocalTitleNavigator] (P5.T3), а не через callback-параметр
 * `onReleaseClick`, как у более старых экранов (`HomeScreen`/`LibraryScreen`/`SearchScreen`) — это
 * новый паттерн Фазы 5: экран сам решает, что "открыть тайтл", а куда именно (панель или
 * полноэкранный маршрут) — уже забота реализации `TitleNavigator`, интегратору не нужно прокидывать
 * коллбэк через `NavHost`.
 *
 * Лейблы дней недели — временно [WeekDay.name] (английские константы enum, не кириллица, так что
 * detekt `ForbiddenCyrillicStringLiteral` их не поймает): в `Strings` (shared/ui, трогать в этой
 * задаче нельзя) нет ключей под 7 дней недели. Локализация запланирована на Фазу 9 (P9.T4/P9.T5),
 * когда экран дорабатывается полноценно — см. `docs/REELWAVE_PLAN.md`.
 */
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.schedule == null -> AnixLoadingBox(modifier = Modifier.fillMaxSize())

            state.errorMessage != null && state.schedule == null ->
                AnixErrorBox(
                    message = state.errorMessage.toScheduleMessage(strings),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

            state.schedule != null ->
                ScheduleContent(schedule = state.schedule!!, title = strings.scheduleTitle)
        }
    }
}

@Composable
private fun ScheduleContent(
    schedule: Schedule,
    title: String,
) {
    val dimens = AnixThemeTokens.dimens
    val titleNavigator = LocalTitleNavigator.current

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        WeekDay.entries.forEach { day ->
            val releases = schedule.releasesOn(day)
            if (releases.isNotEmpty()) {
                DaySection(
                    day = day,
                    releases = releases,
                    onReleaseClick = { releaseId -> titleNavigator.openTitle(releaseId) },
                )
            }
        }
    }
}

@Composable
private fun DaySection(
    day: WeekDay,
    releases: List<Release>,
    onReleaseClick: (Int) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = day.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            contentPadding = PaddingValues(horizontal = dimens.spaceM),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(releases, key = { it.id }) { release ->
                ReleaseCard(release = release, onClick = { onReleaseClick(release.id) })
            }
        }
    }
}

private fun LoadError?.toScheduleMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.homeSectionLoadError
    }
