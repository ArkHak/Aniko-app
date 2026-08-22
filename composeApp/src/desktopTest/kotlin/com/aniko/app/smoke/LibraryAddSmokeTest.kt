package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/**
 * P11.T3 — добавление в список.
 *
 * Домашний экран → карточка релиза 186 → статус-чип «Watching» в [com.aniko.app.feature.release.ReleaseHeaderSection]
 * (`ChipRow` над `ListStatus.entries`, `onChangeListStatus`) → оптимистичная запись
 * (`ReleaseDetailsViewModel.changeListStatus`, D4-паттерн: пишет в фейковую in-memory БД сразу,
 * фоновая синхронизация — no-op в [fakeInfraModule]) → чип помечается выбранным.
 *
 * Сетевых маршрутов сверх [defaultFixtureRoutes] не требуется — запись оптимистичная, локальная.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryAddSmokeTest {
    @Test
    fun addReleaseToWatchingList() {
        runAnikoSmokeTest(
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()

            // Фаза 11, T9 (устройство): AnixPoster/ChipRow теперь озвучивают заголовок/статус
            // через clearAndSetSemantics{contentDescription=...} (найдено на устройстве — иначе
            // TalkBack фокусировал их без имени) — поиск по видимому Text больше не находит эти
            // узлы, только по contentDescription (заодно снята прежняя неоднозначность: раньше
            // "Watching" совпадал с двумя узлами, clearAndSetSemantics оставляет только один).
            onNodeWithContentDescription("Темнее Черного", substring = true)
                .performScrollTo()
                .performClick()

            onNodeWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).assertIsDisplayed()

            val watchingChip = onNodeWithContentDescription("Watching")
            watchingChip.performScrollTo().performClick()

            watchingChip.assertIsSelected()
        }
    }
}
