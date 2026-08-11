package com.aniko.ui.adaptive

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Адаптивный каркас приложения (P5.T5): bottom bar на [AnixWindowSize.Compact], nav rail на
 * [AnixWindowSize.Medium], постоянный sidebar на [AnixWindowSize.Expanded] — границы задаёт
 * [AnixWindowSize].
 *
 * Чистый UI-компонент: не знает про `AnixDestination`/Koin/`NavController` — вызывающая сторона
 * (composeApp) передаёт список [items] и обрабатывает клики через [onItemClick].
 */
@Suppress("LongParameterList") // Публичная сигнатура зафиксирована брифом P5.T5: два слота
// сайдбара (header/footer), snackbarHost и content — все опциональны с дефолтами, кроме первых
// трёх (items/selectedItemId/onItemClick) и content. Дробить дальше (например, отдельный
// data class для сайдбар-слотов) добавило бы косвенность ради обхода линта, а не ради читаемости.
@Composable
fun AdaptiveScaffold(
    items: List<AdaptiveNavItem>,
    selectedItemId: String?,
    onItemClick: (AdaptiveNavItem) -> Unit,
    modifier: Modifier = Modifier,
    windowSize: AnixWindowSize = LocalAnixWindowSize.current,
    sidebarHeader: @Composable ColumnScope.() -> Unit = {},
    sidebarFooter: @Composable ColumnScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    when (windowSize) {
        AnixWindowSize.Compact -> {
            Scaffold(
                modifier = modifier,
                bottomBar = { AnixNavigationBar(items, selectedItemId, onItemClick) },
                snackbarHost = snackbarHost,
            ) { innerPadding -> content(innerPadding) }
        }

        AnixWindowSize.Medium -> {
            Row(modifier = modifier) {
                AnixNavigationRail(items, selectedItemId, onItemClick)
                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = snackbarHost,
                ) { innerPadding -> content(innerPadding) }
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
                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = snackbarHost,
                ) { innerPadding -> content(innerPadding) }
            }
        }
    }
}
