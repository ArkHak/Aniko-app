package com.aniko.app.smoke

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixContentState
import kotlin.test.Test

/**
 * Регрессия на живой баг Pixel 7 (2026-09-10, не пойман компиляцией/юнит-тестами/ревью):
 * `AnixContentSlot`'s `else`-ветка (реальные данные, `state.items` непустой) тихо теряла свой
 * `modifier` — только эта ветка из четырёх не применяла его (loading/error/empty ветки передают
 * `modifier` в `AnixLoadingState`/`AnixErrorState`/`AnixEmptyState` напрямую). На практике
 * `modifier` часто несёт `ColumnScope.weight(1f)` от родительского `Column` (см. живой вызов в
 * `ReleaseCommentsScreen.kt`), а `content: @Composable (List<T>) -> Unit` не принимает `Modifier`
 * как параметр — передать его контенту было буквально некуда. Результат на устройстве: сиблинг
 * ПОСЛЕ `AnixContentSlot` в том же `Column` (там — `CommentComposer`) схлопывался до
 * `bounds=[0,0][0,0]` (`adb shell uiautomator dump`), потому что "потерявший вес" слот вёл себя
 * как обычный нераспорядительный ребёнок `Column` и жадно занимал fillMaxSize() без ограничения.
 *
 * Тест воспроизводит ту же форму: `Column` фиксированной высоты, `AnixContentSlot` в состоянии
 * с данными и `Modifier.weight(1f)` внутри неё, `Box(Modifier.fillMaxSize())` как контент слота
 * (аналог `PullToRefreshBox.fillMaxSize()`), несжатый по высоте сиблинг СРАЗУ ПОСЛЕ слота
 * (аналог `CommentComposer`). До фикса composer получал `maxHeight=0` от `Column` (весь бюджет
 * уходил "невзвешенному" слоту первым проходом измерения) и падал ниже порога; после фикса
 * `Box(modifier)` в `else`-ветке возвращает `weight(1f)` слоту, `Column` резервирует под composer
 * его собственную высоту первым проходом (для невзвешенных детей), и он остаётся видимым.
 */
@OptIn(ExperimentalTestApi::class)
class ContentSlotModifierSmokeTest {
    @Test
    fun weightedContentSlotLeavesRoomForSiblingBelow() {
        runSkikoComposeUiTest(size = Size(390f, 844f), density = Density(1f)) {
            setContent {
                Column(Modifier.fillMaxWidth().height(400.dp)) {
                    AnixContentSlot(
                        state = AnixContentState(items = listOf("a", "b", "c")),
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("contentSlot"),
                    ) { items ->
                        // Форма реального бага: PullToRefreshBox.fillMaxSize() внутри слота
                        // (ReleaseCommentsScreen.kt) — жадно занимает весь доступный размер, если
                        // modifier (и его weight()) до неё не долетел.
                        Box(Modifier.fillMaxSize()) {
                            Text(items.joinToString())
                        }
                    }
                    // Аналог CommentComposer: фиксированная высота, БЕЗ weight — именно такой
                    // сиблинг схлопывался до ~21px/нулевой кнопки на реальном устройстве.
                    Box(Modifier.fillMaxWidth().height(56.dp).testTag("composerBelow")) {
                        Text("Compose a comment")
                    }
                }
            }

            // До фикса: composerBelow получал maxHeight=0 от Column (весь бюджет уходил
            // "потерявшему вес" слоту первым проходом) — измеренная высота падала до 0dp, что
            // ниже порога. После фикса Column резервирует под него честные 56dp.
            onNodeWithTag("composerBelow").assertHeightIsAtLeast(40.dp)
        }
    }
}
