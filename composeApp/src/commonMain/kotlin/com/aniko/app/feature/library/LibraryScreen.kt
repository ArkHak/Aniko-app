package com.aniko.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.data.librarypreferences.LibraryViewMode
import com.aniko.data.paging.PagingState
import com.aniko.model.ListStatus
import com.aniko.model.ProfileDetails
import com.aniko.model.Release
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixEmptyState
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран «Мои списки»: 7 вкладок (5 статусов [ListStatus] + избранное + история), каждая —
 * независимый пагинатор вкладки (см. [LibraryViewModel]).
 *
 * Смена статуса/избранного/удаление сделаны через долгое нажатие на карточке и контекстное меню
 * ([LibraryContextMenu]) — сознательно не свайп, так решено в плане. Переключатель вкладок
 * нарисован горизонтальным рядом чипов ([LibraryTabChips]), а не M3 таб-баром, поэтому конфликта с
 * горизонтальным скроллом нет.
 *
 * P9.T1: заголовки вкладок статусов/избранного дополнены счётчиком из `ProfileDetails`
 * (см. [LibraryTab.title]) — у истории отдельного счётчика в `ProfileDetails` нет, вкладка
 * остаётся без числа.
 *
 * **Вид «Список» ↔ «Сетка постеров»** (2026-09-22). Два вида,
 * [LibraryViewMode], переключает кнопка-иконка ([LibraryViewModeButton]): в тулбаре
 * ([LibraryToolbar], Compact/Medium) или в заголовке ([LibraryExpandedHeader], Expanded).
 * - «Список»: Compact/Medium — строки с прогрессом ([LibraryRows], на Medium раскладываются в
 *   несколько колонок), Expanded — 3-колоночные горизонтальные карточки ([LibraryExpandedListCard]).
 * - «Сетка постеров»: плитки [com.aniko.ui.component.TitleCard] с бейджами ([LibraryPosterGrid]) на
 *   ВСЕХ размерах окна; на [com.aniko.ui.adaptive.AnixWindowSize.isTwoPane] минимальная ширина
 *   постера — [com.aniko.ui.theme.AnixDimens.posterWidthL], на телефоне —
 *   [com.aniko.ui.theme.AnixDimens.posterWidth].
 * Пока пользователь ничего не выбирал, вид зависит от размера окна ([defaultLibraryViewMode]:
 * Compact — список, Medium — сетка, Expanded — список), то есть ровно как до появления
 * переключателя. Явный выбор запоминается глобально (не по вкладкам) и действует на всех размерах
 * окна ([LibraryViewModel.setViewMode]). Долгое нажатие/контекстное меню работает одинаково во всех
 * видах и размерах, состояния загрузки/пусто/ошибка тоже общие.
 *
 * P9.T2: тулбар над содержимым ([LibraryToolbar]) — кнопка вида и реверс (серверный `sort=`,
 * недоступен для истории), см. [LibraryViewModel.toggleReverse].
 *
 * P9.T3: контент ограничен [com.aniko.ui.theme.AnixDimens.contentMaxWidth] и центрирован на
 * wide-экранах — тот же паттерн, что `HomeScreen`/P7.T2.
 *
 * P13.T4: на [com.aniko.ui.adaptive.AnixWindowSize.Compact] «Список» — компактные горизонтальные
 * строки ([LibraryRows]) — под мокап Claude Design, тот же паттерн "арт + название + прогресс +
 * чип справа", что уже даёт [com.aniko.ui.component.ProgressRow] на Home (Continue Watching).
 *
 * P13.T7: на [com.aniko.ui.adaptive.AnixWindowSize.Expanded] — desktop-раскладка «My Lists»:
 * заголовок + кнопка вида, чипы-вкладки и сетка под ними ([LibraryExpandedContent]).
 *
 * Ячейки всех видов обёрнуты в один overlay-контейнер (`overlay045`/`overlay07`/`cornerM`).
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
    // Явный выбор пользователя сильнее размера окна; null — «не выбирал» (см. LibraryUiState.viewMode).
    val viewMode = uiState.viewMode ?: windowSize.defaultLibraryViewMode()
    val onViewModeClick = { viewModel.setViewMode(viewMode.toggled()) }

    // Id релиза, для которого сейчас открыто контекстное меню — не Release целиком, чтобы меню
    // не "залипало" на устаревших данных карточки, если пагинатор успел обновить список.
    var menuReleaseId by remember { mutableStateOf<Int?>(null) }
    val actions =
        LibraryItemActions(
            tab = selectedTab,
            menuReleaseId = menuReleaseId,
            onMenuReleaseIdChange = { menuReleaseId = it },
            onReleaseClick = onReleaseClick,
            viewModel = viewModel,
        )

    // Track A (сверка Compact-раскладки, 2026-09-04): дефолтный цвет M3 Surface непрозрачен и
    // перекрывает корневую заливку приложения (`AppTheme`, iOS `systemGroupedBackground`) —
    // Transparent делает фон видимым сквозь экран.
    Surface(
        modifier = modifier.fillMaxSize().testTag(AnixTestTags.LIBRARY_SCREEN_ROOT),
        color = Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Сверка My Lists (phone-макет, 2026-09-08): заголовок страницы над вкладками —
            // только Compact (тот же паттерн, что заголовок Catalog). iOS-like редизайн
            // (2026-09-11, полный HIG-паттерн): подлинный iOS Large Title (`displayLarge`,
            // 34/41 Bold) — та же логика, что и `HomeGreetingHeader` (см. её KDoc).
            if (windowSize == AnixWindowSize.Compact) {
                Text(
                    text = strings.navLibrary,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceM).padding(top = dimens.spaceM),
                )
            }
            // Track A: макет (`listsGroups`) рисует переключатель вкладок как ряд чипов
            // (тот же паттерн, что жанр-чипы Catalog) — на Compact/Medium, см.
            // [LibraryTabChips]. На Expanded используется собственный ряд чипов под макет
            // desktop-артборда ([LibraryExpandedChips]).
            if (windowSize != AnixWindowSize.Expanded) {
                LibraryTabChips(
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> viewModel.selectTab(tab) },
                    strings = strings,
                    profile = uiState.profile,
                    modifier = Modifier.padding(horizontal = dimens.spaceM),
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (windowSize == AnixWindowSize.Expanded) {
                    LibraryExpandedContent(
                        pagingState = uiState.pagingState,
                        viewMode = viewMode,
                        actions = actions,
                        onViewModeClick = onViewModeClick,
                        onTabSelected = { tab -> viewModel.selectTab(tab) },
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().widthIn(max = dimens.contentMaxWidth)) {
                        LibraryToolbar(
                            tab = selectedTab,
                            itemCount = uiState.pagingState.items.size,
                            viewMode = viewMode,
                            isReversed = uiState.isReversed,
                            onViewModeClick = onViewModeClick,
                            onReverseClick = { viewModel.toggleReverse(selectedTab) },
                        )
                        LibraryCompactContent(
                            pagingState = uiState.pagingState,
                            viewMode = viewMode,
                            windowSize = windowSize,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Track A (сверка Compact-раскладки, 2026-09-04): ряд чипов-групп статусов списков (макет
 * `listsGroups`) — замена `ScrollableTabRow` на всех ширинах окна (тот же визуальный паттерн,
 * что и жанр-чипы Catalog, см. [ChipRow.selectedColor] = [MaterialTheme.colorScheme.primary]).
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
 * Содержимое вкладки на Compact/Medium под тулбаром: ветвление ошибка/загрузка/пусто/данные, а для
 * данных — вид [viewMode]: «Список» ([LibraryRows]) или «Сетка постеров» ([LibraryPosterGrid]).
 */
@Composable
private fun LibraryCompactContent(
    pagingState: PagingState<Release>,
    viewMode: LibraryViewMode,
    windowSize: AnixWindowSize,
    actions: LibraryItemActions,
) {
    val strings = LocalStrings.current
    val tab = actions.tab

    when {
        pagingState.error != null && pagingState.items.isEmpty() ->
            AnixErrorState(
                // P2.T10: не показываем `error.message` напрямую — это текст исключения
                // AnixError (технический, на английском, только для логов/debug), не
                // локализованный UI-текст. Всегда локализованный fallback.
                message = strings.libraryLoadError,
                onRetry = { actions.viewModel.retry(tab) },
                modifier = Modifier.fillMaxSize(),
            )

        pagingState.items.isEmpty() && (pagingState.isLoading || pagingState.isRefreshing) ->
            AnixLoadingState(modifier = Modifier.fillMaxSize())

        pagingState.isEmpty ->
            AnixEmptyState(message = tab.emptyMessage(strings), modifier = Modifier.fillMaxSize())

        viewMode == LibraryViewMode.List -> LibraryRows(pagingState = pagingState, actions = actions)

        else -> LibraryPosterGrid(pagingState = pagingState, windowSize = windowSize, actions = actions)
    }
}

/**
 * P9.T2: тулбар над содержимым вкладки: счётчик «N тайтлов» слева, справа — кнопка вида
 * «Список» ↔ «Сетка постеров» ([LibraryViewModeButton], порядок: [вид] [реверс]) и реверс. Реверс
 * скрыт для [LibraryTab.History] — у `HistoryApi` нет параметра `sort` (см. её KDoc), показывать для
 * неё переключатель было бы обманчиво. Активный реверс подсвечивается цветом `primary`.
 */
@Suppress("LongParameterList") // Координирующий блок: вкладка + счётчик + вид + реверс + 2 колбэка + modifier.
@Composable
private fun LibraryToolbar(
    tab: LibraryTab,
    itemCount: Int,
    viewMode: LibraryViewMode,
    isReversed: Boolean,
    onViewModeClick: () -> Unit,
    onReverseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = dimens.spaceS),
        // Track A (сверка Compact-раскладки, 2026-09-04): макет добавляет счётчик "N titles"
        // слева от кнопок — SpaceBetween вместо End, чтобы счётчик и кнопки разошлись по разным
        // краям строки.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.libraryItemsCountFormat(itemCount),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary55,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            LibraryViewModeButton(currentMode = viewMode, onClick = onViewModeClick)
            if (tab != LibraryTab.History) {
                // Подтверждено на устройстве (Фаза 11, T9): IconButton не сливает
                // Icon.contentDescription в свой кликабельный узел (тот же паттерн, что и остальные
                // M3-компоненты этой фазы) — явный clearAndSetSemantics на самом IconButton.
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

/**
 * Меню долгого нажатия: смена статуса (кроме текущего), тоггл избранного и удаление,
 * контекстно зависящее от активной вкладки — «из списка» на вкладках статусов, «из истории»
 * на истории. На вкладке избранного отдельного пункта удаления нет — эту роль играет тоггл
 * избранного (снятие галочки убирает карточку ровно так же).
 */
@Composable
internal fun LibraryContextMenu(
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

internal fun LibraryTab.emptyMessage(strings: Strings): String =
    when (this) {
        is LibraryTab.Status -> strings.libraryEmptyStatus
        LibraryTab.Favorites -> strings.libraryEmptyFavorites
        LibraryTab.History -> strings.libraryEmptyHistory
    }

internal const val LIBRARY_PREFETCH_THRESHOLD = 6
