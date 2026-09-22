package com.aniko.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.data.librarypreferences.LibraryViewMode
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Вид «Мои списки» по умолчанию для размера окна — пока пользователь ничего не выбирал
 * (`LibraryUiState.viewMode == null`): Compact — «Список» (строки с прогрессом), Medium — «Сетка
 * постеров», Expanded (Desktop) — «Список» (3-колоночные карточки с прогрессом). Это ровно то, что
 * экран показывал на каждом размере окна до появления переключателя, поэтому у тех, кто им не
 * пользуется, ничего не меняется. Явный выбор пользователя сильнее и действует на всех размерах.
 */
internal fun AnixWindowSize.defaultLibraryViewMode(): LibraryViewMode =
    when (this) {
        AnixWindowSize.Compact -> LibraryViewMode.List
        AnixWindowSize.Medium -> LibraryViewMode.Grid
        AnixWindowSize.Expanded -> LibraryViewMode.List
    }

/**
 * Кнопка-переключатель вида «Список» ↔ «Сетка постеров» — общая для тулбара Compact/Medium
 * ([LibraryToolbar]) и заголовка Expanded ([LibraryExpandedHeader]).
 *
 * Иконка и подпись показывают режим, НА который переключимся, а не текущий: в «Списке» это
 * `grid_view` + «Показать сеткой», в «Сетке» — `view_list` (глиф шрифта Material Symbols через
 * [AnixIcon]) + «Показать списком». Визуально — маленький квадрат [visualSize] на фоне `overlay06`
 * (макет Claude Design), но зона нажатия и семантический узел — не меньше
 * [com.aniko.ui.theme.AnixDimens.minTouchTarget] (48.dp): внешний `Box` растягивается до неё и
 * центрирует квадрат внутри. Семантика — на этом внешнем узле (`clickable` снаружи,
 * `clearAndSetSemantics` внутри: иначе очистка стёрла бы и `onClick`, см. паттерн `HeroBackButton`).
 */
@Composable
internal fun LibraryViewModeButton(
    currentMode: LibraryViewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualSize: Dp = LIBRARY_VIEW_MODE_BUTTON_SIZE,
    cornerRadius: Dp = LIBRARY_VIEW_MODE_BUTTON_CORNER,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val label =
        when (currentMode) {
            LibraryViewMode.List -> strings.libraryShowAsGrid
            LibraryViewMode.Grid -> strings.libraryShowAsList
        }
    val iconModifier = Modifier.size(LIBRARY_VIEW_MODE_ICON_SIZE)

    Box(
        modifier =
            modifier
                .testTag(LibraryTestTags.VIEW_MODE_TOGGLE)
                .sizeIn(minWidth = dimens.minTouchTarget, minHeight = dimens.minTouchTarget)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // Волна не ограничена 48.dp-зоной нажатия: радиус по видимому квадрату.
                    indication = ripple(bounded = false, radius = visualSize),
                    role = Role.Button,
                    onClick = onClick,
                ).clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(visualSize)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(colors.overlay06),
            contentAlignment = Alignment.Center,
        ) {
            when (currentMode) {
                LibraryViewMode.List ->
                    AnixIcon(name = "grid_view", contentDescription = null, modifier = iconModifier, filled = true)
                LibraryViewMode.Grid ->
                    AnixIcon(name = "view_list", contentDescription = null, modifier = iconModifier, filled = true)
            }
        }
    }
}

/**
 * Стабильные `testTag` экрана «Мои списки» для смоук-тестов: кнопка-переключатель вида и
 * контейнеры двух видов ([LIST_CONTENT] — строки/карточки с прогрессом, [GRID_CONTENT] — сетка
 * постеров). Тесты проверяют по ним, какой вид сейчас на экране, не привязываясь к вёрстке ячеек.
 */
internal object LibraryTestTags {
    const val VIEW_MODE_TOGGLE = "library_view_mode_toggle"
    const val LIST_CONTENT = "library_list_content"
    const val GRID_CONTENT = "library_grid_content"
}

/** Видимый размер квадрата кнопки в тулбаре Compact/Medium — точное значение макета (28×28). */
internal val LIBRARY_VIEW_MODE_BUTTON_SIZE = 28.dp

/** Видимый размер квадрата кнопки в заголовке Expanded — desktop-мокап. */
internal val LIBRARY_VIEW_MODE_BUTTON_SIZE_EXPANDED = 32.dp

private val LIBRARY_VIEW_MODE_BUTTON_CORNER = 8.dp
private val LIBRARY_VIEW_MODE_ICON_SIZE = 18.dp
