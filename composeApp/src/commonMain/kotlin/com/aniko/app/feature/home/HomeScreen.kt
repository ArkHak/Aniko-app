package com.aniko.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.app.ui.toContentState
import com.aniko.model.AnixError
import com.aniko.model.InterestingBanner
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixContentState
import com.aniko.ui.component.HorizontalPosterRail
import com.aniko.ui.component.TitleCard
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Главный экран под макет (P7.T1/T2): баннер-карусель топ-тайтлов, 4 плитки быстрых действий,
 * «Продолжить смотреть» (список прогресса, [ContinueWatchingSection]), «Рекомендации»,
 * «Обсуждаемое» (замена CUT «Top This Week», см. P0.T3), «Новые серии» (LOC-секция из
 * расписания). Единый скроллящийся `LazyColumn`, контент ограничен
 * [com.aniko.ui.theme.AnixDimens.contentMaxWidth] и центрирован на wide-экранах (P7.T2).
 *
 * Навигационные колбэки на плитки быстрых действий Catalog/Schedule/Library — опциональные
 * (дефолт `{}`): координатор фазы подключает реальную навигацию в `App.kt` без правки этой
 * сигнатуры (вне территории трека A этой фазы). «Случайный тайтл» — исключение: загрузка и
 * переход обрабатываются самой ViewModel (`HomeIntent.OpenRandomRelease` ->
 * `HomeEffect.NavigateToRelease`), т.к. это асинхронная операция с сетевым вызовом, а не чистая
 * навигация — экран сам вызывает уже переданный [onReleaseClick].
 */
@Suppress("LongParameterList") // Координирующий блок: экран + 4 навигационных колбэка + viewModel.
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    onCatalogClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val windowSize = LocalAnixWindowSize.current

    // P2.T10: эффект несёт только доменную AnixError — локализованный текст выбирает экран, не
    // ViewModel. `SnackbarHostState` сейчас живёт приватно в `AnixSessionGate` (App.kt) и не
    // прокинут сюда (вне рамок трека A — не трогаем файлы за пределами feature/home); пока это
    // просто демонстрирует паттерн CollectEffects (P5.T7). Показ снекбара — TODO координатора,
    // когда `SnackbarHostState` станет доступен экрану (общий хост/CompositionLocal).
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.ShowError -> Unit
            is HomeEffect.NavigateToRelease -> onReleaseClick(effect.releaseId)
        }
    }

    HomeContent(
        modifier = modifier,
        state = state,
        windowSize = windowSize,
        viewModel = viewModel,
        onReleaseClick = onReleaseClick,
        onCatalogClick = onCatalogClick,
        onScheduleClick = onScheduleClick,
        onLibraryClick = onLibraryClick,
    )
}

/** Тело `LazyColumn` вынесено из [HomeScreen] отдельной функцией (detekt `LongMethod`). */
@Suppress("LongParameterList") // Тот же координирующий блок, что и у HomeScreen — см. её KDoc.
@Composable
private fun HomeContent(
    state: HomeState,
    windowSize: AnixWindowSize,
    viewModel: HomeViewModel,
    onReleaseClick: (Int) -> Unit,
    onCatalogClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onLibraryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Surface(modifier = modifier.fillMaxSize().testTag(AnixTestTags.HOME_SCREEN_ROOT)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = dimens.contentMaxWidth),
                contentPadding = PaddingValues(vertical = dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
            ) {
                item(key = "home_hero") {
                    HomeHeroSection(
                        windowSize = windowSize,
                        bannerState = state.banners.toContentState { it.toHomeMessage(strings) },
                        onBannerClick = onReleaseClick,
                        onBannerRetry = { viewModel.dispatch(HomeIntent.RetryBanners) },
                        onCatalogClick = onCatalogClick,
                        onScheduleClick = onScheduleClick,
                        onLibraryClick = onLibraryClick,
                        onRandomClick = { viewModel.dispatch(HomeIntent.OpenRandomRelease) },
                    )
                }

                item(key = "home_continue_watching") {
                    ContinueWatchingSection(
                        title = strings.homeContinueWatching,
                        state = state.watching,
                        onReleaseClick = onReleaseClick,
                        onRetry = { viewModel.dispatch(HomeIntent.RetryWatching) },
                    )
                }

                homeRailItems(state, windowSize, strings, viewModel, onReleaseClick)
            }
        }
    }
}

/**
 * «Рекомендации»/«Обсуждаемое»/«Новые серии» — вынесено из [HomeContent] отдельной
 * `LazyListScope`-функцией (detekt `LongMethod`), не `@Composable`: `item { }` сам несёт
 * composable-лямбду, обёртка над ним им быть не обязана.
 */
@Suppress("LongParameterList") // Координирующий блок: 3 секции × (состояние + retry) + общие зависимости.
private fun LazyListScope.homeRailItems(
    state: HomeState,
    windowSize: AnixWindowSize,
    strings: Strings,
    viewModel: HomeViewModel,
    onReleaseClick: (Int) -> Unit,
) {
    item(key = "home_recommendations") {
        HorizontalPosterRail(
            title = strings.homeRecommendations,
            state = state.recommendations.toContentState { it.toHomeMessage(strings) },
            key = { it.id },
            onRetry = { viewModel.dispatch(HomeIntent.RetryRecommendations) },
            onLoadMore = { viewModel.dispatch(HomeIntent.LoadMoreRecommendations) },
            windowSize = windowSize,
            // P13.T7: на Expanded — сетка вместо горизонтального скролла (мокап Claude Design
            // рисует Home на десктопе сетками, не рельсами), см. KDoc [HorizontalPosterRail].
            gridOnExpanded = true,
        ) { release -> TitleCard(release = release, onClick = { onReleaseClick(release.id) }) }
    }

    item(key = "home_discussing") {
        HorizontalPosterRail(
            title = strings.homeSectionDiscussing,
            state = state.discussing.toContentState { it.toHomeMessage(strings) },
            key = { it.id },
            onRetry = { viewModel.dispatch(HomeIntent.RetryDiscussing) },
            windowSize = windowSize,
            gridOnExpanded = true,
        ) { release -> TitleCard(release = release, onClick = { onReleaseClick(release.id) }) }
    }

    item(key = "home_new_episodes") {
        HorizontalPosterRail(
            title = strings.homeSectionNewEpisodes,
            state = state.newEpisodes.toContentState { it.toHomeMessage(strings) },
            key = { it.id },
            onRetry = { viewModel.dispatch(HomeIntent.RetryNewEpisodes) },
            windowSize = windowSize,
            gridOnExpanded = true,
        ) { release ->
            TitleCard(release = release, onClick = { onReleaseClick(release.id) }, isNewEpisode = true)
        }
    }
}

/**
 * Верх экрана — баннер + плитки быстрых действий. Раскладка по [windowSize] (P7.T2):
 * Compact/Medium — колонкой (баннер во всю ширину, плитки под ним), Expanded — один `Row`
 * (баннер : плитки ≈ 2:1) — весь блок уже ограничен [AnixDimens.contentMaxWidth] родительским
 * `LazyColumn`, см. [HomeScreen].
 */
@Suppress("LongParameterList") // Координирующий блок: состояние баннера + 4 колбэка плиток + 2 колбэка баннера.
@Composable
private fun HomeHeroSection(
    windowSize: AnixWindowSize,
    bannerState: AnixContentState<InterestingBanner>,
    onBannerClick: (Int) -> Unit,
    onBannerRetry: () -> Unit,
    onCatalogClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onRandomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    if (windowSize == AnixWindowSize.Expanded) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            HomeBanner(
                state = bannerState,
                onBannerClick = onBannerClick,
                onRetry = onBannerRetry,
                modifier = Modifier.weight(EXPANDED_BANNER_WEIGHT),
            )
            HomeQuickActions(
                windowSize = windowSize,
                onCatalogClick = onCatalogClick,
                onScheduleClick = onScheduleClick,
                onLibraryClick = onLibraryClick,
                onRandomClick = onRandomClick,
                modifier = Modifier.weight(EXPANDED_TILES_WEIGHT),
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
            HomeBanner(state = bannerState, onBannerClick = onBannerClick, onRetry = onBannerRetry)
            HomeQuickActions(
                windowSize = windowSize,
                onCatalogClick = onCatalogClick,
                onScheduleClick = onScheduleClick,
                onLibraryClick = onLibraryClick,
                onRandomClick = onRandomClick,
            )
        }
    }
}

/** Мост `HomeState.SectionState -> AnixContentState` — локальный аналог `PagingStateAdapter.kt`
 *  (`composeApp/ui`, вне территории трека A) для непагинированных секций Home. */
private fun <T> SectionState<T>.toContentState(errorMessage: (AnixError) -> String): AnixContentState<T> =
    AnixContentState(items = items, isLoading = isLoading, errorMessage = error?.let(errorMessage))

/** См. KDoc `AnixError` — только `Network`/`Unauthorized` получают специфичный текст, остальное
 *  падает на общий `homeSectionLoadError` (как и раньше вело себя `HomeScreen`, до P7.T1). */
internal fun AnixError.toHomeMessage(strings: Strings): String =
    when (this) {
        is AnixError.Network -> strings.commonErrorNoConnection
        is AnixError.Unauthorized -> strings.commonErrorUnauthorized
        else -> strings.homeSectionLoadError
    }

private const val EXPANDED_BANNER_WEIGHT = 2f
private const val EXPANDED_TILES_WEIGHT = 1f
