package com.aniko.app.feature.library

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.data.paging.PagingState
import com.aniko.model.ListStatus
import com.aniko.model.ProfileDetails
import com.aniko.model.Release
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixEmptyBox
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.ChipRow
import com.aniko.ui.component.ListStatusChip
import com.aniko.ui.component.ListStatusChipStyle
import com.aniko.ui.component.ProgressRow
import com.aniko.ui.component.ReleaseCard
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.lazy.itemsIndexed as itemsIndexedColumn

/**
 * Экран «Мои списки»: 7 вкладок (5 статусов [ListStatus] + избранное + история), каждая —
 * своя сетка [ReleaseCard] поверх независимого пагинатора вкладки (см. [LibraryViewModel]).
 *
 * Смена статуса/избранного/удаление сделаны через долгое нажатие на карточке (см.
 * [ReleaseCard.onLongClick]) и контекстное меню — сознательно не свайп, так решено в плане:
 * свайп на сетке (в отличие от списка) неоднозначен и конфликтует с горизонтальным скроллом
 * `ScrollableTabRow` выше.
 *
 * P9.T1: заголовки вкладок статусов/избранного дополнены счётчиком из `ProfileDetails`
 * (см. [LibraryTab.title]) — у истории отдельного счётчика в `ProfileDetails` нет, вкладка
 * остаётся без числа.
 *
 * P9.T2: тулбар над сеткой ([LibraryToolbar]) — shuffle (клиентская перетасовка уже
 * загруженных элементов) и реверс (серверный `sort=`, недоступен для истории). Оба переключателя
 * подробно задокументированы в [LibraryViewModel.toggleShuffle]/[LibraryViewModel.toggleReverse].
 *
 * P9.T3: контент ограничен [com.aniko.ui.theme.AnixDimens.contentMaxWidth] и центрирован на
 * wide-экранах — тот же паттерн, что `HomeScreen`/P7.T2. На
 * [com.aniko.ui.adaptive.AnixWindowSize.isTwoPane] сетка дополнительно переходит на увеличенный
 * постер ([com.aniko.ui.theme.AnixDimens.posterWidthL]),
 * как рельсы Фазы 6 (`HorizontalPosterRail`) — постоянный боковой каркас (`AnixSidebar`) уже
 * есть на уровне навигации приложения (`App.kt`/`shared/ui/.../adaptive/`), самому экрану
 * заводить его ещё раз не нужно.
 *
 * P13.T4: на [com.aniko.ui.adaptive.AnixWindowSize.Compact] грид постеров ([LibraryGrid]) заменён
 * на компактные горизонтальные строки ([LibraryRows]) — под мокап Claude Design, тот же паттерн
 * "48×48 арт + название + прогресс + чип справа", что уже даёт [ProgressRow] на Home (Continue
 * Watching). Medium/Expanded ([LibraryGrid]) не тронуты — аудит сверки с макетом пометил их как
 * "unaffected, works fine". Долгое нажатие/контекстное меню (смена статуса/избранное/удаление)
 * работает одинаково в обоих вариантах — [ProgressRow] получил тот же `onLongClick`, что уже
 * был у [ReleaseCard].
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val windowSize = LocalAnixWindowSize.current
    val selectedTab = uiState.selectedTab

    // Id релиза, для которого сейчас открыто контекстное меню — не Release целиком, чтобы меню
    // не "залипало" на устаревших данных карточки, если пагинатор успел обновить список.
    var menuReleaseId by remember { mutableStateOf<Int?>(null) }

    // Track A (сверка Compact-раскладки, 2026-09-04): дефолтный цвет M3 Surface непрозрачен и
    // перекрывает корневой радиальный градиент приложения (anixAppBackground()) — Transparent
    // делает фон/градиент видимым сквозь экран, как в макете.
    Surface(
        modifier = modifier.fillMaxSize().testTag(AnixTestTags.LIBRARY_SCREEN_ROOT),
        color = Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Track A: макет (`listsGroups`) рисует переключатель вкладок как ряд чипов
            // (тот же паттерн, что жанр-чипы Catalog), а не M3 таб-бар с подчёркиванием — только
            // на Compact. isTwoPane (Medium/Expanded) сохраняет прежний ScrollableTabRow дословно
            // (см. LibraryGrid/LibraryRows ниже — тот же уже существующий в файле паттерн
            // ветвления по windowSize.isTwoPane).
            if (windowSize.isTwoPane) {
                ScrollableTabRow(selectedTabIndex = LibraryTab.all.indexOf(selectedTab).coerceAtLeast(0)) {
                    LibraryTab.all.forEach { tab ->
                        val tabTitle = tab.title(strings, uiState.profile)
                        val tabSelected = tab == selectedTab
                        Tab(
                            selected = tabSelected,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tabTitle) },
                            // Подтверждено на устройстве (Фаза 11, T9): M3 Tab не сливает text{} в
                            // свой озвучиваемый узел (тот же паттерн, что и FilterChip/
                            // NavigationBarItem — см. ChipRow.kt/AnixNavigationBar.kt).
                            modifier =
                                Modifier.clearAndSetSemantics {
                                    contentDescription = tabTitle
                                    role = Role.Tab
                                    selected = tabSelected
                                },
                        )
                    }
                }
            } else {
                LibraryTabChips(
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> viewModel.selectTab(tab) },
                    strings = strings,
                    profile = uiState.profile,
                    modifier = Modifier.padding(horizontal = dimens.spaceM),
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.fillMaxSize().widthIn(max = dimens.contentMaxWidth)) {
                    val pagingState = uiState.pagingState

                    LibraryToolbar(
                        tab = selectedTab,
                        itemCount = pagingState.items.size,
                        isShuffled = uiState.isShuffled,
                        isReversed = uiState.isReversed,
                        onShuffleClick = { viewModel.toggleShuffle(selectedTab) },
                        onReverseClick = { viewModel.toggleReverse(selectedTab) },
                    )

                    when {
                        pagingState.error != null && pagingState.items.isEmpty() ->
                            AnixErrorBox(
                                // P2.T10: не показываем `error.message` напрямую — это текст исключения
                                // AnixError (технический, на английском, только для логов/debug), не
                                // локализованный UI-текст. Всегда локализованный fallback.
                                message = strings.libraryLoadError,
                                onRetry = { viewModel.retry(selectedTab) },
                                modifier = Modifier.fillMaxSize(),
                            )

                        pagingState.items.isEmpty() && (pagingState.isLoading || pagingState.isRefreshing) ->
                            AnixLoadingBox(
                                modifier = Modifier.fillMaxSize(),
                            )

                        pagingState.isEmpty ->
                            AnixEmptyBox(
                                message = selectedTab.emptyMessage(strings),
                                modifier = Modifier.fillMaxSize(),
                            )

                        // P13.T4: грид ([LibraryGrid]) остаётся на Medium/Expanded, Compact — новые
                        // компактные строки ([LibraryRows]), см. KDoc класса.
                        else ->
                            if (windowSize.isTwoPane) {
                                LibraryGrid(
                                    pagingState = pagingState,
                                    windowSize = windowSize,
                                    selectedTab = selectedTab,
                                    menuReleaseId = menuReleaseId,
                                    onMenuReleaseIdChange = { menuReleaseId = it },
                                    onReleaseClick = onReleaseClick,
                                    viewModel = viewModel,
                                )
                            } else {
                                LibraryRows(
                                    pagingState = pagingState,
                                    selectedTab = selectedTab,
                                    menuReleaseId = menuReleaseId,
                                    onMenuReleaseIdChange = { menuReleaseId = it },
                                    onReleaseClick = onReleaseClick,
                                    viewModel = viewModel,
                                )
                            }
                    }
                }
            }
        }
    }
}

/**
 * Track A (сверка Compact-раскладки, 2026-09-04): ряд чипов-групп статусов списков (макет
 * `listsGroups`) — Compact-замена [ScrollableTabRow] выше (тот же визуальный паттерн, что и
 * жанр-чипы Catalog, см. [ChipRow.selectedColor]). Medium/Expanded ([windowSize.isTwoPane])
 * продолжают использовать M3 таб-бар с подчёркиванием, эта функция для них не вызывается.
 */
@Composable
private fun LibraryTabChips(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    strings: Strings,
    profile: ProfileDetails?,
    modifier: Modifier = Modifier,
) {
    ChipRow(
        items = LibraryTab.all,
        isSelected = { it == selectedTab },
        label = { tab -> tab.title(strings, profile) },
        onClick = onTabSelected,
        modifier = modifier,
        selectedColor = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Сетка [ReleaseCard] — Medium/Expanded (P9.T3, layout не менялся в P13.T4: аудит сверки с
 * макетом пометил его "unaffected, works fine"). На Compact вместо неё — [LibraryRows].
 */
@Suppress("LongParameterList") // Координирующий блок: пагинация + ширина экрана + вкладка + меню-стейт +
// колбэк клика + viewModel (тот же паттерн передачи viewModel во внутренний composable, что уже
// у HomeScreen/SearchScreen).
@Composable
private fun LibraryGrid(
    pagingState: PagingState<Release>,
    windowSize: AnixWindowSize,
    selectedTab: LibraryTab,
    menuReleaseId: Int?,
    onMenuReleaseIdChange: (Int?) -> Unit,
    onReleaseClick: (Int) -> Unit,
    viewModel: LibraryViewModel,
) {
    val dimens = AnixThemeTokens.dimens

    LazyVerticalGrid(
        columns =
            GridCells.Adaptive(
                minSize = if (windowSize.isTwoPane) dimens.posterWidthL else dimens.posterWidth,
            ),
        contentPadding = PaddingValues(vertical = dimens.spaceM, horizontal = dimens.spaceS),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(pagingState.items, key = { _, release -> release.id }) { index, release ->
            if (index >= pagingState.items.size - LIBRARY_PREFETCH_THRESHOLD) {
                viewModel.loadMore(selectedTab)
            }
            Box {
                ReleaseCard(
                    release = release,
                    onClick = { onReleaseClick(release.id) },
                    onLongClick = { onMenuReleaseIdChange(release.id) },
                )
                LibraryContextMenu(
                    expanded = menuReleaseId == release.id,
                    release = release,
                    tab = selectedTab,
                    onDismiss = { onMenuReleaseIdChange(null) },
                    onChangeStatus = { status ->
                        viewModel.changeStatus(release, status)
                        onMenuReleaseIdChange(null)
                    },
                    onToggleFavorite = {
                        viewModel.toggleFavorite(release)
                        onMenuReleaseIdChange(null)
                    },
                    onRemoveFromList = { status ->
                        viewModel.removeFromList(release, status)
                        onMenuReleaseIdChange(null)
                    },
                    onRemoveFromHistory = {
                        viewModel.removeFromHistory(release)
                        onMenuReleaseIdChange(null)
                    },
                )
            }
        }

        if (pagingState.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnixLoadingBox(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * P13.T4: компактные горизонтальные строки на Compact-ширине — под мокап Claude Design
 * (DesignSync `Reelwave Prototype.dc.html`, секция `isLists`/`activeListItems`): 48×48 арт +
 * название + подпись прогресса слева, статус-чип списка справа. Переиспользует [ProgressRow]
 * (тот же компонент, что уже рисует "Продолжить смотреть" на Home и по KDoc [ProgressRow]
 * задуман под трёх потребителей, включая Мои списки) — только с [ListStatusChip] в `trailing`
 * вместо пустого слота.
 *
 * Долгое нажатие → то же [LibraryContextMenu], что и у грида: [ProgressRow.onLongClick] заведён
 * через `combinedClickable`, поведение (смена статуса/избранное/удаление) не отличается от
 * Medium/Expanded — меняется только внешний вид строки, не логика.
 *
 * У истории и части «избранного» `release.myListStatus` может быть `null` (релиз не состоит ни
 * в одном статусном списке) — тогда чип справа просто не рисуется ([trailing] `null`), а не
 * подставляется выдуманный статус.
 */
@Suppress("LongParameterList", "LongMethod")
// LongParameterList: См. LibraryGrid — тот же координирующий паттерн, минус windowSize (Compact
// всегда один размер строки, ширина экрана строкам не нужна).
// LongMethod: Track A (сверка Compact-раскладки, 2026-09-04) добавил контейнер-modifier
// (фон/бордер/radius) вокруг ProgressRow — тело осталось линейным, разбиение добавило бы
// косвенность ради счётчика строк.
@Composable
private fun LibraryRows(
    pagingState: PagingState<Release>,
    selectedTab: LibraryTab,
    menuReleaseId: Int?,
    onMenuReleaseIdChange: (Int?) -> Unit,
    onReleaseClick: (Int) -> Unit,
    viewModel: LibraryViewModel,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    LazyColumn(
        contentPadding = PaddingValues(vertical = dimens.spaceM, horizontal = dimens.spaceS),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexedColumn(pagingState.items, key = { _, release -> release.id }) { index, release ->
            if (index >= pagingState.items.size - LIBRARY_PREFETCH_THRESHOLD) {
                viewModel.loadMore(selectedTab)
            }
            Box {
                ProgressRow(
                    posterUrl = release.posterUrl,
                    title = release.title,
                    watchedEpisodes = release.lastViewEpisode,
                    totalEpisodes = release.episodesTotal,
                    onClick = { onReleaseClick(release.id) },
                    onLongClick = { onMenuReleaseIdChange(release.id) },
                    trailing = {
                        release.myListStatus?.let { status ->
                            ListStatusChip(status, style = ListStatusChipStyle.Full)
                        }
                    },
                    // Track A (сверка Compact-раскладки, 2026-09-04): макет оборачивает строку
                    // "Мои списки" в контейнер w045/w07/radius12 — снаружи через modifier, сам
                    // ProgressRow.kt (общий и на Home Continue Watching) не тронут.
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(dimens.cornerM))
                            .background(colors.overlay045)
                            .border(1.dp, colors.overlay07, RoundedCornerShape(dimens.cornerM)),
                )
                LibraryContextMenu(
                    expanded = menuReleaseId == release.id,
                    release = release,
                    tab = selectedTab,
                    onDismiss = { onMenuReleaseIdChange(null) },
                    onChangeStatus = { status ->
                        viewModel.changeStatus(release, status)
                        onMenuReleaseIdChange(null)
                    },
                    onToggleFavorite = {
                        viewModel.toggleFavorite(release)
                        onMenuReleaseIdChange(null)
                    },
                    onRemoveFromList = { status ->
                        viewModel.removeFromList(release, status)
                        onMenuReleaseIdChange(null)
                    },
                    onRemoveFromHistory = {
                        viewModel.removeFromHistory(release)
                        onMenuReleaseIdChange(null)
                    },
                )
            }
        }

        if (pagingState.isLoading) {
            item {
                AnixLoadingBox(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * P9.T2: тулбар shuffle/реверс над сеткой вкладки. Shuffle доступен всегда (клиентская операция
 * над уже загруженным списком, см. [LibraryViewModel.toggleShuffle]), отключён только при
 * `itemCount <= 1`, когда перетасовывать нечего. Реверс скрыт для [LibraryTab.History] — у
 * `HistoryApi` нет параметра `sort` (см. её KDoc), показывать для неё переключатель было бы
 * обманчиво. Активный переключатель подсвечивается цветом `primary`.
 */
@Suppress("LongParameterList") // Координирующий блок: вкладка + счётчик + 2 состояния тогглов + 2 колбэка + modifier.
@Composable
private fun LibraryToolbar(
    tab: LibraryTab,
    itemCount: Int,
    isShuffled: Boolean,
    isReversed: Boolean,
    onShuffleClick: () -> Unit,
    onReverseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = dimens.spaceS),
        // Track A (сверка Compact-раскладки, 2026-09-04): макет добавляет счётчик "N titles"
        // слева от shuffle/реверс — SpaceBetween вместо End, чтобы счётчик и кнопки разошлись по
        // разным краям строки.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.libraryItemsCountFormat(itemCount),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary55,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Подтверждено на устройстве (Фаза 11, T9): IconButton не сливает
            // Icon.contentDescription в свой кликабельный узел (тот же паттерн, что и остальные
            // M3-компоненты этой фазы) — явный clearAndSetSemantics на самом IconButton.
            // Track A: макет рисует shuffle маленькой квадратной кнопкой 28×28 с фоном overlay06
            // — обёрнуто снаружи модификатором, сам IconButton внутри не тронут.
            IconButton(
                onClick = onShuffleClick,
                enabled = itemCount > 1,
                modifier =
                    Modifier
                        .size(SHUFFLE_BUTTON_SIZE)
                        .clip(RoundedCornerShape(dimens.cornerS))
                        .background(colors.overlay06)
                        .clearAndSetSemantics { contentDescription = strings.libraryShuffle },
            ) {
                AnixIcon(
                    name = "shuffle",
                    contentDescription = null,
                    filled = true,
                    tint = if (isShuffled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            if (tab != LibraryTab.History) {
                IconButton(
                    onClick = onReverseClick,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = strings.libraryReverseSort },
                ) {
                    AnixIcon(
                        name = "swap_vert",
                        contentDescription = null,
                        filled = true,
                        tint = if (isReversed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
        }
    }
}

/** Track A (сверка Compact-раскладки, 2026-09-04): размер квадратной кнопки shuffle в тулбаре
 *  (см. [LibraryToolbar]) — точное значение макета, не токен [AnixThemeTokens.dimens] (единственный
 *  потребитель — этот тулбар). */
private val SHUFFLE_BUTTON_SIZE = 28.dp

/**
 * Меню долгого нажатия: смена статуса (кроме текущего), тоггл избранного и удаление,
 * контекстно зависящее от активной вкладки — «из списка» на вкладках статусов, «из истории»
 * на истории. На вкладке избранного отдельного пункта удаления нет — эту роль играет тоггл
 * избранного (снятие галочки убирает карточку ровно так же).
 */
@Composable
private fun LibraryContextMenu(
    expanded: Boolean,
    release: Release,
    tab: LibraryTab,
    onDismiss: () -> Unit,
    onChangeStatus: (ListStatus) -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromList: (ListStatus) -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    val strings = LocalStrings.current

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ListStatus.entries.filter { it != release.myListStatus }.forEach { status ->
            DropdownMenuItem(
                text = { Text(strings.libraryMoveToStatus(status.displayName(strings))) },
                onClick = { onChangeStatus(status) },
            )
        }
        DropdownMenuItem(
            text = {
                Text(if (release.isFavorite) strings.commonRemoveFromFavorites else strings.commonAddToFavorites)
            },
            onClick = onToggleFavorite,
        )
        when (tab) {
            is LibraryTab.Status ->
                DropdownMenuItem(
                    text = { Text(strings.libraryRemoveFromList) },
                    onClick = { onRemoveFromList(tab.status) },
                )

            LibraryTab.History ->
                DropdownMenuItem(
                    text = { Text(strings.libraryRemoveFromHistory) },
                    onClick = onRemoveFromHistory,
                )

            LibraryTab.Favorites -> Unit
        }
    }
}

/**
 * P9.T1: заголовок вкладки, дополненный счётчиком из [ProfileDetails], когда он есть
 * (см. [count]) — для истории счётчика нет, заголовок остаётся как раньше, без числа.
 */
private fun LibraryTab.title(
    strings: Strings,
    profile: ProfileDetails?,
): String {
    val base =
        when (this) {
            is LibraryTab.Status -> status.displayName(strings)
            LibraryTab.Favorites -> strings.libraryTabFavorites
            LibraryTab.History -> strings.libraryTabHistory
        }
    val count = count(profile)
    return if (count != null) strings.libraryTabCountFormat(base, count) else base
}

/**
 * Счётчик вкладки из [ProfileDetails] (P9.T1). У истории отдельного поля в [ProfileDetails] нет
 * (`watchedEpisodeCount` — это счётчик просмотренных СЕРИЙ, а не размер списка истории релизов,
 * это разные вещи) — сознательно возвращаем `null`, а не выдумываем несуществующее число.
 */
private fun LibraryTab.count(profile: ProfileDetails?): Int? =
    profile?.let {
        when (this) {
            is LibraryTab.Status ->
                when (status) {
                    ListStatus.WATCHING -> it.watchingCount
                    ListStatus.PLANNED -> it.planCount
                    ListStatus.COMPLETED -> it.completedCount
                    ListStatus.ON_HOLD -> it.holdOnCount
                    ListStatus.DROPPED -> it.droppedCount
                }
            LibraryTab.Favorites -> it.favoriteCount
            LibraryTab.History -> null
        }
    }

private fun LibraryTab.emptyMessage(strings: Strings): String =
    when (this) {
        is LibraryTab.Status -> strings.libraryEmptyStatus
        LibraryTab.Favorites -> strings.libraryEmptyFavorites
        LibraryTab.History -> strings.libraryEmptyHistory
    }

private const val LIBRARY_PREFETCH_THRESHOLD = 6
