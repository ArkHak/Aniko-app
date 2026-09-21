package com.aniko.app.feature.search

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.component.AnixSearchField
import com.aniko.ui.i18n.EnStrings
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Структура семантики [AnixSearchField] (то, что уходит в accessibility-дерево): имя поля и
 * редактируемый текст на ОДНОМ узле, кнопка очистки — отдельный кликабельный узел ≥ 48.dp,
 * IME-действие Search вызывает `onSearch`. На Desktop это проверяет именно структуру дерева
 * (общий код для всех платформ); озвучка TalkBack на устройстве — отдельная ручная проверка.
 */
@OptIn(ExperimentalTestApi::class)
class AnixSearchFieldSemanticsTest {
    @Test
    fun nameAndEditableTextLiveOnTheSameNode() =
        runFieldTest {
            // hasSetTextAction() и contentDescription — на одном и том же узле: скринридер получает
            // и имя, и «редактируемое поле» вместе (в отличие от OutlinedTextField под CMP).
            onNode(hasSetTextAction() and hasContentDescription(HINT)).assertExists()
        }

    @Test
    fun clearButtonAppearsWithTextHas48dpTargetAndClears() =
        runFieldTest {
            onNodeWithContentDescription(CLEAR).assertDoesNotExist()

            onNode(hasSetTextAction()).performTextInput("Магическая")
            onNode(hasSetTextAction()).assertTextEquals("Магическая")

            onNodeWithContentDescription(CLEAR)
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
                .performClick()

            onNode(hasSetTextAction()).assertTextEquals("")
            onNodeWithContentDescription(CLEAR).assertDoesNotExist()
        }

    @Test
    fun fieldIsAtLeast48dpTall() =
        runFieldTest {
            onNodeWithTag(FIELD_TAG).assertHeightIsAtLeast(48.dp)
        }

    @Test
    fun imeSearchActionInvokesOnSearch() {
        var searched = false
        runFieldTest(onSearch = { searched = true }) {
            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).performImeAction()
            waitForIdle()
            assertTrue(searched, "IME Search must invoke onSearch")
        }
    }

    private fun runFieldTest(
        onSearch: () -> Unit = {},
        body: androidx.compose.ui.test.SkikoComposeUiTest.() -> Unit,
    ) = runCatalogUiTest(
        size = Size(420f, 160f),
        windowSize = AnixWindowSize.Compact,
        strings = EnStrings,
        content = { Host(onSearch) },
        body = body,
    )

    @Composable
    private fun Host(onSearch: () -> Unit) {
        var value by remember { mutableStateOf("") }
        AnixSearchField(
            value = value,
            onValueChange = { value = it },
            placeholder = HINT,
            clearContentDescription = CLEAR,
            modifier = Modifier.width(360.dp).testTag(FIELD_TAG),
            onSearch = onSearch,
        )
    }

    private companion object {
        const val HINT = "Search anime…"
        const val CLEAR = "Clear search"
        const val FIELD_TAG = "field"
    }
}
