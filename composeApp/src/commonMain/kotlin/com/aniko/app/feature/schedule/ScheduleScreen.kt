package com.aniko.app.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.Release
import com.aniko.model.Schedule
import com.aniko.model.WeekDay
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ChipRow
import com.aniko.ui.component.ProgressRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран расписания выхода эпизодов по дням недели (P5.T2-задел, доработан P9.T4-T6, P13.T5).
 *
 * ## Compact: вертикальный список 7 секций, а не день-селектор (P13.T5, было наоборот)
 * До P9.T4 экран уже рисовал все 7 дней друг под другом, но молча пропуская дни без релизов;
 * P9.T4/T5 заменили это на день-селектор [ChipRow] + контент только выбранного дня. Мокап
 * Claude Design (сверка Фазы 13) требует обратного на Compact-ширине: сплошной вертикальный
 * скролл всех 7 дней подряд ([ScheduleDaysList]), каждый со своим заголовком-секцией
 * ([DaySectionHeader]) и пустым состоянием на день без релизов — без табов вообще. Опасение
 * "7 полных лент — это больше скролла, чем один тап", из-за которого раньше выбрали табы,
 * мокапом не подтвердилось: там список действительно длинный, но это осознанный выбор дизайна,
 * не бага. Day-селектор (чипы) остаётся только на Medium/Expanded, см. ниже.
 *
 * ## Пустое состояние (P9.T5, доработан Track A 2026-09-08)
 * День без релизов не пропускается: вместо тяжёлого `AnixEmptyState` (компонент для пустого
 * экрана целиком, см. `LibraryScreen`) внутри списка из 7 секций показывается лёгкая
 * надпись `bodySmall 12sp textSecondary45` — без контейнера и центрирования. Работает
 * одинаково что в [ScheduleDaysList] на Compact, что в [DayColumn] на Medium/Expanded.
 *
 * ## Навигация по дням + подсветка "сегодня" (P9.T5, сужено до Medium/Expanded в P13.T5)
 * На Medium/Expanded чипы [ChipRow] — способ прокрутить колонки к нужному дню (см. ниже); на
 * Compact дня-селектора больше нет — все дни и так на экране, скроллить к нужному можно пальцем,
 * отдельный контрол избыточен. День "сегодня" помечен точкой после названия — сравнение с
 * [WeekDay] сегодняшнего дня уже есть в [ScheduleViewModel] (используется и `HomeViewModel` для
 * секции "Новые серии"); тот же маркер используется и в заголовках секций [DaySectionHeader].
 *
 * ## Wide-экраны: колонки на Medium и Expanded (P9.T6, гейт расширен с Expanded в P13.T5)
 * Мокап показал, что мульти-колоночная сетка дней нужна не только на Expanded, а на любой
 * [AnixWindowSize.isTwoPane]-ширине (то есть и на Medium/tablet тоже) — раньше здесь стояла
 * жёсткая проверка `== Expanded`, из-за чего Medium ошибочно показывал Compact-раскладку с
 * табами. Под селектором показываются колонки сразу нескольких дней (все 7, горизонтальный
 * скролл) — тап по чипу не фильтрует контент (он и так весь на экране), а прокручивает колонки
 * до нужного дня. Сама визуальная доводка сетки под desktop-ширину (P13.T7) — отдельная задача,
 * этот файл только расширяет условие её показа.
 */
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val windowSize = LocalAnixWindowSize.current

    // Track A (сверка Compact-раскладки, 2026-09-04): дефолтный цвет M3 Surface непрозрачен и
    // перекрывает корневую заливку приложения (`AppTheme`, iOS `systemGroupedBackground`) —
    // Transparent делает фон видимым сквозь экран.
    Surface(
        modifier = modifier.fillMaxSize().testTag(AnixTestTags.SCHEDULE_SCREEN_ROOT),
        color = Color.Transparent,
    ) {
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
        // Track A (сверка Compact-раскладки, 2026-09-04): макет хочет 20sp ExtraBold(800) вместо
        // дефолтного titleLarge (22sp Bold) — общий заголовок экрана, не специфичен раскладке
        // дней ниже (Compact/Medium/Expanded различаются только в ScheduleDaysList/
        // ScheduleColumns), поэтому правится как есть.
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        if (windowSize.isTwoPane) {
            DaySelector(
                selectedDay = selectedDay,
                today = today,
                strings = strings,
                onDaySelected = { day ->
                    onDaySelected(day)
                    val index = WeekDay.entries.indexOf(day)
                    coroutineScope.launch { columnsListState.animateScrollToItem(index) }
                },
            )
            ScheduleColumns(
                schedule = schedule,
                strings = strings,
                listState = columnsListState,
                today = today,
                onReleaseClick = { releaseId -> titleNavigator.openTitle(releaseId) },
            )
        } else {
            ScheduleDaysList(
                schedule = schedule,
                strings = strings,
                today = today,
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

/** "•" после названия — маркер "сегодня", общий и для чипов [DaySelector] (Medium/Expanded), и
 *  для заголовков секций [DaySectionHeader] (Compact, P13.T5) — не отдельная иконка, чтобы не
 *  тянуть Material Icons Extended ради одного значка точки, см. KDoc [ChipRow]. */
private fun WeekDay.chipLabel(
    today: WeekDay,
    strings: Strings,
): String {
    val name = displayName(strings)
    return if (this == today) "$name •" else name
}

/** Все 7 дней вертикальным списком сразу — Compact (P13.T5), замена дневных чипов-табов. Один
 *  [LazyColumn] на весь экран, а не по вложенному списку на день: `LazyColumn` внутри `LazyColumn`
 *  не умеет мерить бесконечную высоту вложенного скролла — секции дописываются в общий список
 *  плоскими `item`/`items` вызовами (тот же приём, которым [ScheduleColumns] собирает `LazyRow`
 *  из [DayColumn]-колонок, только тут по вертикали и без вложенности). */
@Composable
private fun ScheduleDaysList(
    schedule: Schedule,
    strings: Strings,
    today: WeekDay,
    onReleaseClick: (Int) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    LazyColumn(
        contentPadding = PaddingValues(horizontal = dimens.spaceM, vertical = dimens.spaceS),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        modifier = Modifier.fillMaxSize(),
    ) {
        WeekDay.entries.forEach { day ->
            item(key = "header_${day.name}") {
                DaySectionHeader(day = day, today = today, strings = strings)
            }

            val releases = schedule.releasesOn(day)
            if (releases.isEmpty()) {
                item(key = "empty_${day.name}") {
                    // Track A (сверка Compact-раскладки, 2026-09-04): AnixEmptyState — тяжёлый
                    // центрированный компонент с крупным паддингом, задуманный под пустой ЭКРАН
                    // целиком (см. LibraryScreen). Внутри списка из 7 секций макет хочет простую
                    // надпись без контейнера/центрирования — тот же лёгкий Text используется и в
                    // DayColumn на Medium/Expanded.
                    Text(
                        text = strings.scheduleEmptyDayMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary45,
                    )
                }
            } else {
                items(releases, key = { release -> "${day.name}_${release.id}" }) { release ->
                    // Track A: макет хочет горизонтальную строку (44×44 арт + название + прогресс-
                    // лейбл в контейнере w045/w07/radius12), а не вертикальную плитку-карточку
                    // ReleaseCard — заменено на ProgressRow, тот же паттерн, что уже даёт
                    // LibraryScreen.LibraryRows (см. её KDoc) для строки "Мои списки". У Release
                    // нет понятия "просмотрено пользователем" на этом экране (это расписание
                    // ВЫХОДА серий, не прогресс просмотра) — переиспользуем episodesReleased/
                    // episodesTotal вместо выдумывания нового поля модели, см. KDoc ProgressRow.
                    ProgressRow(
                        posterUrl = release.posterUrl,
                        title = release.title,
                        watchedEpisodes = release.episodesReleased,
                        totalEpisodes = release.episodesTotal,
                        onClick = { onReleaseClick(release.id) },
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(dimens.cornerM))
                                .background(colors.overlay045)
                                .border(1.dp, colors.overlay07, RoundedCornerShape(dimens.cornerM)),
                    )
                }
            }
        }
    }
}

/** Заголовок секции дня внутри [ScheduleDaysList] — название дня (с маркером "сегодня" через
 *  [chipLabel]) + разделительная линия, как в мокапе Claude Design (P13.T5). */
@Composable
private fun DaySectionHeader(
    day: WeekDay,
    today: WeekDay,
    strings: Strings,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Track A (сверка Compact-раскладки, 2026-09-04): макет хочет 11px/700 UPPERCASE t2-60
        // вместо titleMedium (Manrope 16sp SemiBold). uppercase() без Locale — в common-коде это
        // единственный доступный вариант (Locale-aware overload — платформенный API), и для
        // латиницы/кириллицы названий дней недели даёт корректный результат.
        Text(
            text = day.chipLabel(today, strings).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = colors.textSecondary60,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = colors.overlay08)
    }
}

/** Все 7 дней колонками сразу — Medium/Expanded (P9.T6, гейт расширен с Expanded в P13.T5):
 *  горизонтально прокручиваемый [LazyRow] колонок фиксированной ширины [dayColumnWidth], каждая —
 *  заголовок дня + своя вертикальная лента. */
@Composable
private fun ScheduleColumns(
    schedule: Schedule,
    strings: Strings,
    listState: LazyListState,
    today: WeekDay,
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
                    today = today,
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
    today: WeekDay,
    onReleaseClick: (Int) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    Column(
        modifier = Modifier.width(dayColumnWidth).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        DaySectionHeader(day = day, today = today, strings = strings)

        if (releases.isEmpty()) {
            Text(
                text = strings.scheduleEmptyDayMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textSecondary45,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(releases, key = { it.id }) { release ->
                    ProgressRow(
                        posterUrl = release.posterUrl,
                        title = release.title,
                        watchedEpisodes = release.episodesReleased,
                        totalEpisodes = release.episodesTotal,
                        onClick = { onReleaseClick(release.id) },
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(dimens.cornerM))
                                .background(colors.overlay045)
                                .border(1.dp, colors.overlay07, RoundedCornerShape(dimens.cornerM)),
                    )
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

/** Ширина колонки дня на Medium/Expanded (P9.T6) — как `sidebarWidth` в `SidebarSlot.kt`,
 *  локальная константа: единственный потребитель этой ширины, заводить общий токен в
 *  [AnixThemeTokens] ради одного экрана избыточно. */
private val dayColumnWidth = 280.dp
