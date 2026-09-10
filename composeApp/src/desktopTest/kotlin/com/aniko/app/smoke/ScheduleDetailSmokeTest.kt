package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aniko.app.smoke.fixtures.ApiFixtures
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/**
 * P11.T5 — расписание → детали.
 *
 * Нижняя навигация → «Schedule» ([AnixTestTags.SCHEDULE_SCREEN_ROOT]) → релиз понедельника
 * из `schedule` ([ApiFixtures.schedule], `id=19588`, "Аккуратная и симпатичная девочка...") →
 * [AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT].
 *
 * `release/19588` в `docs/api/samples` нет отдельного сэмпла (только урезанная карточка внутри
 * `schedule.json`, без `related`/`recommended`/комментариев — расширенный ответ ReleaseDetails
 * экран всё равно грузит отдельным вызовом). Переиспользован [ApiFixtures.release186Extended] —
 * несовпадение id полю проверки не мешает: сценарий проверяет саму навигацию Schedule → Details,
 * а не то, что открылся именно тайтл 19588.
 *
 * `ScheduleScreen` на Compact-ширине (P13.T5) показывает вертикальный скролл ВСЕХ 7 дней сразу —
 * дневного чип-селектора на Compact больше нет (было до P13.T5, тест раньше выбирал чип "Monday"
 * перед кликом по релизу). Релиз id=19588 из [ApiFixtures.schedule] лежит под понедельником и
 * так и остаётся видимым в дереве независимо от реального "сегодня" (`Clock.System.now()`) — все
 * 7 секций теперь на экране одновременно, достаточно проскроллить прямо к нужной карточке.
 *
 * Клик — по постеру (`onNodeWithContentDescription`), не по подписи-заголовку под ним: у
 * `ReleaseCard` (используется здесь и в `LibraryScreen`, `@Deprecated` в пользу `TitleCard` — см.
 * находку трека D) `OnClick` висит на самой картинке, текстовая подпись под ней — отдельный
 * несвязанный семантический узел без своего действия (проверено дампом дерева,
 * `onNodeWithText(title).performClick()` тут не находил цели).
 */
@OptIn(ExperimentalTestApi::class)
class ScheduleDetailSmokeTest {
    @Test
    fun scheduleReleaseOpensDetails() {
        runAnikoSmokeTest(
            apiRoutes = mapOf("release/19588" to { ApiFixtures.release186Extended }),
            initialToken = "fake-token",
            koinDeclaration = forceEnglishLocale(),
        ) {
            onNodeWithTag(AnixTestTags.bottomNavItem("Schedule")).performClick()
            onNodeWithTag(AnixTestTags.SCHEDULE_SCREEN_ROOT).assertIsDisplayed()

            // P13.T5: Compact больше не фильтрует по дню чипом — все 7 секций уже в дереве,
            // достаточно проскроллить прямо к релизу понедельника и кликнуть.
            // Флак в CI (2026-09-10): расписание грузится асинхронно, и карточка могла ещё не
            // попасть в дерево на момент клика — ждём её появления, как остальные смоуки ждут
            // свои экраны/узлы (ассерт не ослаблен, добавлено только ожидание).
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Аккуратная и симпатичная", substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            onNodeWithContentDescription("Аккуратная и симпатичная", substring = true)
                .performScrollTo()
                .performClick()

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT).assertIsDisplayed()
        }
    }
}
