package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 *   чипов ([CatalogInlineFilterChips]) прямо под полем поиска (P13.T3 — до этого жили за кнопкой
 *   "Фильтры" в `ModalBottomSheet`, мокап Claude Design рисует их постоянно видимыми, а не
 *   скрытыми в шторке). Medium отличается от Compact только тем, что `ListDetailHost`
 *   (`App.kt`, вне территории трека B) сам открывает detail-панель сбоку — список результатов
 *   получает меньше горизонтальной ширины, ничего специального здесь не нужно.
 * - Expanded — постоянная боковая панель [CatalogFilterPanel] (`Dimens.filterSidebarWidth`)
 *   слева от списка, фильтры всегда развёрнуты.
 *
 * Сверка с дизайном (2026-09-11, `local://design-catalog-reference`): область между полем поиска
 * и списком тайтлов упрощена до двух рядов чипов (статус, жанр) — три элемента, ранее жившие
 * здесь (табы «Аниме/Дунхуа» типа контента P16.T1, «Моя вкладка» P16.T2, переключатель
 * Сетка/Список), убраны с экрана как фичи по решению пользователя (см. журнал
 * `docs/REELWAVE_PLAN.md`): `CatalogContentType`/`CatalogFilter.contentType` остаётся в модели
 * (дефолт ANIME, входящие deep-link `type=donghua` по-прежнему применяются), но управлять им с
 * этого экрана больше нельзя; список результатов — теперь единственный режим (см. KDoc
 * `CatalogResultsGrid`). Вкладки «Все/Новинки» ([CatalogTab]) подняты ВЫШЕ поля поиска — тот же
 * слот, что "All/New Arrivals" в референсе. Поле поиска перекрашено в заливку без рамки
 * ([OutlinedTextFieldDefaults.colors] — `surfaceVariant`, `Color.Transparent` на бордере), тот же
 * компонент `OutlinedTextField`, только другие цвета (второй конвенции не заводим).
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

/** Expanded: постоянная боковая панель фильтров + список (см. KDoc [SearchScreen]). */
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
    // P13.T7 [FIX]: сайдбар раньше держал фиксированные dimens.filterSidebarWidth (280dp)
    // независимо от реальной доступной ширины — на Desktop `ExpandedCatalogLayout` живёт внутри
    // constrained list-панели `ListDetailHost` (после P13.T6 максимум 560dp, на границе Medium/
    // Expanded — ~420dp), а не во весь экран, как предполагала исходная раскладка P7.T6. При
    // 280dp сайдбара body получал всего ~140-280dp — поле поиска переносилось по одной букве
    // (найдено живьём при аудите Фазы 13). Пропорциональный `weight` с потолком в исходные 280dp
    // решает оба случая: на действительно широком экране сайдбар упирается в свой прежний
    // максимум 280dp (весь остаток уходит body, как и раньше), на узкой list-панели сайдбар
    // сжимается вместе с body пропорционально (1:2), не отъедая у него непропорционально много
    // места фиксированным числом.
    Row(modifier = Modifier.fillMaxSize()) {
        CatalogFilterPanel(
            filter = state.filter,
            onStatusToggle = { id -> viewModel.dispatch(SearchIntent.StatusToggled(id)) },
            onGenreToggle = { genre -> viewModel.dispatch(SearchIntent.GenreToggled(genre)) },
            onReset = { viewModel.dispatch(SearchIntent.FiltersReset) },
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
            onTabSelected = { tab -> viewModel.dispatch(SearchIntent.TabSelected(tab)) },
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
 * Compact/Medium/Expanded раскладок (см. KDoc [SearchScreen]), различие только в [filtersContent]
 * (Compact/Medium передают [CatalogInlineFilterChips], Expanded оставляет `null` — панель уже
 * развёрнута сбоку, P13.T3) и в модификаторе ширины со стороны вызова.
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
    filtersContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(horizontal = dimens.spaceM)) {
        // Вкладки «Все/Новинки» ([CatalogTab]) — подняты выше поля поиска (сверка с дизайном,
        // 2026-09-11): тот же слот, что "All/New Arrivals" в референсе, см. KDoc [SearchScreen].
        // Попытка заменить на iOS `UISegmentedControl`-компонент ([AnixSegmentedControl])
        // отменена: в связке с этим конкретным экраном `SearchFilterSmokeTest`
        // (`composeApp/src/desktopTest/...`) стабильно падал на непрозрачную ошибку видимости
        // поля поиска — root cause не установлен в бюджет этой задачи (не всплывающий стектрейс,
        // а несовпадение visible-bounds в `ComposeUiTest`, воспроизводится только в связке с
        // отсутствием/заменой именно этого ряда, не с самим `AnixSegmentedControl` в изоляции).
        // Оставлен `ChipRow` — рабочий, проверенный вариант; `AnixSegmentedControl` — в бэклоге.
        ChipRow(
            items = CatalogTab.entries,
            isSelected = { it == state.tab },
            label = { tab -> tab.label(strings) },
            onClick = onTabSelected,
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
            // iOS-like редизайн (2026-09-11): заливка вместо рамки — приглушённый серый трек
            // (`outlineVariant`, iOS `systemGray6`-аналог, отличим от белых карточек вокруг),
            // бордер прозрачен в обоих состояниях (тот же `OutlinedTextField`, не новый
            // компонент — второй конвенции поля ввода не заводим).
            shape = RoundedCornerShape(dimens.cornerM),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
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

private fun CatalogTab.label(strings: Strings): String =
    when (this) {
        CatalogTab.All -> strings.catalogTabAll
        CatalogTab.New -> strings.catalogTabNew
    }
