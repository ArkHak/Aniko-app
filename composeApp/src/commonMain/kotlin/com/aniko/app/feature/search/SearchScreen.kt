package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixDimens
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран Catalog/Search (P7.T3-T6) — MVI-контракт см. `SearchContract.kt`/`SearchViewModel.kt`.
 * Пакет остаётся `feature.search` (не переименован в `feature.catalog`) — "Catalog" здесь только
 * UI-имя в текстах/KDoc, см. бриф трека B.
 *
 * Раскладка по [AnixWindowSize] ([LocalAnixWindowSize] — глобально предоставлен в `App.kt`, этот
 * файл только читает, P5.T3):
 * - Compact/Medium — фильтры (статус+жанр, см. [CatalogFilterPanel]) живут в [ModalBottomSheet],
 *   открываемом кнопкой "Фильтры". Medium отличается от Compact только тем, что `ListDetailHost`
 *   (`App.kt`, вне территории трека B) сам открывает detail-панель сбоку — сетка результатов
 *   получает меньше горизонтальной ширины, а `LazyVerticalGrid.Adaptive`
 *   ([CatalogResultsGrid]) сама пересчитывает число колонок, здесь ничего специального не нужно.
 * - Expanded — постоянная боковая панель [CatalogFilterPanel] (`Dimens.filterSidebarWidth`)
 *   слева от сетки, фильтры всегда развёрнуты, [ModalBottomSheet] не используется вовсе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val isExpanded = LocalAnixWindowSize.current == AnixWindowSize.Expanded

    // P2.T10/см. HomeScreen (тот же паттерн и причина): эффект несёт только доменную AnixError,
    // локализованный снекбар — TODO, когда SnackbarHostState станет доступен экрану (вне
    // территории трека B — не трогаем App.kt).
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SearchEffect.ShowError -> Unit
        }
    }

    Surface(modifier = modifier.fillMaxSize().testTag(AnixTestTags.SEARCH_SCREEN_ROOT)) {
        if (isExpanded) {
            ExpandedCatalogLayout(state, strings, dimens, viewModel, onReleaseClick)
        } else {
            CompactCatalogLayout(state, strings, dimens, viewModel, onReleaseClick)
        }
    }
}

/** Expanded: постоянная боковая панель фильтров + сетка, без `ModalBottomSheet` (см. KDoc [SearchScreen]). */
@Composable
private fun ExpandedCatalogLayout(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    viewModel: SearchViewModel,
    onReleaseClick: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        CatalogFilterPanel(
            filter = state.filter,
            onStatusToggle = { id -> viewModel.dispatch(SearchIntent.StatusToggled(id)) },
            onGenreToggle = { genre -> viewModel.dispatch(SearchIntent.GenreToggled(genre)) },
            onReset = { viewModel.dispatch(SearchIntent.FiltersReset) },
            modifier =
                Modifier
                    .width(dimens.filterSidebarWidth)
                    .fillMaxHeight()
                    .padding(dimens.spaceM),
        )
        CatalogBody(
            state = state,
            strings = strings,
            dimens = dimens,
            showFiltersButton = false,
            onQueryChange = { q -> viewModel.dispatch(SearchIntent.QueryChanged(q)) },
            onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
            onViewModeChanged = { mode -> viewModel.dispatch(SearchIntent.ViewModeChanged(mode)) },
            onOpenFilters = {},
            onReleaseClick = onReleaseClick,
            onLoadMore = { viewModel.dispatch(SearchIntent.LoadMore) },
            onRetry = { viewModel.dispatch(SearchIntent.Retry) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** Compact/Medium: кнопка "Фильтры" открывает [ModalBottomSheet] (см. KDoc [SearchScreen]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactCatalogLayout(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    viewModel: SearchViewModel,
    onReleaseClick: (Int) -> Unit,
) {
    CatalogBody(
        state = state,
        strings = strings,
        dimens = dimens,
        showFiltersButton = true,
        onQueryChange = { q -> viewModel.dispatch(SearchIntent.QueryChanged(q)) },
        onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
        onViewModeChanged = { mode -> viewModel.dispatch(SearchIntent.ViewModeChanged(mode)) },
        onOpenFilters = { viewModel.dispatch(SearchIntent.FilterSheetVisibilityChanged(true)) },
        onReleaseClick = onReleaseClick,
        onLoadMore = { viewModel.dispatch(SearchIntent.LoadMore) },
        onRetry = { viewModel.dispatch(SearchIntent.Retry) },
        modifier = Modifier.fillMaxSize(),
    )

    if (state.isFilterSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dispatch(SearchIntent.FilterSheetVisibilityChanged(false)) },
        ) {
            CatalogFilterPanel(
                filter = state.filter,
                onStatusToggle = { id -> viewModel.dispatch(SearchIntent.StatusToggled(id)) },
                onGenreToggle = { genre -> viewModel.dispatch(SearchIntent.GenreToggled(genre)) },
                onReset = { viewModel.dispatch(SearchIntent.FiltersReset) },
                modifier = Modifier.padding(horizontal = dimens.spaceM),
            )
            Button(
                onClick = { viewModel.dispatch(SearchIntent.FilterSheetVisibilityChanged(false)) },
                modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
            ) {
                Text(strings.catalogFiltersApply)
            }
        }
    }
}

/**
 * Поле поиска + вкладки/переключатели + сетка результатов — общая часть Compact/Medium/Expanded
 * раскладок (см. KDoc [SearchScreen]), различие только в [showFiltersButton] (Expanded скрывает
 * кнопку "Фильтры" — панель уже развёрнута сбоку) и в модификаторе ширины со стороны вызова.
 */
@Suppress("LongParameterList", "LongMethod")
// LongParameterList: 8 колбэков экрана поверх общего SearchState — один экран, разбиение
// колбэков в объект-параметр добавило бы уровень косвенности ради самого разбиения.
// LongMethod: за порог (60) вывели `clearAndSetSemantics{}`-модификаторы на кнопке очистки
// поиска/view-mode чипах/кнопке "Фильтры" (Фаза 11, T9 — IconButton/FilterChip/TextButton не
// сливают contentDescription сами по себе, см. их KDoc) — тело осталось линейным раскладом
// поле+чипы+сетка, разбиение добавило бы косвенность ради счётчика строк.
@Composable
private fun CatalogBody(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    showFiltersButton: Boolean,
    onQueryChange: (String) -> Unit,
    onTabSelected: (CatalogTab) -> Unit,
    onViewModeChanged: (CatalogViewMode) -> Unit,
    onOpenFilters: () -> Unit,
    onReleaseClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = dimens.spaceM)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = dimens.spaceS),
            // [ИЗВЕСТНАЯ ПРОБЛЕМА, не решена в рамках T9] Подтверждено на устройстве: пустое
            // поле озвучивается TalkBack без имени — placeholder не даёт доступного имени сам по
            // себе (проверено дампом accessibility-дерева, content-desc/text у EditText пустые).
            // Ни внешний Modifier.semantics{}/clearAndSetSemantics, ни параметр `label` этого не
            // чинят: `modifier`/`label` OutlinedTextField в используемой версии Compose
            // Multiplatform (Android-таргет) не связываются с accessible name внутреннего
            // BasicTextField так, как задокументировано для androidx Material3 — похоже на
            // версионную особенность/баг именно этого CMP-релиза, не архитектуру проекта.
            // `clearAndSetSemantics` рискует стереть реальную EditableText/SetText-семантику поля
            // ради имени — сознательно не применён без возможности проверить TalkBack "на слух" в
            // этой среде. Требует отдельного расследования, не блокирует остальную Фазу 11.
            placeholder = { Text(strings.searchPlaceholder) },
            singleLine = true,
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    // P11.T7 (Трек C): было `Text("✕")` без осмысленной подписи для скринридера
                    // — заменено на Icon с contentDescription из уже существующего ключа (P11
                    // фундамент, F6). T9 на устройстве: сам IconButton всё равно не сливал его в
                    // свой кликабельный узел — добавлен clearAndSetSemantics.
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier =
                            Modifier.clearAndSetSemantics {
                                contentDescription = strings.searchClearContentDescription
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        // "Горизонтальный ряд чипов" из брифа P7.T3 — вкладки Все/Новинки (см. KDoc CatalogTab).
        ChipRow(
            items = CatalogTab.entries,
            isSelected = { it == state.tab },
            label = { tab -> tab.label(strings) },
            onClick = onTabSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = dimens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            // Подтверждено на устройстве (Фаза 11, T9): M3 FilterChip не сливает подпись в свой
            // озвучиваемый узел (тот же паттерн, что и ChipRow.kt/NavigationBarItem — см. их
            // KDoc); TextButton ниже пострадал так же, несмотря на единственного Text-потомка.
            FilterChip(
                selected = state.viewMode == CatalogViewMode.Grid,
                onClick = { onViewModeChanged(CatalogViewMode.Grid) },
                label = { Text(strings.catalogViewGrid) },
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = strings.catalogViewGrid
                        role = Role.Checkbox
                        selected = state.viewMode == CatalogViewMode.Grid
                    },
            )
            FilterChip(
                selected = state.viewMode == CatalogViewMode.List,
                onClick = { onViewModeChanged(CatalogViewMode.List) },
                label = { Text(strings.catalogViewList) },
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = strings.catalogViewList
                        role = Role.Checkbox
                        selected = state.viewMode == CatalogViewMode.List
                    },
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showFiltersButton) {
                TextButton(
                    onClick = onOpenFilters,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = strings.catalogFiltersTitle },
                ) { Text(strings.catalogFiltersTitle) }
            }
        }

        CatalogResultsGrid(
            pagingState = state.pagingState,
            viewMode = state.viewMode,
            onReleaseClick = onReleaseClick,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun CatalogTab.label(strings: Strings): String =
    when (this) {
        CatalogTab.All -> strings.catalogTabAll
        CatalogTab.New -> strings.catalogTabNew
    }
