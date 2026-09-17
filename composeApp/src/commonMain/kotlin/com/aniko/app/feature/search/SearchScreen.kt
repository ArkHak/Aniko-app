package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.model.ListStatus
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixIcon
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
 * - Compact/Medium — фильтры (статус+жанр) всегда видимы как две горизонтально скроллящиеся строки
 *   чипов ([CatalogInlineFilterChips]) прямо под полем поиска, вкладки «Все/Новинки» —
 *   [ChipRow] над полем поиска (P13.T3).
 * - Expanded — постоянная боковая панель [CatalogFilterPanel] фиксированной ширины 200.dp слева
 *   от выдачи; вкладки «Все/Новинки» перенесены в панель, поле поиска имеет max-width 360.dp,
 *   результаты — сетка из 5 колонок (desktop-артборд мокапа, строка 800).
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

    // Catalog-меню «⋮» (сверка 2026-09-08): статусы списка через LibraryRepository, тот же
    // оптимистичный механизм, что в Library (см. SearchViewModel).
    val onSetListStatus: (Int, ListStatus) -> Unit = { id, status ->
        viewModel.dispatch(SearchIntent.SetListStatus(id, status))
    }
    val onRemoveFromList: (Int) -> Unit = { id ->
        viewModel.dispatch(SearchIntent.RemoveFromList(id))
    }

    // P2.T10/см. HomeScreen (тот же паттерн и причина): эффект несёт только доменную AnixError,
    // локализованный снекбар — TODO, когда SnackbarHostState станет доступен экрану (вне
    // территории трека B — не трогаем App.kt).
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SearchEffect.ShowError -> Unit
        }
    }

    // Track A (сверка Compact-раскладки, 2026-09-04): дефолтный цвет M3 Surface непрозрачен и
    // перекрывает корневую заливку приложения (`AppTheme`, iOS `systemGroupedBackground`) —
    // Transparent делает фон видимым сквозь экран.
    Surface(
        modifier = modifier.fillMaxSize().testTag(AnixTestTags.SEARCH_SCREEN_ROOT),
        color = Color.Transparent,
    ) {
        if (isExpanded) {
            ExpandedCatalogLayout(
                state,
                strings,
                dimens,
                viewModel,
                onReleaseClick,
                onSetListStatus,
                onRemoveFromList,
            )
        } else {
            CompactCatalogLayout(
                state,
                strings,
                dimens,
                viewModel,
                onReleaseClick,
                onSetListStatus,
                onRemoveFromList,
            )
        }
    }
}

/** Expanded: фиксированная боковая панель 200.dp + выдача справа (см. KDoc [SearchScreen]). */
@Suppress("LongParameterList") // Состояние + VM + 3 колбэка, см. SearchScreen KDoc.
@Composable
private fun ExpandedCatalogLayout(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    viewModel: SearchViewModel,
    onReleaseClick: (Int) -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit,
    onRemoveFromList: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(EXPANDED_COLUMN_GAP),
    ) {
        CatalogFilterPanel(
            filter = state.filter,
            tab = state.tab,
            onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
            onStatusToggle = { id -> viewModel.dispatch(SearchIntent.StatusToggled(id)) },
            onGenreToggle = { genre -> viewModel.dispatch(SearchIntent.GenreToggled(genre)) },
            modifier =
                Modifier
                    .width(FILTER_SIDEBAR_WIDTH)
                    .fillMaxHeight(),
        )
        CatalogBody(
            state = state,
            strings = strings,
            dimens = dimens,
            onQueryChange = { q -> viewModel.dispatch(SearchIntent.QueryChanged(q)) },
            onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
            onReleaseClick = onReleaseClick,
            onSetListStatus = onSetListStatus,
            onRemoveFromList = onRemoveFromList,
            onLoadMore = { viewModel.dispatch(SearchIntent.LoadMore) },
            onRetry = { viewModel.dispatch(SearchIntent.Retry) },
            showTabs = false,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** Ширина левой колонки фильтров в Expanded (desktop-артборд, 200px). */
private val FILTER_SIDEBAR_WIDTH = 200.dp

/** Горизонтальный зазор между левой панелью и правой колонкой выдачи (мокап: 28px). */
private val EXPANDED_COLUMN_GAP = 28.dp

/**
 * Compact/Medium: чипы фильтров (см. [CatalogInlineFilterChips]) рендерятся прямо в [CatalogBody]
 * через слот `filtersContent`, никакой отдельной шторки/панели здесь больше нет (P13.T3, см. KDoc
 * [SearchScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Состояние + VM + 3 колбэка, см. SearchScreen KDoc.
@Composable
private fun CompactCatalogLayout(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    viewModel: SearchViewModel,
    onReleaseClick: (Int) -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit,
    onRemoveFromList: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Сверка Catalog (phone-макет, 2026-09-08): заголовок экрана над поиском — только
        // Compact (на Medium/Expanded чипы/сайдбар идут без шапки). iOS-like редизайн
        // (2026-09-11, полный HIG-паттерн): подлинный iOS Large Title (`displayLarge`, 34/41
        // Bold) — та же логика, что и `HomeGreetingHeader` (см. её KDoc).
        if (LocalAnixWindowSize.current == AnixWindowSize.Compact) {
            Text(
                text = strings.navCatalog,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = dimens.spaceM).padding(top = dimens.spaceM),
            )
        }
        CatalogBody(
            state = state,
            strings = strings,
            dimens = dimens,
            onQueryChange = { q -> viewModel.dispatch(SearchIntent.QueryChanged(q)) },
            onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
            onReleaseClick = onReleaseClick,
            onSetListStatus = onSetListStatus,
            onRemoveFromList = onRemoveFromList,
            onLoadMore = { viewModel.dispatch(SearchIntent.LoadMore) },
            onRetry = { viewModel.dispatch(SearchIntent.Retry) },
            modifier = Modifier.fillMaxSize(),
            filtersContent = {
                CatalogInlineFilterChips(
                    filter = state.filter,
                    onStatusToggle = { id -> viewModel.dispatch(SearchIntent.StatusToggled(id)) },
                    onGenreToggle = { genre -> viewModel.dispatch(SearchIntent.GenreToggled(genre)) },
                )
            },
        )
    }
}

/**
 * Вкладки «Все/Новинки» + поле поиска + чипы фильтров + список результатов — общая часть
 * Compact/Medium/Expanded раскладок. Различия:
 * - Expanded: вкладки скрыты (они рисуются в [CatalogFilterPanel]), поле поиска не
 *   растягивается на всю ширину, а ограничено 360.dp и стилизовано под desktop-артборд.
 * - Compact/Medium: поведение сохраняется (P13.T3).
 */
@Suppress("LongParameterList")
// 7 колбэков экрана поверх общего SearchState + опциональный слот фильтров — один экран,
// разбиение колбэков в объект-параметр добавило бы уровень косвенности ради самого разбиения.
@Composable
private fun CatalogBody(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    onQueryChange: (String) -> Unit,
    onTabSelected: (CatalogTab) -> Unit,
    onReleaseClick: (Int) -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit,
    onRemoveFromList: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTabs: Boolean = true,
    filtersContent: (@Composable () -> Unit)? = null,
) {
    val isExpanded = LocalAnixWindowSize.current == AnixWindowSize.Expanded
    Column(modifier = modifier.padding(horizontal = dimens.spaceM)) {
        // Вкладки «Все/Новинки» ([CatalogTab]) — для Compact/Medium над полем поиска.
        // Expanded передаёт showTabs = false, т.к. вкладки живут в боковой панели.
        if (showTabs) {
            CatalogTabsChipRow(
                state = state,
                strings = strings,
                dimens = dimens,
                onTabSelected = onTabSelected,
            )
        }

        CatalogSearchField(
            state = state,
            strings = strings,
            dimens = dimens,
            isExpanded = isExpanded,
            onQueryChange = onQueryChange,
        )

        // P13.T3: чипы статус+жанр (Compact/Medium) — прямо под полем поиска, перед списком.
        // Expanded передаёт `null` — панель уже развёрнута сбоку CatalogFilterPanel.
        filtersContent?.invoke()

        CatalogResultsGrid(
            pagingState = state.pagingState,
            onReleaseClick = onReleaseClick,
            onSetListStatus = onSetListStatus,
            onRemoveFromList = onRemoveFromList,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Вкладки «Все/Новинки» над полем поиска — только Compact/Medium (см. KDoc [CatalogBody]). */
@Composable
private fun CatalogTabsChipRow(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    onTabSelected: (CatalogTab) -> Unit,
) {
    ChipRow(
        items = CatalogTab.entries,
        isSelected = { it == state.tab },
        label = { tab -> tab.label(strings) },
        onClick = onTabSelected,
        modifier = Modifier.fillMaxWidth().padding(top = dimens.spaceS),
        selectedColor = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Поле поиска каталога — общий `OutlinedTextField`, стилизация зависит от [isExpanded] (см. KDoc
 * [CatalogBody]): Compact/Medium сохраняют iOS-like заливку без рамки, Expanded — desktop-артборд
 * (max-width 360.dp, высота 42.dp, бордер/заливка `overlay09`/`overlay05`, текст 13sp).
 */
@Composable
private fun CatalogSearchField(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    isExpanded: Boolean,
    onQueryChange: (String) -> Unit,
) {
    val searchModifier =
        if (isExpanded) {
            Modifier
                .widthIn(max = SEARCH_MAX_WIDTH)
                .height(SEARCH_FIELD_HEIGHT)
                .padding(bottom = dimens.spaceS)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.spaceS)
        }

    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = searchModifier,
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
        shape = if (isExpanded) RoundedCornerShape(SEARCH_FIELD_RADIUS) else RoundedCornerShape(dimens.cornerM),
        colors = catalogSearchFieldColors(isExpanded),
        textStyle =
            if (isExpanded) {
                MaterialTheme.typography.bodyMedium.copy(fontSize = SEARCH_FONT_SIZE)
            } else {
                MaterialTheme.typography.bodyLarge
            },
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
                    AnixIcon(
                        name = "close",
                        contentDescription = null,
                        filled = true,
                    )
                }
            }
        },
    )
}

/**
 * Compact/Medium: заливка без рамки (`outlineVariant`, iOS `systemGray6`-аналог, бордер
 * прозрачен, iOS-like редизайн 2026-09-11). Expanded: desktop-артборд — заливка `overlay05` +
 * бордер `overlay09`.
 */
@Composable
private fun catalogSearchFieldColors(isExpanded: Boolean) =
    if (isExpanded) {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AnixThemeTokens.colors.overlay05,
            unfocusedContainerColor = AnixThemeTokens.colors.overlay05,
            focusedBorderColor = AnixThemeTokens.colors.overlay09,
            unfocusedBorderColor = AnixThemeTokens.colors.overlay09,
        )
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
        )
    }

/** Max-width поля поиска в Expanded (мокап: 360px). */
private val SEARCH_MAX_WIDTH = 360.dp

/** Высота поля поиска в Expanded (мокап: 42px). */
private val SEARCH_FIELD_HEIGHT = 42.dp

/** Радиус поля поиска в Expanded (мокап: 10px). */
private val SEARCH_FIELD_RADIUS = 10.dp

/** Размер текста поля поиска в Expanded (мокап: 13px). */
private val SEARCH_FONT_SIZE = 13.sp

private fun CatalogTab.label(strings: Strings): String =
    when (this) {
        CatalogTab.All -> strings.catalogTabAll
        CatalogTab.New -> strings.catalogTabNew
    }
