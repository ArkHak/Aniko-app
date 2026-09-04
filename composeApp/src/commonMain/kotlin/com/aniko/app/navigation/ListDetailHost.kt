package com.aniko.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aniko.app.feature.comments.ReleaseCommentsScreen
import com.aniko.app.feature.release.ReleaseDetailsScreen
import com.aniko.ui.adaptive.rememberListPanePreferredWidth
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Хост list-detail-панелей (P5.T3, трек B) — единственный файл во всём проекте, которому разрешено
 * импортировать `androidx.compose.material3.adaptive.*`. Это архитектурная граница: material3-adaptive
 * — experimental API (`@ExperimentalMaterial3AdaptiveApi`), его сигнатуры не гарантированы стабильными
 * между минорными версиями (см. журнал Фазы 5 в `docs/REELWAVE_PLAN.md`) — если API поменяется
 * несовместимо в будущей версии библиотеки, менять придётся ТОЛЬКО этот файл, а не каждый экран.
 *
 * Сигнатуры `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`/`calculateThreePaneScaffoldValue`/
 * `ListDetailPaneScaffold` сверены с исходниками androidx (`androidx.compose.material3.adaptive:
 * adaptive-layout:1.2.0`, `frameworks/support` на `androidx-main`) — не с декомпилированными `.class`,
 * а с реальным `.kt` (**уточнение P13.T6**: sources-jar для `org.jetbrains.compose.material3.adaptive:
 * adaptive-layout:1.2.0` в Gradle-кэше на самом деле есть — `.../modules-2/files-2.1/
 * org.jetbrains.compose.material3.adaptive/adaptive-layout/1.2.0/…/adaptive-layout-1.2.0-sources.jar`;
 * более ранняя версия этого комментария ошибочно утверждала обратное, не найдя его при поиске).
 *
 * Директива two-pane считается через `WithTwoPanesOnMediumWidth`, а не через дефолтный
 * `calculatePaneScaffoldDirective` — последний даёт две панели только на Expanded (>=840dp), а
 * граница two-pane в этом приложении — Medium (>=600dp), см. `AnixWindowSize.isTwoPane`/
 * `AnixWindowSize.MEDIUM_MIN_DP`. Расхождение этих двух границ прямо запрещено KDoc'ом
 * `AnixWindowSize` ("считается багом"), отсюда и выбор функции.
 *
 * `currentWindowAdaptiveInfo()`, а не `currentWindowAdaptiveInfoV2()`: последней в подключённой
 * версии артефакта (`org.jetbrains.compose.material3.adaptive:adaptive:1.2.0`) не существует
 * (проверено `javap` по реальному jar в Gradle-кэше — есть только `currentWindowAdaptiveInfo`, без
 * `Deprecated`-атрибута в байткоде; V2 и связанное с ним deprecation появились позже в
 * androidx-main, JetBrains-форк 1.2.0 их ещё не подтянул). Параметр по умолчанию
 * (`supportLargeAndXLargeWidth = false`) не имеет значения для наших двух партиций.
 *
 * **P13.T6 — явная ширина list-панели на `Expanded`.** `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`
 * сам по себе не принимает `defaultPanePreferredWidth` параметром (сверено с реальным `.kt` из
 * sources-jar артефакта, не с `.class` — см. `PaneScaffoldDirective.kt`), только `windowAdaptiveInfo`/
 * `verticalHingePolicy`; внутри он всегда берёт библиотечный дефолт 360.dp. Это годится для
 * телефона/планшета, но на широком Desktop-окне list-панель залипает на 360dp независимо от размера
 * окна, пока detail-панель растёт (полный разбор — KDoc [com.aniko.ui.adaptive.rememberListPanePreferredWidth],
 * единственный источник истины для этого числа). Поэтому здесь директива, полученная от
 * material3-adaptive, дополнительно прогоняется через `.copy(defaultPanePreferredWidth = …)` тем же
 * значением, что `rememberAnixWindowSize()` уже использует для классификации этого окна — оба числа
 * считаются из одной и той же ширины (`LocalWindowInfo.current.containerDpSize`), рассинхрон между
 * ними прямо запрещён KDoc'ом `AnixWindowSize`.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ListDetailHost(
    paneStack: DetailPaneStack,
    modifier: Modifier = Modifier,
    listPane: @Composable () -> Unit,
) {
    val top = paneStack.top

    // P13.T6: без override здесь material3-adaptive всегда берёт свои 360dp — верно для
    // телефона/планшета, но не растёт вместе с широким Desktop-окном (список постов/строк остаётся
    // в узкой нерастущей колонке). rememberListPanePreferredWidth() — единый источник истины,
    // вычисленный из той же ширины окна, что уже классифицирует rememberAnixWindowSize(); null
    // означает "не переопределять" (Compact/Medium, см. её KDoc).
    val listPanePreferredWidth = rememberListPanePreferredWidth()
    val baseDirective = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfo())
    val directive =
        if (listPanePreferredWidth != null) {
            baseDirective.copy(defaultPanePreferredWidth = listPanePreferredWidth)
        } else {
            baseDirective
        }

    // Всегда ровно один "текущий" destination — либо List (ничего не выбрано на панели детали),
    // либо Detail (releaseId — contentKey, чтобы calculateThreePaneScaffoldValue считал разные
    // тайтлы разными destination). Без явного List-элемента при top == null то же самое
    // вычисление отдало бы Detail по умолчанию (см. androidx `forEachPaneByPriority`: пустая
    // история весов деградирует к порядку Primary/Secondary/Tertiary, а Primary — это как раз
    // Detail) — на пустой панели список должен быть виден, а не наоборот.
    val currentDestination =
        if (top != null) {
            ThreePaneScaffoldDestinationItem(pane = ListDetailPaneScaffoldRole.Detail, contentKey = top)
        } else {
            ThreePaneScaffoldDestinationItem(pane = ListDetailPaneScaffoldRole.List, contentKey = Unit)
        }
    val value =
        calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = directive.maxHorizontalPartitions,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            currentDestination = currentDestination,
        )

    ListDetailPaneScaffold(
        directive = directive,
        value = value,
        listPane = { listPane() },
        detailPane = {
            // key(route) обязателен: панель живёт вне NavBackStackEntry, у неё нет собственного
            // ViewModelStoreOwner на маршрут — без key(...) koinViewModel() внутри DetailPaneContent
            // возьмёт ОДИН И ТОТ ЖЕ ViewModelStoreOwner для всех тайтлов подряд, и при клике по
            // другому релизу в списке пользователь увидит старые данные предыдущего тайтла.
            top?.let { route -> key(route) { DetailPaneContent(route) } } ?: DetailPanePlaceholder()
        },
        modifier = modifier,
    )
}

@Composable
private fun DetailPaneContent(route: DetailPaneRoute) {
    val titleNavigator = LocalTitleNavigator.current
    when (route) {
        is DetailPaneRoute.Details ->
            ReleaseDetailsScreen(
                releaseId = route.releaseId,
                onEpisodeClick = { releaseId, sourceId, position, host ->
                    titleNavigator.openPlayer(releaseId, sourceId, position, host)
                },
                viewModel = koinViewModel(key = "detail-pane-release-${route.releaseId}"),
            )

        is DetailPaneRoute.Comments ->
            ReleaseCommentsScreen(
                releaseId = route.releaseId,
                viewModel = koinViewModel(key = "detail-pane-comments-${route.releaseId}"),
            )
    }
}

@Composable
private fun DetailPanePlaceholder() {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            modifier = Modifier.padding(dimens.spaceL),
        ) {
            Text(text = strings.detailPaneEmptyTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                text = strings.detailPaneEmptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
