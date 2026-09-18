package com.aniko.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.ui.glass.LocalGlassBackdrop
import com.aniko.ui.glass.glassBackdropSource
import com.aniko.ui.glass.rememberGlassBackdropState
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Адаптивный каркас приложения (P5.T5): bottom bar на [AnixWindowSize.Compact], nav rail на
 * [AnixWindowSize.Medium], постоянный sidebar на [AnixWindowSize.Expanded] — границы задаёт
 * [AnixWindowSize].
 *
 * Чистый UI-компонент: не знает про `AnixDestination`/Koin/`NavController` — вызывающая сторона
 * (composeApp) передаёт список [items] и обрабатывает клики через [onItemClick].
 *
 * @param sidebarHeader Слот в верхней части постоянного sidebar на [AnixWindowSize.Expanded].
 *   По умолчанию пустой. См. [AnixSidebar].
 * @param sidebarFooter Слот в нижней части постоянного sidebar на [AnixWindowSize.Expanded].
 *   По умолчанию пустой. См. [AnixSidebar].
 * @param showNavigationChrome `false` полностью убирает bottomBar/rail/sidebar, отдавая [content]
 * весь экран (P13, найдено живой проверкой) — нужен маршрутам, которые обязаны быть "поверх"
 * каркаса (плеер, см. KDoc `PlayerScreen`): `content` и раньше получал `PaddingValues` без
 * учёта этих панелей, но САМИ панели оставались нарисованы рядом (nav rail/sidebar) или под
 * (bottomBar) — на широком/альбомном окне играющее видео оставалось притиснутым к сайдбару
 * вместо честного fullscreen. `true` по умолчанию — поведение для всех остальных маршрутов
 * не меняется.
 *
 * **`movableContentOf` вокруг [content], не прямой вызов.** У этой функции четыре разных места
 * вызова [content] — по одному на ветку `when (windowSize)` плюс ранний `return` при
 * `showNavigationChrome == false`. Каждое место вызова — для Compose отдельный узел композиции;
 * когда `windowSize`/`showNavigationChrome` меняются НА ЛЕТУ (реальный поворот экрана пересекает
 * границу класса ширины; вход/выход из маршрута плеера переключает `showNavigationChrome`), без
 * `movableContentOf` Compose разбирал бы и заново строил ВСЁ поддерево внутри [content] (obычно
 * это целый `NavHost`) — живая проверка поймала это как два одновременно видимых UI плеера и
 * нерабочие кнопки во время такого перехода: `NavHost` прерывал свой обычный переход между
 * экранами (crossfade из старого и нового пункта назначения) на середине, потому что сам его
 * call site физически менялся. `movableContentOf` сохраняет идентичность поддерева при переносе
 * между этими местами вызова вместо разбора/пересборки.
 */
@Suppress("LongParameterList") // Публичная сигнатура зафиксирована брифом P5.T5: слот сайдбара
// (header), snackbarHost и content — все опциональны с дефолтами, кроме первых трёх
// (items/selectedItemId/onItemClick) и content. Дробить дальше (например, отдельный
// data class для сайдбар-слотов) добавило бы косвенность ради обхода линта, а не ради читаемости.
@Composable
fun AdaptiveScaffold(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
    modifier: Modifier = Modifier,
    windowSize: AnixWindowSize = LocalAnixWindowSize.current,
    showNavigationChrome: Boolean = true,
    sidebarHeader: @Composable ColumnScope.() -> Unit = {},
    sidebarFooter: @Composable ColumnScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val movableContent = remember { movableContentOf(content) }

    if (!showNavigationChrome) {
        movableContent(PaddingValues())
        return
    }

    when (windowSize) {
        AnixWindowSize.Compact -> {
            // Liquid Glass (2026-09-11, feature/liquid-glass-tab-bar): `backdropState` — общий
            // источник фона для `AnixNavigationBar`, живёт на уровне AdaptiveScaffold (не внутри
            // самого бара), т.к. просвечивать под стеклом должен КОНТЕНТ экрана
            // (`movableContent`), а не сам бар. `CompositionLocalProvider` оборачивает весь
            // `Scaffold` целиком — оба его слота (`bottomBar`/`content`) остаются потомками этой
            // composition-области, даже вызываясь изнутри `Scaffold`, поэтому `LocalGlassBackdrop`
            // виден обоим (тот же принцип, что `MaterialTheme` вокруг слотов `Scaffold`).
            val backdropState = rememberGlassBackdropState()
            CompositionLocalProvider(LocalGlassBackdrop provides backdropState) {
                Scaffold(
                    modifier = modifier,
                    // Track A (Foundation): фон приложения рисуется один раз на корне (`AppTheme`,
                    // iOS `systemGroupedBackground`) — непрозрачный дефолт Scaffold
                    // (`MaterialTheme.colorScheme.background`) перекрывал бы его плашкой сплошного
                    // цвета поверх всей области контента, поэтому здесь он явно прозрачный.
                    containerColor = Color.Transparent,
                    bottomBar = { AnixNavigationBar(items, selectedItemId, onItemClick) },
                    snackbarHost = snackbarHost,
                ) { innerPadding ->
                    // `LocalGlassBottomInset` = именно та нижняя часть [innerPadding], которую
                    // `Scaffold` посчитал под реально измеренный `bottomBar` (высота бара + системный
                    // inset) — экраны читают её отдельно от `innerPadding`, когда сами решают НЕ
                    // отступать от бара Modifier.padding'ом (см. KDoc [LocalGlassBottomInset] и
                    // `App.kt`), а прокидывают её в `contentPadding` своих `LazyColumn`/
                    // `LazyVerticalGrid`, чтобы контент физически продолжался под полупрозрачным
                    // баром (иначе блюрить там нечего — сплошной фон уже обрезан снаружи).
                    CompositionLocalProvider(
                        LocalGlassBottomInset provides innerPadding.calculateBottomPadding(),
                    ) {
                        Box(modifier = Modifier.fillMaxSize().glassBackdropSource(backdropState)) {
                            movableContent(innerPadding)
                        }
                    }
                }
            }
        }

        AnixWindowSize.Medium -> {
            Row(modifier = modifier) {
                AnixNavigationRail(items, selectedItemId, onItemClick)
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent, // см. комментарий в ветке Compact выше
                    snackbarHost = snackbarHost,
                ) { innerPadding -> movableContent(innerPadding) }
            }
        }

        AnixWindowSize.Expanded -> {
            Row(modifier = modifier) {
                AnixSidebar(
                    items = items,
                    selectedItemId = selectedItemId,
                    onItemClick = onItemClick,
                    header = sidebarHeader,
                    footer = sidebarFooter,
                )
                // Продуктовый запрос (2026-09-17): «добавить ограничительные линии между меню и
                // контентом в десктопе» — до этого границу сайдбара нёс только тон
                // sidebarBackgroundColor() (см. её KDoc в SidebarSlot.kt), который читался слабо.
                // Дефолты VerticalDivider (DividerDefaults.Thickness/color = outlineVariant) — тот
                // же волосяной разделитель, что HorizontalDivider() без параметров уже рисует по
                // проекту (FeedScreen/CollectionsScreen/CatalogResultsGrid), поэтому явные
                // thickness/color здесь не задаются.
                VerticalDivider()
                Scaffold(
                    // Живая проверка продуктом (2026-09-17, скриншот экрана «Каталог»): контент
                    // (CatalogFilterPanel/SearchScreen — заголовок «Каталог») начинался вплотную к
                    // VerticalDivider выше, без отступа — «тут надо добавить отступ от линии,
                    // везде надо добавить одинаковый отступ». Отступ задан здесь один раз (а не в
                    // каждом экране), чтобы гарантированно быть одинаковым во всех местах,
                    // использующих AdaptiveScaffold в Expanded. Сторону сайдбара трогать не
                    // нужно — его контент (пункты меню) уже не касается линии: SIDEBAR_OUTER_PADDING
                    // (см. SidebarSlot.kt) даёт 12dp между items и правым краем Surface, где рисуется
                    // разделитель.
                    modifier = Modifier.weight(1f).padding(start = AnixThemeTokens.dimens.spaceM),
                    containerColor = Color.Transparent, // см. комментарий в ветке Compact выше
                    snackbarHost = snackbarHost,
                ) { innerPadding -> movableContent(innerPadding) }
            }
        }
    }
}

/**
 * Высота нижнего плавающего Liquid Glass-бара (2026-09-11, feature/liquid-glass-tab-bar), которую
 * [AdaptiveScaffold] отдельно от [PaddingValues] прокидывает вниз по дереву на
 * [AnixWindowSize.Compact] — `0.dp` везде, где бара нет (Medium/Expanded, showNavigationChrome ==
 * false), и на Compact ДО того, как `Scaffold` в первый раз измерит `bottomBar`.
 *
 * **Зачем отдельно от [PaddingValues], которые и так приходят в `content`.** До этой фичи
 * `App.kt` применял `Modifier.padding(innerPadding)` целиком (включая нижнюю часть) поверх
 * `AnixNavGraph` — экран физически обрывался НАД баром, блюрить под ним было нечего (там просто
 * голая заливка фона `AppTheme`, а не реальный контент). Теперь `App.kt` применяет `innerPadding`
 * БЕЗ нижней составляющей (контент идёт edge-to-edge ПОД бар, см. её KDoc), а высоту бара
 * добавляют сами экраны через `contentPadding` своих `LazyColumn`/`LazyVerticalGrid` — иначе
 * последний элемент списка визуально прятался бы под непрозрачной частью бара. Публичная
 * сигнатура [AdaptiveScaffold] (`content: @Composable (PaddingValues) -> Unit`) при этом не
 * меняется — тот, кто ЕЩЁ не переведён на этот `CompositionLocal`, продолжает получать полный
 * [PaddingValues], как раньше (просто увидит непрозрачный, не блюрящий бар поверх своего контента,
 * что не ломает раскладку).
 */
val LocalGlassBottomInset: ProvidableCompositionLocal<Dp> = staticCompositionLocalOf { 0.dp }
