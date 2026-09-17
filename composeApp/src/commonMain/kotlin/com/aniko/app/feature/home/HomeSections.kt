package com.aniko.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aniko.data.paging.PagingState
import com.aniko.model.Release
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixContentState
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ProgressRow
import com.aniko.ui.component.TitleCard
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * «Продолжить смотреть» (P7.T1): на Compact/Medium — вертикальный список [ProgressRow]
 * (не горизонтальная рельса: `ProgressRow` спроектирован как строка списка — `fillMaxWidth()`
 * внутри, поза/подпись/прогресс в один ряд); на Expanded — сетка 4 колонок (мокап Claude
 * Design, строка 757) с кадром 130dp, полоской прогресса 4dp внизу и подписью 13sp/600.
 *
 * Прогресс — `Release.lastViewEpisode`/`episodesTotal`.
 *
 * [maxVisibleItems] намеренно режет список: Home — витрина, а не полноценный экран истории
 * просмотра (эта функция не входит в объём P7.T1) — показывать весь пагинированный список здесь
 * незачем, `onRetry` относится только к первой загрузке.
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
    windowSize: AnixWindowSize = LocalAnixWindowSize.current,
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

            windowSize == AnixWindowSize.Expanded ->
                ContinueWatchingGrid(
                    releases = state.items.take(maxVisibleItems),
                    onReleaseClick = onReleaseClick,
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

/**
 * Сетка «Продолжить смотреть» на Expanded: 4 колонки, gap 16dp, кадр 130dp с прогресс-баром
 * внизу (мокап Claude Design, строка 757).
 */
@Composable
private fun ContinueWatchingGrid(
    releases: List<Release>,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    HomeGrid(
        columns = CONTINUE_WATCHING_COLUMNS,
        gap = CONTINUE_WATCHING_GAP,
        items = releases,
        modifier = modifier.padding(horizontal = dimens.spaceM),
    ) { release ->
        ContinueWatchingGridItem(
            release = release,
            onClick = { onReleaseClick(release.id) },
        )
    }
}

/**
 * Ячейка сетки «Продолжить смотреть»: кадр 130dp, полоса прогресса 4dp внизу кадра
 * (трек `rgba(255,255,255,.2)`, заливка accent/primary), подпись 13sp/600 Manrope.
 */
@Composable
private fun ContinueWatchingGridItem(
    release: Release,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val watched = release.lastViewEpisode ?: 0
    val total = release.episodesTotal ?: 0
    val progress = if (total > 0) (watched.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val accessibleLabel =
        buildString {
            append(release.title)
            append(", ").append(strings.progressEpisodesOf(watched, total))
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CONTINUE_WATCHING_TEXT_GAP),
    ) {
        ContinueWatchingFrame(
            posterUrl = release.posterUrl,
            progress = progress,
            accessibleLabel = accessibleLabel,
            onClick = onClick,
        )
        Text(
            text = release.title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = CONTINUE_WATCHING_TITLE_FONT_SIZE,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = CONTINUE_WATCHING_TITLE_LINE_HEIGHT,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Кадр ячейки [ContinueWatchingGridItem] (постер + полоса прогресса) — вынесено отдельной
 *  функцией (detekt `LongMethod`). */
@Composable
private fun ContinueWatchingFrame(
    posterUrl: String?,
    progress: Float,
    accessibleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CONTINUE_WATCHING_FRAME_RADIUS)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CONTINUE_WATCHING_FRAME_HEIGHT)
                .clip(shape)
                .clickable(onClick = onClick)
                .clearAndSetSemantics { contentDescription = accessibleLabel },
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        ProgressTrack(progress = progress, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Полоса прогресса внизу кадра: трек `rgba(255,255,255,.2)`, заливка `primary`. */
@Composable
private fun ProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(PROGRESS_BAR_HEIGHT).background(PROGRESS_TRACK_COLOR),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(progress)
                    .height(PROGRESS_BAR_HEIGHT)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Универсальная сетка секций Home на Expanded: ровно [columns] колонок с заданным [gap] —
 * каждый ряд `Row` с ячейками `Modifier.weight(1f)`, чтобы все столбцы были равной ширины
 * независимо от контента внутри. `internal`, а не `private` — переиспользуется рельсами Home
 * (`HomeScreen.kt`, тот же файл-модуль, другой файл того же пакета).
 */
@Composable
internal fun <T> HomeGrid(
    columns: Int,
    gap: Dp,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val rows = items.chunked(columns)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        itemContent(item)
                    }
                }
                // Дополняем недостающие ячейки в последнем ряду, чтобы ширина оставалась
                // согласованной (Compose не требует этого визуально, но сохраняет расчёт
                // constraints для каждого столбца).
                repeat(columns - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Рельса-сетка «Top This Week» / «Новые серии» на Expanded (мокап Claude Design, строка 757):
 * 6 колонок, gap 14dp, постер 2:3 radius 14 через [TitleCard], подпись 12sp/600 Manrope
 * lineHeight 1.25. `internal` — переиспользуется из `HomeScreen.kt` (тот же пакет, другой файл).
 *
 * На Compact/Medium эта секция не рендерится — там остаётся горизонтальная рельса
 * [com.aniko.ui.component.HorizontalPosterRail] без изменений (см. `HomeScreen.kt`).
 */
@Suppress("LongParameterList") // Координирующий блок: заголовок + состояние + retry + 2 колбэка/флага.
@Composable
internal fun HomeRailGridSection(
    title: String,
    state: AnixContentState<Release>,
    onRetry: () -> Unit,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isNewEpisode: Boolean = false,
) {
    if (state.isEmpty) return
    val dimens = AnixThemeTokens.dimens

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        val errorMessage = state.errorMessage
        when {
            state.isLoading && state.items.isEmpty() ->
                AnixLoadingState(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(RAIL_GRID_PLACEHOLDER_HEIGHT)
                            .padding(horizontal = dimens.spaceM),
                )

            errorMessage != null && state.items.isEmpty() ->
                AnixErrorState(
                    message = errorMessage,
                    onRetry = onRetry,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(RAIL_GRID_PLACEHOLDER_HEIGHT)
                            .padding(horizontal = dimens.spaceM),
                )

            else ->
                HomeRailGrid(
                    releases = state.items,
                    isNewEpisode = isNewEpisode,
                    onReleaseClick = onReleaseClick,
                )
        }
    }
}

/**
 * Ячейки [HomeRailGridSection]: [RAIL_GRID_COLUMNS] колонок фиксированной ширины, вычисленной
 * через [BoxWithConstraints] (постоянная ширина нужна [TitleCard], у него нет режима
 * "заполнить родителя" — см. её `posterWidth`), gap [RAIL_GRID_GAP]. Не переиспользует
 * [HomeGrid] (весовые колонки внутри `Row`): [TitleCard] сам задаёт свою ширину модификатором
 * `.width(posterWidth)`, а не растягивается через `fillMaxWidth()`, поэтому явный `weight(1f)`
 * ему не нужен — контракт с [HomeGrid] был рассчитан под [ContinueWatchingGridItem], которая
 * растягивается.
 */
@Composable
private fun HomeRailGrid(
    releases: List<Release>,
    isNewEpisode: Boolean,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val captionStyle =
        MaterialTheme.typography.titleSmall.copy(
            fontSize = RAIL_GRID_CAPTION_FONT_SIZE,
            fontWeight = FontWeight.SemiBold,
            lineHeight = RAIL_GRID_CAPTION_LINE_HEIGHT,
        )

    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(horizontal = dimens.spaceM)) {
        val totalGap = RAIL_GRID_GAP * (RAIL_GRID_COLUMNS - 1)
        val cellWidth = (maxWidth - totalGap) / RAIL_GRID_COLUMNS

        Column(verticalArrangement = Arrangement.spacedBy(RAIL_GRID_GAP)) {
            releases.chunked(RAIL_GRID_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(RAIL_GRID_GAP)) {
                    row.forEach { release ->
                        TitleCard(
                            release = release,
                            onClick = { onReleaseClick(release.id) },
                            isNewEpisode = isNewEpisode,
                            posterWidth = cellWidth,
                            captionStyle = captionStyle,
                        )
                    }
                }
            }
        }
    }
}

private val RAIL_GRID_PLACEHOLDER_HEIGHT = 200.dp
private const val RAIL_GRID_COLUMNS = 6
private val RAIL_GRID_GAP = 14.dp
private val RAIL_GRID_CAPTION_FONT_SIZE = 12.sp
private val RAIL_GRID_CAPTION_LINE_HEIGHT = 15.sp

private val PLACEHOLDER_HEIGHT = 96.dp
private const val CONTINUE_WATCHING_COLUMNS = 4
private val CONTINUE_WATCHING_GAP = 16.dp
private val CONTINUE_WATCHING_FRAME_HEIGHT = 130.dp
private val CONTINUE_WATCHING_FRAME_RADIUS = 14.dp
private val CONTINUE_WATCHING_TITLE_FONT_SIZE = 13.sp
private val CONTINUE_WATCHING_TITLE_LINE_HEIGHT = 17.sp
private val CONTINUE_WATCHING_TEXT_GAP = 8.dp
private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_TRACK_COLOR = Color.White.copy(alpha = 0.2f)
