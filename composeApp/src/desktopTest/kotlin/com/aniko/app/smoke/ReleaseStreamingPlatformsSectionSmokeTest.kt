package com.aniko.app.smoke

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aniko.app.feature.release.ReleaseHeaderSection
import com.aniko.app.feature.release.ReleaseStreamingPlatformsSection
import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseStreamingPlatform
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.i18n.EnStrings
import com.aniko.ui.theme.AppTheme
import kotlin.test.Test

/**
 * Офскрин-компоуз-тесты легальных стриминг-площадок Title Detail (сверено вживую 2026-09-23,
 * `GET release/streaming/platform/{releaseId}` — см. KDoc `ReleaseStreamingPlatformDto` в
 * `shared/data`, `ReleaseStreamingPlatformsSection` в этом модуле).
 *
 * Тот же паттерн standalone-рендера одного композабла в `AppTheme`, что и
 * [EpisodeGridColorSmokeTest]/[ContentSlotModifierSmokeTest] — без полного `runAnikoSmokeTest`
 * харнесса (ViewModel/Koin/сеть здесь не нужны, проверяется чистая презентация по входным данным).
 * `EnStrings` напрямую (не через `LocalStrings`/локаль) — тот же выбор, что описан в KDoc
 * [forceEnglishLocale]: `LocalStrings` по умолчанию и есть [EnStrings] (см. её `staticCompositionLocalOf`),
 * поэтому строки для проверки текста узлов берутся оттуда же, без дополнительного провайдера.
 */
@OptIn(ExperimentalTestApi::class)
class ReleaseStreamingPlatformsSectionSmokeTest {
    @Test
    fun emptyList_rendersNothing() {
        runSkikoComposeUiTest(size = Size(390f, 200f), density = Density(1f)) {
            setContent {
                AppTheme(darkTheme = false) {
                    ReleaseStreamingPlatformsSection(platforms = emptyList())
                }
            }
            // Секция намеренно не рисует пустой стейт (см. KDoc секции) — ни заголовка, ни строк.
            onNodeWithText(EnStrings.releaseStreamingPlatformsTitle).assertDoesNotExist()
        }
    }

    @Test
    fun nonEmptyList_rendersAllPlatforms() {
        val platforms =
            listOf(
                ReleaseStreamingPlatform(id = 1, name = "Kinopoisk", iconUrl = null, url = "https://example.com/1"),
                ReleaseStreamingPlatform(id = 5, name = "Ivi", iconUrl = null, url = "https://example.com/2"),
            )
        runSkikoComposeUiTest(size = Size(390f, 400f), density = Density(1f)) {
            setContent {
                AppTheme(darkTheme = false) {
                    ReleaseStreamingPlatformsSection(platforms = platforms)
                }
            }
            onNodeWithText(EnStrings.releaseStreamingPlatformsTitle).assertExists()
            // Каждая строка площадки — `clearAndSetSemantics { contentDescription = ... }` на всём
            // кликабельном Row (см. KDoc `StreamingPlatformRow`), поэтому поиск по видимому имени
            // (`onNodeWithText`) не находит цель — тот же случай, что и с кнопкой "Watch"
            // (см. KDoc теста [thirdPartyPlatformsDisabled_hidesWatchButtonInHeader] ниже).
            onNodeWithContentDescription(EnStrings.releaseStreamingPlatformOpenContentDescription("Kinopoisk")).assertExists()
            onNodeWithContentDescription(EnStrings.releaseStreamingPlatformOpenContentDescription("Ivi")).assertExists()
        }
    }

    /**
     * `ReleaseDetails.isThirdPartyPlatformsDisabled == true` скрывает кнопку "Смотреть" в шапке
     * (`ReleaseHeaderSection`/`WatchAndFavoriteRow.hideWatchAction`, см. её KDoc) — та часть
     * "обычного флоу выбора источника", которую можно проверить рендером одной секции без
     * `ReleaseDetailsScreen`/ViewModel целиком (сама подмена `ReleaseEpisodesSection` на
     * `ReleaseStreamingPlatformsSection` живёт в приватной `ReleaseDetailsContent` и напрямую не
     * адресуема из теста другого файла — покрыта на уровне публичного контракта шапки).
     * `AnixWindowSize.Medium` → `WideHeaderLayout`/`WatchAndFavoriteRow`, самая простая из трёх
     * раскладок шапки (без hero-обложки). Кнопка ищется по `contentDescription`, не по видимому
     * тексту — `HeroPlayButton` вешает `clearAndSetSemantics { contentDescription = ... }` на весь
     * кликабельный `Row` (см. её KDoc), что стирает `Text`-свойство вложенного `Text("Watch")` из
     * смёрженного семантического дерева; тот же приём поиска ("Watch") уже используется в
     * `BrowseDetailPlaySmokeTest.onNodeWithContentDescription("Watch")`.
     */
    @Test
    fun thirdPartyPlatformsDisabled_hidesWatchButtonInHeader() {
        val release = Release(id = 1, title = "Test title")
        val details = ReleaseDetails(release = release, isThirdPartyPlatformsDisabled = true)

        runSkikoComposeUiTest(size = Size(390f, 900f), density = Density(1f)) {
            setContent { HeaderUnderTest(release = release, details = details) }
            onNodeWithContentDescription(EnStrings.titleDetailWatch).assertDoesNotExist()
        }
    }

    /** Контроль к [thirdPartyPlatformsDisabled_hidesWatchButtonInHeader]: обычный случай
     *  (`isThirdPartyPlatformsDisabled == false`) кнопку не трогает. */
    @Test
    fun thirdPartyPlatformsEnabled_showsWatchButtonInHeader() {
        val release = Release(id = 1, title = "Test title")
        val details = ReleaseDetails(release = release, isThirdPartyPlatformsDisabled = false)

        runSkikoComposeUiTest(size = Size(390f, 900f), density = Density(1f)) {
            setContent { HeaderUnderTest(release = release, details = details) }
            onNodeWithContentDescription(EnStrings.titleDetailWatch).assertExists()
        }
    }

    /**
     * Регрессия на находку ревью: `HeroActionsRow` (Compact/`CompactHeroHeader` и
     * Expanded/`ExpandedDrawerHeader`, в отличие от Medium/`WatchAndFavoriteRow` выше) держит Play
     * и "Add to list" в ОДНОМ `Row`, и раньше только у Play был `Modifier.weight(1f)` — когда
     * `hideWatchAction` прячет Play, "Add to list" оставался без веса и вставал узкой кнопкой слева
     * с пустым местом справа вместо того, чтобы растянуться на весь ряд. Фикс — условный
     * `weight(1f)` у `HeroAddToListButton`, когда он остаётся единственным элементом ряда (см. её
     * KDoc). `AnixWindowSize.Compact` — раскладка, где баг реально проявлялся (Medium его не ловит,
     * см. [thirdPartyPlatformsDisabled_hidesWatchButtonInHeader]). Порог 300dp при ширине сцены
     * 390dp — кнопка без веса (обычная ширина по тексту "Add to list" + паддинги) заведомо уже,
     * регрессия сломает именно эту проверку, а не будущую точную раскладку.
     */
    @Test
    fun thirdPartyPlatformsDisabled_stretchesAddToListButtonAtCompact() {
        val release = Release(id = 1, title = "Test title")
        val details = ReleaseDetails(release = release, isThirdPartyPlatformsDisabled = true)

        runSkikoComposeUiTest(size = Size(390f, 900f), density = Density(1f)) {
            setContent {
                AppTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalAnixWindowSize provides AnixWindowSize.Compact) {
                        ReleaseHeaderSection(
                            release = release,
                            details = details,
                            detailsError = null,
                            isResolvingPlay = false,
                            onWatchClick = {},
                            onChangeListStatus = {},
                            onToggleFavorite = {},
                            onRetryDetails = {},
                            onShareClick = {},
                            onBackClick = {},
                        )
                    }
                }
            }
            onNodeWithContentDescription(EnStrings.titleDetailAddToList).assertWidthIsAtLeast(300.dp)
        }
    }
}

/** `AnixWindowSize.Medium` → `WideHeaderLayout`/`WatchAndFavoriteRow`, самая простая из трёх
 *  раскладок шапки (без hero-обложки) — общая обвязка обоих тестов [hideWatchAction] выше. */
@Composable
private fun HeaderUnderTest(
    release: Release,
    details: ReleaseDetails,
) {
    AppTheme(darkTheme = false) {
        CompositionLocalProvider(LocalAnixWindowSize provides AnixWindowSize.Medium) {
            ReleaseHeaderSection(
                release = release,
                details = details,
                detailsError = null,
                isResolvingPlay = false,
                onWatchClick = {},
                onChangeListStatus = {},
                onToggleFavorite = {},
                onRetryDetails = {},
                onShareClick = {},
                onBackClick = {},
            )
        }
    }
}
