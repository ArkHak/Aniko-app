package com.aniko.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.aniko.app.feature.comments.ReleaseCommentsScreen
import com.aniko.app.feature.release.ReleaseDetailsScreen
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Хост detail-панели поверх list-секций (P5.T3, трек B; desktop-проход 2026-09-15).
 *
 * До 2026-09-08 здесь жил `ListDetailPaneScaffold` из material3-adaptive (две панели рядом);
 * 2026-09-08 панели были выключены на всех размерах по тогдашнему макету; desktop-артборд мокапа
 * Claude Design (2026-09-15, блок `showDetail`) возвращает панель, но в другой форме — НЕ
 * разделение списка на колонки, а **правый ящик поверх списка**:
 * `position:absolute;top/right/bottom:0;width:520px;background:--bg-elevated;
 * border-left:1px solid --w09; box-shadow:-24px 0 60px rgba(0,0,0,0.4)`. Поэтому
 * material3-adaptive отсюда убран окончательно: его двухпанельная раскладка с
 * `defaultPanePreferredWidth` этот дизайн не выражает, а нужный результат — тривиальный [Box]
 * с оверлеем. Это также снимает историческую границу "единственный файл с импортом
 * experimental material3-adaptive" (она была нужна ровно для того кода, что удалён).
 *
 * Размерная вилка:
 * - `Expanded` + непустой [DetailPaneStack] — список + ящик 520dp поверх него справа;
 *   закрытие — `TitleNavigator.back()` (кнопка "назад" на hero обложки
 *   `ReleaseDetailsScreen`, см. её KDoc) / `DetailPaneStack.pop()`.
 * - `Expanded` + пустой стек, а также Compact/Medium всегда — только [listPane]: на
 *   Compact/Medium тайтл открывается полноэкранным маршрутом (`AdaptiveTitleNavigator`,
 *   `panesEnabled` в `App.kt` — `true` только на `Expanded`).
 *
 * Скрим под ящиком не рисуется (в мокапе его нет — список остаётся читаемым слева от ящика);
 * клик по списку под ящиком не перехватывается специально — клик по другой карточке просто
 * заменяет содержимое ящика ([DetailPaneStack.open] сбрасывает стек до нового `Details`).
 */
@Composable
fun ListDetailHost(
    paneStack: DetailPaneStack,
    modifier: Modifier = Modifier,
    listPane: @Composable () -> Unit,
) {
    val top = paneStack.top
    if (LocalAnixWindowSize.current != AnixWindowSize.Expanded || top == null) {
        listPane()
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        listPane()

        // border-left 1px --w09 из мокапа — рисуем через drawBehind (Modifier.border умеет
        // только все четыре стороны), поверх собственного фона ящика.
        val borderColor = AnixThemeTokens.colors.overlay09
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(DETAIL_DRAWER_WIDTH)
                    // box-shadow:-24px 0 60px rgba(0,0,0,0.4) — Compose не даёт задать смещение
                    // и размытие тени отдельно; 24dp elevation с прозрачным клипом — ближайший
                    // встроенный эквивалент (тень уходит влево от правого ящика симметрично).
                    .shadow(DETAIL_DRAWER_SHADOW_ELEVATION, RectangleShape, clip = false)
                    .background(MaterialTheme.colorScheme.surface)
                    .drawBehind {
                        drawLine(
                            color = borderColor,
                            start = Offset.Zero,
                            end = Offset(0f, size.height),
                            strokeWidth = DETAIL_DRAWER_BORDER_WIDTH.toPx(),
                        )
                    },
        ) {
            // key(route) обязателен: ящик живёт вне NavBackStackEntry, у него нет собственного
            // ViewModelStoreOwner на маршрут — без key(...) koinViewModel() внутри DetailPaneContent
            // возьмёт ОДИН И ТОТ ЖЕ ViewModelStoreOwner для всех тайтлов подряд, и при клике по
            // другому релизу в списке пользователь увидит старые данные предыдущего тайтла.
            key(top) { DetailPaneContent(top) }
        }
    }
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

// Литеральные px-значения desktop-артборда мокапа (`showDetail`, блок правого ящика) — не сведены
// к общим токенам AnixDimens намеренно: это точные величины одного конкретного места макета,
// а не переиспользуемые design-токены (тот же приём, что у hero-констант `ReleaseHeaderSection`).
private val DETAIL_DRAWER_WIDTH = 520.dp
private val DETAIL_DRAWER_SHADOW_ELEVATION = 24.dp
private val DETAIL_DRAWER_BORDER_WIDTH = 1.dp
