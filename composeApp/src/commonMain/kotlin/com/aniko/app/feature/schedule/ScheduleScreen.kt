package com.aniko.app.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.Release
import com.aniko.model.Schedule
import com.aniko.model.WeekDay
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixEmptyState
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ChipRow
import com.aniko.ui.component.ReleaseCard
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран расписания выхода эпизодов по дням недели (P5.T2-задел, доработан P9.T4-T6).
 *
 * ## Структура: день-селектор наверху, а не вертикальный список из 7 секций подряд
 * Раньше (до P9.T4) экран рисовал все 7 дней друг под другом, молча пропуская дни без релизов.
 * Заменено на горизонтальный [ChipRow] выбора дня + контент только выбранного дня — типичный
 * мобильный паттерн "расписания" (как системные календари/список эпизодов на неделю): не нужно
 * скроллить мимо 6 нерелевантных дней, чтобы увидеть нужный, а "сегодня" и выбор пользователя
 * видны сразу в одну строку, без прокрутки. Вертикальный список всех секций был бы валиден тоже
 * (и проще), но здесь релизов на день может быть много — 7 полных горизонтальных лент подряд на
 * Compact-экране означают куда больше скролла до нужного дня, чем один тап по чипу.
 *
 * ## Пустое состояние (P9.T5)
 * День без релизов теперь не пропускается, а показывает [AnixEmptyState] (тот же компонент,
 * что и `LibraryScreen` для пустой вкладки) — пользователь видит, что расписание точно
 * загружено, а не думает, что экран завис или день ещё не пришёл с сервера.
 *
 * ## Навигация по дням + подсветка "сегодня" (P9.T5)
 * Чипы [ChipRow] — единственный способ переключения (свайп между днями не добавлен: тап по чипу
 * уже даёт мгновенный переход к любому из 7 дней за одно действие, свайп добавил бы только жест
 * "вперёд/назад на один день" ценой дополнительного `Pager`-стейта ради того же результата).
 * День "сегодня" помечен точкой после названия — сравнение с [WeekDay] сегодняшнего дня уже
 * есть в [ScheduleViewModel] (используется и `HomeViewModel` для секции "Новые серии").
 *
 * ## Wide-экраны (P9.T6)
 * На [AnixWindowSize.Expanded] под селектором показываются колонки сразу нескольких дней (все 7,
 * горизонтальный скролл) — на широком экране есть место видеть соседние дни одновременно, а не
 * только один выбранный. Тап по чипу на Expanded не фильтрует контент (он и так весь на экране),
 * а прокручивает колонки до нужного дня — тот же элемент управления работает предсказуемо в обоих
 * режимах ширины. На Compact/Medium колонок не хватило бы места — там чип обычным образом
 * фильтрует единственную видимую секцию.
 */
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val windowSize = LocalAnixWindowSize.current

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.schedule == null -> AnixLoadingState(modifier = Modifier.fillMaxSize())

            state.errorMessage != null && state.schedule == null ->
                AnixErrorState(
                    message = state.errorMessage.toScheduleMessage(strings),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

            state.schedule != null ->
                ScheduleContent(
                    schedule = state.schedule!!,
                    title = strings.scheduleTitle,
                    selectedDay = state.selectedDay,
                    today = state.today,
                    windowSize = windowSize,
                    onDaySelected = viewModel::selectDay,
                )
        }
    }
}

@Suppress("LongParameterList") // Координирующий блок: контент дня + селектор + подсветка "сегодня" +
// ширина экрана — тот же паттерн, что и `HomeContent`/`HomeHeroSection` в `HomeScreen.kt`.
@Composable
private fun ScheduleContent(
    schedule: Schedule,
    title: String,
    selectedDay: WeekDay,
    today: WeekDay,
    windowSize: AnixWindowSize,
    onDaySelected: (WeekDay) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val titleNavigator = LocalTitleNavigator.current
    val columnsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        DaySelector(
            selectedDay = selectedDay,
            today = today,
            strings = strings,
            onDaySelected = { day ->
                onDaySelected(day)
                if (windowSize == AnixWindowSize.Expanded) {
                    val index = WeekDay.entries.indexOf(day)
                    coroutineScope.launch { columnsListState.animateScrollToItem(index) }
                }
            },
        )

        if (windowSize == AnixWindowSize.Expanded) {
            ScheduleColumns(
                schedule = schedule,
                strings = strings,
                listState = columnsListState,
                onReleaseClick = { releaseId -> titleNavigator.openTitle(releaseId) },
            )
        } else {
            SelectedDayContent(
                releases = schedule.releasesOn(selectedDay),
                strings = strings,
                onReleaseClick = { releaseId -> titleNavigator.openTitle(releaseId) },
            )
        }
    }
}

/** Горизонтальный ряд чипов выбора дня — тонкая обёртка над [ChipRow] (P9.T4/T5). */
@Composable
private fun DaySelector(
    selectedDay: WeekDay,
    today: WeekDay,
    strings: Strings,
    onDaySelected: (WeekDay) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    ChipRow(
        items = WeekDay.entries,
        isSelected = { it == selectedDay },
        label = { day -> day.chipLabel(today, strings) },
        onClick = onDaySelected,
        modifier = Modifier.padding(horizontal = dimens.spaceM),
    )
}

/** "•" после названия — маркер "сегодня" в чипе (не отдельная иконка, чтобы не тянуть Material
 *  Icons Extended ради одного значка точки, см. KDoc [ChipRow]). */
private fun WeekDay.chipLabel(
    today: WeekDay,
    strings: Strings,
): String {
    val name = displayName(strings)
    return if (this == today) "$name •" else name
}

/** Контент одного выбранного дня — Compact/Medium (P9.T5): вертикальная сетка постеров или
 *  [AnixEmptyState], если на день ничего не запланировано. */
@Composable
private fun SelectedDayContent(
    releases: List<Release>,
    strings: Strings,
    onReleaseClick: (Int) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens

    if (releases.isEmpty()) {
        AnixEmptyState(message = strings.scheduleEmptyDayMessage, modifier = Modifier.fillMaxSize())
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = dimens.spaceM, vertical = dimens.spaceS),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(releases, key = { it.id }) { release ->
                ReleaseCard(release = release, onClick = { onReleaseClick(release.id) })
            }
        }
    }
}

/** Все 7 дней колонками сразу — Expanded (P9.T6): горизонтально прокручиваемый [LazyRow] колонок
 *  фиксированной ширины [dayColumnWidth], каждая — заголовок дня + своя вертикальная лента. */
@Composable
private fun ScheduleColumns(
    schedule: Schedule,
    strings: Strings,
    listState: LazyListState,
    onReleaseClick: (Int) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = dimens.spaceM),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
            modifier = Modifier.fillMaxSize().widthIn(max = dimens.contentMaxWidth),
        ) {
            items(WeekDay.entries, key = { it.name }) { day ->
                DayColumn(
                    day = day,
                    releases = schedule.releasesOn(day),
                    strings = strings,
                    onReleaseClick = onReleaseClick,
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: WeekDay,
    releases: List<Release>,
    strings: Strings,
    onReleaseClick: (Int) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens

    Column(
        modifier = Modifier.width(dayColumnWidth).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        Text(text = day.displayName(strings), style = MaterialTheme.typography.titleMedium)

        if (releases.isEmpty()) {
            AnixEmptyState(message = strings.scheduleEmptyDayMessage, modifier = Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(releases, key = { it.id }) { release ->
                    ReleaseCard(release = release, onClick = { onReleaseClick(release.id) })
                }
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

/** Ширина колонки дня на Expanded (P9.T6) — как `sidebarWidth` в `SidebarSlot.kt`, локальная
 *  константа: единственный потребитель этой ширины, заводить общий токен в [AnixThemeTokens]
 *  ради одного экрана избыточно. */
private val dayColumnWidth = 280.dp
