package com.aniko.app.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aniko.app.buildinfo.BuildInfo
import com.aniko.app.di.AppIconHelper
import com.aniko.app.di.NoOpAppIconHelper
import com.aniko.ui.testing.AnixTestTags
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import kotlin.test.Test

/**
 * Пункт «Версия приложения» — служебная сноска, прибитая к низу окна настроек ВНЕ
 * прокручиваемого списка (запрос пользователя 2026-09-24): информационная строка (намеренно
 * некликабельная) со значением, зашитым при сборке (`BuildInfo.APP_VERSION`, генератор —
 * `GenerateBuildInfoTask` из build-logic; единый источник версии — `anikoAppVersion` в
 * `composeApp/build.gradle.kts`). Тот же навигационный путь, что и
 * [DefaultVideoQualitySettingsSmokeTest]: Профиль → шестерёнка → Настройки.
 *
 * Намеренно БЕЗ `performScrollTo()`: футер виден сразу, без прокрутки списка (в этом и смысл
 * закрепления по низу окна — заодно такой ассерт не сломался бы, останься строка внутри
 * scrollable-контента, только если список умещается целиком; закреплённый футер проверяется
 * честно).
 */
@OptIn(ExperimentalTestApi::class)
class AppVersionSettingsSmokeTest {
    @Test
    fun settingsShowsAppVersionAtBottom() {
        runAnikoSmokeTest(
            initialToken = "fake-token",
            koinDeclaration = englishLocaleWithSettingsDependencies(),
        ) {
            onNodeWithTag(AnixTestTags.bottomNavItem("Profile")).performClick()
            onNodeWithContentDescription("Settings").performClick()
            onNodeWithTag(AnixTestTags.SETTINGS_SCREEN_ROOT).assertIsDisplayed()

            onNodeWithText("App version").assertIsDisplayed()
            onNodeWithText(BuildInfo.APP_VERSION).assertIsDisplayed()
        }
    }

    /**
     * Тот же набор зависимостей, что и в [DefaultVideoQualitySettingsSmokeTest] (`SettingsScreen`
     * берёт `AppIconHelper` из Koin — в проде его даёт `platformModule()`, здесь no-op
     * Desktop-реализация); дублируется намеренно: общего хелпера между смоук-тестами настроек в
     * проекте пока нет, выносить ради двух строк в общий файл — лишняя косвенность.
     */
    private fun englishLocaleWithSettingsDependencies(): KoinAppDeclaration =
        {
            forceEnglishLocale().invoke(this)
            modules(module { single<AppIconHelper> { NoOpAppIconHelper() } })
        }
}
