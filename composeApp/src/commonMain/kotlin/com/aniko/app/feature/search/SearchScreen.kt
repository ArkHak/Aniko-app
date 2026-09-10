package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.app.navigation.formatCatalogFilterLink
import com.aniko.model.CatalogContentType
import com.aniko.model.ListStatus
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.share.rememberShareController
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
 *   чипов ([CatalogInlineFilterChips]) прямо под полем поиска (P13.T3 — до этого жили за кнопкой
 *   "Фильтры" в `ModalBottomSheet`, мокап Claude Design рисует их постоянно видимыми, а не
 *   скрытыми в шторке). Medium отличается от Compact только тем, что `ListDetailHost`
 *   (`App.kt`, вне территории трека B) сам открывает detail-панель сбоку — сетка результатов
 *   получает меньше горизонтальной ширины, а `LazyVerticalGrid.Adaptive`
 *   ([CatalogResultsGrid]) сама пересчитывает число колонок, здесь ничего специального не нужно.
 * - Expanded — постоянная боковая панель [CatalogFilterPanel] (`Dimens.filterSidebarWidth`)
 *   слева от сетки, фильтры всегда развёрнуты.
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

    // P16.T2 — «поделиться» набором фильтров: тот же `ShareController`, что и у релиза
    // (`ReleaseDetailsScreen`), no snackbar-фидбэка на Desktop-фоллбэке — тот же пробел, что уже
    // есть у релиза (см. его KDoc про снекбар, TODO не входит в периметр этой задачи).
    val shareController = rememberShareController()
    val onShareFilter: () -> Unit = { shareController.shareText(formatCatalogFilterLink(state.filter)) }

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
    // перекрывает корневой радиальный градиент приложения (anixAppBackground()) — Transparent
    // делает фон/градиент видимым сквозь экран, как в макете.
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
                onShareFilter,
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
                onShareFilter,
            )
        }
    }
}

/** Expanded: постоянная боковая панель фильтров + сетка (см. KDoc [SearchScreen]). */
@Suppress("LongParameterList") // Состояние + VM + 5 колбэков, см. SearchScreen KDoc.
@Composable
private fun ExpandedCatalogLayout(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    viewModel: SearchViewModel,
    onReleaseClick: (Int) -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit,
    onRemoveFromList: (Int) -> Unit,
    onShareFilter: () -> Unit,
) {
    // P13.T7 [FIX]: сайдбар раньше держал фиксированные dimens.filterSidebarWidth (280dp)
    // независимо от реальной доступной ширины — на Desktop `ExpandedCatalogLayout` живёт внутри
    // constrained list-панели `ListDetailHost` (после P13.T6 максимум 560dp, на границе Medium/
    // Expanded — ~420dp), а не во весь экран, как предполагала исходная раскладка P7.T6. При
    // 280dp сайдбара body получал всего ~140-280dp — поле поиска и переключатель Сетка/Список
    // переносились по одной букве (найдено живьём при аудите Фазы 13). Пропорциональный `weight`
    // с потолком в исходные 280dp решает оба случая: на действительно широком экране сайдбар
    // упирается в свой прежний максимум 280dp (весь остаток уходит body, как и раньше), на узкой
    // list-панели сайдбар сжимается вместе с body пропорционально (1:2), не отъедая у него
    // непропорционально много места фиксированным числом.
    Row(modifier = Modifier.fillMaxSize()) {
        CatalogFilterPanel(
            filter = state.filter,
            myTab = state.myTab,
            onStatusToggle = { id -> viewModel.dispatch(SearchIntent.StatusToggled(id)) },
            onGenreToggle = { genre -> viewModel.dispatch(SearchIntent.GenreToggled(genre)) },
            onReset = { viewModel.dispatch(SearchIntent.FiltersReset) },
            onApplyMyTab = { viewModel.dispatch(SearchIntent.ApplyMyTab) },
            onSaveMyTab = { viewModel.dispatch(SearchIntent.SaveMyTab) },
            onClearMyTab = { viewModel.dispatch(SearchIntent.ClearMyTab) },
            onShareFilter = onShareFilter,
            modifier =
                Modifier
                    .weight(FILTER_SIDEBAR_WEIGHT, fill = false)
                    // Живая проверка (2026-09-03) вскрыла то же самое переносом-по-буквам на
                    // заголовке "Фильтры"/"Сброс": на границе Medium/Expanded 1:2-пропорция сама
                    // по себе может ужать сайдбар ниже FILTER_SIDEBAR_MIN_WIDTH — нижняя граница
                    // не даёт панели сжаться настолько, что даже её собственный заголовок
                    // перестаёт помещаться (список чипов внутри тоже теряет смысл на совсем
                    // узкой ширине).
                    .widthIn(min = FILTER_SIDEBAR_MIN_WIDTH, max = dimens.filterSidebarWidth)
                    .fillMaxHeight()
                    .padding(dimens.spaceM),
        )
        CatalogBody(
            state = state,
            strings = strings,
            dimens = dimens,
            onQueryChange = { q -> viewModel.dispatch(SearchIntent.QueryChanged(q)) },
            onContentTypeSelected = { type -> viewModel.dispatch(SearchIntent.ContentTypeSelected(type)) },
            onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
            onViewModeChanged = { mode -> viewModel.dispatch(SearchIntent.ViewModeChanged(mode)) },
            onReleaseClick = onReleaseClick,
            onSetListStatus = onSetListStatus,
            onRemoveFromList = onRemoveFromList,
            onLoadMore = { viewModel.dispatch(SearchIntent.LoadMore) },
            onRetry = { viewModel.dispatch(SearchIntent.Retry) },
            modifier = Modifier.weight(CATALOG_BODY_WEIGHT).fillMaxHeight(),
        )
    }
}

private const val FILTER_SIDEBAR_WEIGHT = 1f
private const val CATALOG_BODY_WEIGHT = 2f

/** Ниже этой ширины заголовок [CatalogFilterPanel] ("Фильтры"/"Сброс") сам не помещается в строку. */
private val FILTER_SIDEBAR_MIN_WIDTH = 200.dp

/**
 * Compact/Medium: чипы фильтров (см. [CatalogInlineFilterChips]) рендерятся прямо в [CatalogBody]
 * через слот `filtersContent`, никакой отдельной шторки/панели здесь больше нет (P13.T3, см. KDoc
 * [SearchScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Состояние + VM + 5 колбэков, см. SearchScreen KDoc.
@Composable
private fun CompactCatalogLayout(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    viewModel: SearchViewModel,
    onReleaseClick: (Int) -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit,
    onRemoveFromList: (Int) -> Unit,
    onShareFilter: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Сверка Catalog (phone-макет, 2026-09-08): заголовок экрана над поиском — только
        // Compact (на Medium/Expanded чипы/сайдбар идут без шапки).
        if (LocalAnixWindowSize.current == AnixWindowSize.Compact) {
            Text(
                text = strings.navCatalog,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = dimens.spaceM).padding(top = dimens.spaceM),
            )
        }
        CatalogBody(
            state = state,
            strings = strings,
            dimens = dimens,
            onQueryChange = { q -> viewModel.dispatch(SearchIntent.QueryChanged(q)) },
            onContentTypeSelected = { type -> viewModel.dispatch(SearchIntent.ContentTypeSelected(type)) },
            onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
            onViewModeChanged = { mode -> viewModel.dispatch(SearchIntent.ViewModeChanged(mode)) },
            onReleaseClick = onReleaseClick,
            onSetListStatus = onSetListStatus,
            onRemoveFromList = onRemoveFromList,
            onLoadMore = { viewModel.dispatch(SearchIntent.LoadMore) },
            onRetry = { viewModel.dispatch(SearchIntent.Retry) },
            modifier = Modifier.fillMaxSize(),
            filtersContent = {
                CatalogInlineFilterChips(
                    filter = state.filter,
                    myTab = state.myTab,
                    onStatusToggle = { id -> viewModel.dispatch(SearchIntent.StatusToggled(id)) },
                    onGenreToggle = { genre -> viewModel.dispatch(SearchIntent.GenreToggled(genre)) },
                    onApplyMyTab = { viewModel.dispatch(SearchIntent.ApplyMyTab) },
                    onSaveMyTab = { viewModel.dispatch(SearchIntent.SaveMyTab) },
                    onClearMyTab = { viewModel.dispatch(SearchIntent.ClearMyTab) },
                    onShareFilter = onShareFilter,
                )
            },
        )
    }
}

/**
 * Поле поиска + вкладки/переключатели + сетка результатов — общая часть Compact/Medium/Expanded
 * раскладок (см. KDoc [SearchScreen]), различие только в [filtersContent] (Compact/Medium
 * передают [CatalogInlineFilterChips], Expanded оставляет `null` — панель уже развёрнута сбоку,
 * P13.T3) и в модификаторе ширины со стороны вызова.
 */
@Suppress("LongParameterList", "LongMethod")
// LongParameterList: 7 колбэков экрана поверх общего SearchState + опциональный слот фильтров —
// один экран, разбиение колбэков в объект-параметр добавило бы уровень косвенности ради самого
// разбиения.
// LongMethod: за порог (60) вывели `clearAndSetSemantics{}`-модификаторы на кнопке очистки
// поиска/view-mode чипах (Фаза 11, T9 — IconButton/FilterChip не сливают contentDescription сами
// по себе, см. их KDoc) — тело осталось линейным раскладом поле+фильтры+чипы+сетка, разбиение
// добавило бы косвенность ради счётчика строк.
@Composable
private fun CatalogBody(
    state: SearchState,
    strings: Strings,
    dimens: AnixDimens,
    onQueryChange: (String) -> Unit,
    onContentTypeSelected: (CatalogContentType) -> Unit,
    onTabSelected: (CatalogTab) -> Unit,
    onViewModeChanged: (CatalogViewMode) -> Unit,
    onReleaseClick: (Int) -> Unit,
    onSetListStatus: (Int, ListStatus) -> Unit,
    onRemoveFromList: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    filtersContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(horizontal = dimens.spaceM)) {
        // P16.T1: табы «Аниме/Дунхуа» — самый верхний уровень каталога (в Anixart 10 они стоят
        // над шапкой главной и переключают СТРАНУ релиза, а не сортировку, см. KDoc
        // [CatalogContentType]). Ряд — тем же [ChipRow], что и остальные «табы» проекта
        // (Library/Catalog); от ряда «Все/Новинки» ниже он отделён полем поиска, поэтому два
        // ряда чипов подряд не читаются как один список.
        ChipRow(
            items = CatalogContentType.entries,
            isSelected = { it == state.filter.contentType },
            label = { type -> type.label(strings) },
            onClick = onContentTypeSelected,
            modifier = Modifier.fillMaxWidth().padding(top = dimens.spaceS),
            selectedColor = MaterialTheme.colorScheme.primary,
        )

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
                        AnixIcon(
                            name = "close",
                            contentDescription = null,
                            filled = true,
                        )
                    }
                }
            },
        )

        // P13.T3: чипы статус+жанр (Compact/Medium) — прямо под полем поиска, до вкладок
        // Все/Новинки. Expanded передаёт `null` — панель уже развёрнута сбоку CatalogFilterPanel.
        filtersContent?.invoke()

        // "Горизонтальный ряд чипов" из брифа P7.T3 — вкладки Все/Новинки (см. KDoc CatalogTab).
        // Track A: `selectedColor` — макет (`catalogViewTabs`) рисует активный таб с акцентной
        // заливкой/бордером/radius 10dp вместо дефолтного M3 FilterChip, см. KDoc ChipRow.
        ChipRow(
            items = CatalogTab.entries,
            isSelected = { it == state.tab },
            label = { tab -> tab.label(strings) },
            onClick = onTabSelected,
            modifier = Modifier.fillMaxWidth(),
            selectedColor = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = dimens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            // Подтверждено на устройстве (Фаза 11, T9): M3 FilterChip не сливает подпись в свой
            // озвучиваемый узел (тот же паттерн, что и ChipRow.kt/NavigationBarItem — см. их
            // KDoc).
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
        }

        CatalogResultsGrid(
            pagingState = state.pagingState,
            viewMode = state.viewMode,
            onReleaseClick = onReleaseClick,
            onSetListStatus = onSetListStatus,
            onRemoveFromList = onRemoveFromList,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Подпись таба типа контента (P16.T1) — ключи из i18n, а не значения API (те живут в `shared/data`). */
private fun CatalogContentType.label(strings: Strings): String =
    when (this) {
        CatalogContentType.ANIME -> strings.catalogContentTypeAnime
        CatalogContentType.DONGHUA -> strings.catalogContentTypeDonghua
    }

private fun CatalogTab.label(strings: Strings): String =
    when (this) {
        CatalogTab.All -> strings.catalogTabAll
        CatalogTab.New -> strings.catalogTabNew
    }
