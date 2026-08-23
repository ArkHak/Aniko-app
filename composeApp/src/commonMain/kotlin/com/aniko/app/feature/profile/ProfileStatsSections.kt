// TooManyFunctions: файл — набор мелких presentation-секций статистики профиля, каждая
// отвечает ровно за один блок макета; выделены сюда из `ProfileScreen.kt` именно затем, чтобы
// тот не разрастался (тот же приём, что и `ReleaseHeaderSection.kt` в Фазе 7).
@file:Suppress("TooManyFunctions")

package com.aniko.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aniko.model.Achievement
import com.aniko.model.ProfileDetails
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.component.StatTileData
import com.aniko.ui.component.StatTileRow
import com.aniko.ui.component.TitleCard
import com.aniko.ui.component.chart.BarEntry
import com.aniko.ui.component.chart.ChartSlice
import com.aniko.ui.component.chart.DonutChart
import com.aniko.ui.component.chart.WeeklyBarChart
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Акцентированные метрики: часы просмотра и число просмотренных серий.
 *
 * Часы (`watched_time / 60`, вердикт P0.T4 — сервер отдаёт минуты) вынесены сюда, а не в общую
 * [StatsGrid]: в макете это главная цифра профиля, а восьмым числом в ряду одинаковых плиток она
 * визуально теряется.
 */
@Composable
internal fun ProfileHighlights(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            HighlightMetric(
                value = strings.profileWatchedHoursValue(profile.watchedHours),
                label = strings.profileWatchedHoursLabel,
                modifier = Modifier.weight(1f),
            )
            HighlightMetric(
                value = profile.watchedEpisodeCount.toString(),
                label = strings.profileWatchedEpisodesLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HighlightMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Любимые жанры — готовое `preferred_genres` с процентами от сервера. Минимальный UI (чипы
 * «жанр · N%») намеренно: своей визуализации распределения жанров макет не требует, а
 * придумывать её сверх того, что отдаёт API, — вне объёма v1.
 */
@Composable
internal fun FavoriteGenresSection(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    if (profile.preferredGenres.isEmpty()) return

    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(
            text = strings.profileFavoriteGenresTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        // Не `ChipRow`: тот даёт `FilterChip` с семантикой выбора, а здесь чипы чисто
        // информационные — выбирать среди жанров нечего.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            profile.preferredGenres.forEach { genre ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(strings.profileGenrePercent(genre.name, genre.percentage)) },
                )
            }
        }
    }
}

/**
 * «Достижения» — уже полученные пользователем значки (`profile/preference/badge/all/0`).
 *
 * В отличие от макета, где часть чипов нарисована тусклой как «не получено», API отдаёт только
 * коллекцию УЖЕ полученных бейджей (см. KDoc [Achievement]) — каталога всех возможных ачивок с
 * состоянием «заблокировано» сервер не даёт, поэтому здесь нет «тусклых» вариантов, только те,
 * что реально заработаны. Секция грузится отдельным запросом (см. `ProfileViewModel`) и просто не
 * показывается при пустом списке/ошибке — тем же паттерном, что [FavoriteGenresSection].
 */
@Composable
internal fun AchievementsSection(
    achievements: List<Achievement>,
    modifier: Modifier = Modifier,
) {
    if (achievements.isEmpty()) return

    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = strings.profileAchievementsTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
            contentPadding = PaddingValues(horizontal = dimens.spaceM),
        ) {
            items(items = achievements, key = { it.id }) { achievement ->
                AchievementBadge(achievement = achievement)
            }
        }
    }
}

@Composable
private fun AchievementBadge(achievement: Achievement) {
    val dimens = AnixThemeTokens.dimens

    Column(
        modifier = Modifier.width(ACHIEVEMENT_BADGE_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        AsyncImage(
            model = achievement.badgeUrl,
            contentDescription = achievement.name,
            modifier =
                Modifier
                    .size(ACHIEVEMENT_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = achievement.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ACHIEVEMENT_BADGE_SIZE = 56.dp
private val ACHIEVEMENT_BADGE_WIDTH = 72.dp

/**
 * Сетка статистики на переиспользуемом [StatTileRow] (`:shared:ui`, P6.T9 — переключение с
 * инлайнового кода было явно отложено до этой фазы). Часы просмотра сюда не входят — они
 * в [ProfileHighlights].
 */
@Composable
internal fun StatsGrid(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val tiles =
        listOf(
            StatTileData(profile.watchingCount.toString(), strings.listStatusWatching),
            StatTileData(profile.planCount.toString(), strings.listStatusPlanned),
            StatTileData(profile.completedCount.toString(), strings.listStatusCompleted),
            StatTileData(profile.holdOnCount.toString(), strings.listStatusOnHold),
            StatTileData(profile.droppedCount.toString(), strings.listStatusDropped),
            StatTileData(profile.favoriteCount.toString(), strings.libraryTabFavorites),
            StatTileData(profile.friendCount.toString(), strings.profileFriendsLabel),
            StatTileData(profile.commentCount.toString(), strings.profileCommentsLabel),
        )

    StatTileRow(tiles = tiles, modifier = modifier)
}

/**
 * Два графика статистики (P9.T8 + P9.T9). Адаптив P9.T13: на Expanded — две колонки в одном
 * `Row` (иначе на широком экране получается длинная полупустая вертикальная лента), на
 * Compact/Medium — друг под другом.
 */
@Composable
internal fun ProfileChartsSection(
    profile: ProfileDetails,
    windowSize: AnixWindowSize,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    if (windowSize == AnixWindowSize.Expanded) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceL),
        ) {
            ListsDonutSection(profile = profile, modifier = Modifier.weight(1f))
            WeeklyActivitySection(profile = profile, modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
        ) {
            ListsDonutSection(profile = profile)
            WeeklyActivitySection(profile = profile)
        }
    }
}

/**
 * P9.T8: распределение по 5 спискам + легенда. Полностью пустой профиль (сумма нулевая)
 * обрабатывает сам [DonutChart] — рисует `chartNoData` вместо кольца.
 */
@Composable
private fun ListsDonutSection(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val counts =
        listOf(
            strings.listStatusWatching to profile.watchingCount,
            strings.listStatusPlanned to profile.planCount,
            strings.listStatusCompleted to profile.completedCount,
            strings.listStatusOnHold to profile.holdOnCount,
            strings.listStatusDropped to profile.droppedCount,
        )
    // Подпись сегмента легенды — название списка + число тайтлов в нём.
    val slices = counts.map { (name, count) -> ChartSlice(label = "$name · $count", value = count.toFloat()) }
    val total = counts.sumOf { it.second }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = strings.profileListsChartTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        DonutChart(
            slices = slices,
            centerContent = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = total.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.profileListsChartTotalLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/**
 * P9.T9: недельный график активности из `watch_dynamics` — срез последних 7 точек по времени
 * (см. [ProfileDetails.recentWatchDynamics]). Подпись столбика — число месяца, как его отдаёт
 * сервер: локали это не касается, календарь пересчитывать не нужно.
 */
@Composable
private fun WeeklyActivitySection(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val points = profile.recentWatchDynamics()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = strings.profileActivityTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (points.isEmpty()) {
            Text(text = strings.chartNoData, style = MaterialTheme.typography.bodyMedium)
        } else {
            WeeklyBarChart(entries = points.map { BarEntry(label = it.day.toString(), value = it.count.toFloat()) })
        }
    }
}

/**
 * P9.T11: «Недавно смотрели». Источник — `history[]` из того же ответа `profile/{id}` (5 последних
 * релизов), а не отдельный `GET history/{page}`: данные уже пришли, лишний сетевой запрос под
 * ленту из пяти карточек не нужен. Полноценная пагинируемая история живёт на экране «Библиотека»
 * (`LibraryRepository.historyPaginator`).
 *
 * Горизонтальные отступы навешивает сама секция (а не общий `Column` экрана) — лента обязана
 * скроллиться от края до края, как рельсы главного экрана.
 */
@Composable
internal fun RecentlyWatchedSection(
    profile: ProfileDetails,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = strings.profileRecentlyWatchedTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )
        if (profile.recentlyWatched.isEmpty()) {
            Text(
                text = strings.profileRecentlyWatchedEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.spaceM),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                contentPadding = PaddingValues(horizontal = dimens.spaceM),
            ) {
                items(items = profile.recentlyWatched, key = { it.id }) { release ->
                    TitleCard(release = release, onClick = { onReleaseClick(release.id) })
                }
            }
        }
    }
}
