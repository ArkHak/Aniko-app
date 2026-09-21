package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.ExpandedScreenTitle
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран Catalog/Search (P7.T3-T6) — MVI-контракт см. `SearchContract.kt`/`SearchViewModel.kt`.
 * Пакет остаётся `feature.search` (не переименован в `feature.catalog`) — "Catalog" здесь только
 * UI-имя в текстах/KDoc, см. бриф трека B.
 *
 * Раскладка одна на все размеры окна ([LocalAnixWindowSize] — глобально предоставлен в `App.kt`,
 * этот файл только читает): заголовок раздела (по размеру окна) → единая верхняя панель
 * [CatalogToolbar] (поиск + «Все/Новинки» + чипы «Статус»/«Жанры» + «Сбросить») → выдача
 * ([CatalogResultsGrid]) на всю оставшуюся ширину. Боковой колонки фильтров больше нет —
 * различаются только представление меню чипов и адаптивная сетка/список выдачи.
 *
 * Состояние и ViewModel держит только эта обёртка; всё остальное — в [CatalogScreenContent],
 * который зависит лишь от [SearchState] и колбэка интентов (так его можно рисовать и в тестах
 * без Koin/ViewModel).
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // P2.T10/см. HomeScreen (тот же паттерн и причина): эффект несёт только доменную AnixError,
    // локализованный снекбар — TODO, когда SnackbarHostState станет доступен экрану (вне
    // территории трека B — не трогаем App.kt).
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SearchEffect.ShowError -> Unit
        }
    }

    CatalogScreenContent(
        state = state,
        onIntent = viewModel::dispatch,
        onReleaseClick = onReleaseClick,
        modifier = modifier,
    )
}

/**
 * Безстейтная отрисовка Каталога: [state] + [onIntent]. Catalog-меню «⋮» (сверка 2026-09-08):
 * статусы списка идут через `LibraryRepository` тем же оптимистичным механизмом, что в Library
 * (см. `SearchViewModel`).
 */
@Composable
internal fun CatalogScreenContent(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val windowSize = LocalAnixWindowSize.current

    // Track A (сверка Compact-раскладки, 2026-09-04): дефолтный цвет M3 Surface непрозрачен и
    // перекрывает корневую заливку приложения (`AppTheme`, iOS `systemGroupedBackground`) —
    // Transparent делает фон видимым сквозь экран.
    Surface(
        modifier = modifier.fillMaxSize().testTag(AnixTestTags.SEARCH_SCREEN_ROOT),
        color = Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CatalogHeader(windowSize)
            CatalogToolbar(
                state = state,
                windowSize = windowSize,
                onIntent = onIntent,
                // Отступ от верхнего края/заголовка: у Expanded он уже заложен в ExpandedScreenTitle.
                modifier =
                    Modifier.padding(
                        top =
                            when (windowSize) {
                                AnixWindowSize.Compact -> dimens.spaceS
                                AnixWindowSize.Medium -> dimens.spaceM
                                AnixWindowSize.Expanded -> dimens.spaceXs
                            },
                    ),
            )
            CatalogResultsGrid(
                pagingState = state.pagingState,
                onReleaseClick = onReleaseClick,
                onLoadMore = { onIntent(SearchIntent.LoadMore) },
                onRetry = { onIntent(SearchIntent.Retry) },
                onSetListStatus = { id, status -> onIntent(SearchIntent.SetListStatus(id, status)) },
                onRemoveFromList = { id -> onIntent(SearchIntent.RemoveFromList(id)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Заголовок раздела над панелью: Compact — iOS Large Title (`displayLarge`, 34/41 Bold; та же
 * логика, что у `HomeGreetingHeader`), Expanded — [ExpandedScreenTitle] (единый заголовок-страница
 * Expanded-разделов, раньше жил внутри боковой панели фильтров), Medium — без заголовка (как и
 * раньше: на Medium панель идёт сразу под верхним краем).
 */
@Composable
private fun CatalogHeader(windowSize: AnixWindowSize) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    when (windowSize) {
        AnixWindowSize.Compact ->
            Text(
                text = strings.navCatalog,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = dimens.spaceM).padding(top = dimens.spaceM),
            )

        AnixWindowSize.Expanded -> ExpandedScreenTitle(text = strings.navCatalog)

        AnixWindowSize.Medium -> Unit
    }
}
