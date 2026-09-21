package com.aniko.app.smoke

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aniko.app.di.AppIconHelper
import com.aniko.app.di.NoOpAppIconHelper
import com.aniko.data.playerpreferences.PlayerPreferencesStore
import com.aniko.ui.testing.AnixTestTags
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Раздел «Воспроизведение» экрана настроек: «Качество видео по умолчанию».
 *
 * Профиль → шестерёнка → Настройки → чипы Авто/1080p/720p/480p/360p. Проверяется то, что видит и
 * делает пользователь: дефолт «Авто» подсвечен, тап по чипу подсвечивает его и пишет в
 * [PlayerPreferencesStore] (тот же стор, из которого плеер берёт предпочтение при старте), «Авто»
 * возвращает «ничего не переопределять».
 */
@OptIn(ExperimentalTestApi::class)
class DefaultVideoQualitySettingsSmokeTest {
    @Test
    fun chooseDefaultVideoQuality_isHighlightedAndPersisted() {
        runAnikoSmokeTest(
            initialToken = "fake-token",
            koinDeclaration = englishLocaleWithSettingsDependencies(),
        ) {
            onNodeWithTag(AnixTestTags.bottomNavItem("Profile")).performClick()
            onNodeWithContentDescription("Settings").performClick()
            onNodeWithTag(AnixTestTags.SETTINGS_SCREEN_ROOT).assertIsDisplayed()

            val store = KoinPlatform.getKoin().get<PlayerPreferencesStore>()

            // Раздел размечен как заголовок (экранные читалки прыгают по разделам), есть подсказка про «ближайшее».
            onNodeWithText("Playback").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            onNodeWithText("Default video quality").performScrollTo().assertIsDisplayed()
            onNodeWithText("closest available quality", substring = true).assertIsDisplayed()

            // По умолчанию — «Авто»: ничего не переопределяем. Чипы размечены через
            // clearAndSetSemantics (паттерн ChipRow): подпись — в contentDescription; в merged-
            // дереве чипы схлопываются в родительский Row (его contentDescription — конкатенация
            // всех чипов, selected — от выбранного), поэтому ищем чипы в unmerged-дереве
            // (generic-файндер onNode — параметр у CMP 1.11 называется useUnmergedTree).
            val chip = { label: String -> onNode(hasContentDescription(label), useUnmergedTree = true) }
            assertNull(store.preferredQualityHeight.value)
            chip("Auto").assertIsSelected()
            chip("720p").assertIsNotSelected()

            chip("480p").performScrollTo().performClick()
            chip("480p").assertIsSelected()
            chip("Auto").assertIsNotSelected()
            assertEquals(480, store.preferredQualityHeight.value)

            chip("1080p").performScrollTo().performClick()
            chip("1080p").assertIsSelected()
            chip("480p").assertIsNotSelected()
            assertEquals(1080, store.preferredQualityHeight.value)

            chip("Auto").performScrollTo().performClick()
            chip("Auto").assertIsSelected()
            assertNull(store.preferredQualityHeight.value)
        }
    }

    /**
     * [forceEnglishLocale] + зависимость, которой нет в [fakeInfraModule]: `SettingsScreen` берёт
     * `AppIconHelper` из Koin (в проде его даёт `platformModule()`, здесь — no-op Desktop-реализация,
     * секция иконки скрыта). До этого теста в смоуках экран настроек не открывали.
     */
    private fun englishLocaleWithSettingsDependencies(): KoinAppDeclaration =
        {
            forceEnglishLocale().invoke(this)
            modules(module { single<AppIconHelper> { NoOpAppIconHelper() } })
        }
}
