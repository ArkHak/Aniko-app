package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/**
 * P11.T3 — добавление в список.
 *
 * Домашний экран → карточка релиза 186 → кнопка "Add to list" в
 * [com.aniko.app.feature.release.ReleaseHeaderSection] (`HeroAddToListButton`, phone Compact —
 * `DropdownMenu` со всеми `ListStatus`, `onChangeListStatus`) → оптимистичная запись
 * (`ReleaseDetailsViewModel.changeListStatus`, D4-паттерн: пишет в фейковую in-memory БД сразу,
 * фоновая синхронизация — no-op в [fakeInfraModule]) → лейбл кнопки меняется на выбранный статус.
 *
 * Track A (design-match-remaining-screens, 2026-09-04): на Compact-раскладке одиночный ряд чипов
 * `ChipRow` над `ListStatus.entries` заменён на кнопку с выпадающим меню (макет Claude Design,
 * `selectedAddListLabel ⌄`) — тест обновлён под новый виджет (был написан под старый `ChipRow`,
 * `assertIsSelected()` для `DropdownMenuItem` не применим, семантика `selected` там не выставлена).
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

            // Фаза 11, T9 (устройство): AnixPoster теперь озвучивает заголовок через
            // clearAndSetSemantics{contentDescription=...} (найдено на устройстве — иначе
            // TalkBack фокусировал её без имени) — поиск по видимому Text не находит этот узел,
            // только по contentDescription.
            onNodeWithContentDescription("Темнее Черного", substring = true)
                .performScrollTo()
                .performClick()

            onNodeWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).assertIsDisplayed()

            // Релиз ещё не в списке — кнопка озвучивает себя как "Add to list" (см.
            // `Strings.titleDetailAddToList`), не как конкретный статус.
            onNodeWithContentDescription("Add to list")
                .performScrollTo()
                .performClick()

            // `DropdownMenuItem` рисует обычный `Text`, не переопределяет семантику вручную (в
            // отличие от кнопки-триггера/бывшего `ChipRow`) — ищем по видимому тексту пункта меню.
            // Просто `onNodeWithText("Watching")` неоднозначен: где-то в уже скомпонованном фоне
            // экрана (за модальным `DropdownMenu`) есть ещё один узел с тем же текстом без
            // clickable-семантики (не сам виджет статуса) — фильтруем по наличию клика, это и
            // есть сам пункт меню, а не случайное совпадение текста.
            onNode(hasText("Watching") and hasClickAction()).performClick()

            // После выбора кнопка озвучивает уже текущий статус вместо плейсхолдера.
            onNodeWithContentDescription("Watching").assertIsDisplayed()
        }
    }
}
