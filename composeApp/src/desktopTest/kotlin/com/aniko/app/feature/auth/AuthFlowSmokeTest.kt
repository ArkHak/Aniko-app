package com.aniko.app.feature.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.aniko.app.feature.search.saveShot
import com.aniko.app.smoke.forceEnglishLocale
import com.aniko.app.smoke.runAnikoSmokeTest
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/** Успешный `auth/signUp`: `hash` дальше уходит в `auth/verify`/`auth/resend` (см. `AuthApi`). */
private const val SIGN_UP_RESPONSE =
    """{"code":0,"hash":"fake-signup-hash","codeTimestampExpires":0,"suggested_logins":[]}"""

/** Успешный `auth/verify` — профиль + токен, то есть verify сам завершает вход. */
private const val VERIFY_OK_RESPONSE =
    """{"code":0,"profile":{"id":1,"login":"smoketester"},"profileToken":{"id":1,"token":"fake-token-1"}}"""

/** `auth/verify` с неверным кодом (`CODE_INVALID = 7`, см. `VerifyResponse` в jadx). */
private const val VERIFY_INVALID_CODE_RESPONSE = """{"code":7}"""

/** Успешный `auth/resend` — `timestampExpires` нового кода. */
private const val RESEND_OK_RESPONSE = """{"code":0,"timestampExpires":0}"""

/** Запас на «клик → сеть (MockEngine) → репозиторий → Flow → перекомпозиция». */
private const val AUTH_FLOW_TIMEOUT_MS = 5_000L

/**
 * Смоук-офскрин проверка auth-флоу после редизайна (задача auth-redesign, 2026-09-21): стартовый
 * кадр логина, переход на регистрацию и полный флоу «форма → код → verify с авто-входом» на
 * фейковых маршрутах [com.aniko.app.smoke.fakeInfraModule] (сеть — MockEngine, тексты — EN через
 * [forceEnglishLocale]). Скриншоты кадров пишутся через `saveShot` в `SCREENSHOT_DIR`
 * (свойство `aniko.screenshotDir` или `composeApp/build/reports/catalogToolbar`).
 *
 * Поля ввода ищутся по лейблам (`onNodeWithText("Username")`): в смёрдженном дереве семантики
 * лейбл `OutlinedTextField` — часть узла поля, поэтому `performTextInput` попадает в само поле.
 * Кнопки-«двойники» заголовков («Sign in»/`loginTitle` и «Sign in»/`loginSubmit` совпадают
 * текстом) фильтруются по наличию/отсутствию клика.
 */
@OptIn(ExperimentalTestApi::class)
class AuthFlowSmokeTest {
    /**
     * Стартовый кадр при `initialToken = null`: [AnixTestTags.LOGIN_SCREEN_ROOT], вордмарк «Aniko»,
     * заголовок «Sign in», кнопка «Sign in», ссылка «Sign up», сноска про учётные данные Anixart.
     */
    @Test
    fun loginScreenShowsStartFrame() {
        runAnikoSmokeTest(koinDeclaration = forceEnglishLocale()) {
            onNodeWithTag(AnixTestTags.LOGIN_SCREEN_ROOT).assertIsDisplayed()

            onNodeWithText("Aniko").assertIsDisplayed()
            // Текст совпадает с подписью кнопки — заголовок фильтруем по отсутствию клика.
            onNode(hasText("Sign in") and hasNoClickAction()).assertIsDisplayed()
            onNode(hasText("Sign in") and hasClickAction()).assertIsDisplayed()
            onNodeWithText("Sign up").assertIsDisplayed()
            onNodeWithText("Anixart account credentials are used").assertIsDisplayed()

            saveShot(captureToImage(), "auth_login_en")
        }
    }

    /**
     * Клик по «Sign up» на логине → [AnixTestTags.REGISTER_SCREEN_ROOT] с четырьмя полями.
     * Заодно проверяется клиентская валидация «пароли не совпадают»: при расхождении поля
     * подтверждения показывается `registerPasswordMismatch`, после совпадения — исчезает.
     */
    @Test
    fun registerFormOpensFromLogin() {
        runAnikoSmokeTest(koinDeclaration = forceEnglishLocale()) {
            onNodeWithText("Sign up").performClick()
            onNodeWithTag(AnixTestTags.REGISTER_SCREEN_ROOT).assertIsDisplayed()
            onAllNodesWithTag(AnixTestTags.LOGIN_SCREEN_ROOT).fetchSemanticsNodes().isEmpty()

            onNodeWithText("Username").performTextInput("smoketester")
            onNodeWithText("Email").performTextInput("smoke@example.com")
            onNodeWithText("Password").performTextInput("secret-1")
            onNodeWithText("Confirm password").performTextInput("secret-2")
            onNodeWithText("Passwords do not match").assertIsDisplayed()

            // Приводим подтверждение к совпадению — ошибка валидации обязана исчезнуть.
            onNodeWithText("Confirm password").performTextClearance()
            onNodeWithText("Confirm password").performTextInput("secret-1")
            onAllNodesWithText("Passwords do not match").fetchSemanticsNodes().isEmpty()

            saveShot(captureToImage(), "auth_register_form")
        }
    }

    /**
     * Полный флоу регистрации на фейковых маршрутах: форма → `auth/signUp` → этап кода →
     * НЕВЕРНЫЙ код (`{"code":7}` → текст «Invalid code») → `auth/resend` («The code was sent
     * again») → верный код → `auth/verify` вернул профиль+токен → сессия сохранена → приложение
     * само переключилось на основной граф (HOME_SCREEN_ROOT есть, LOGIN/REGISTER корней нет).
     */
    @Test
    fun registerVerifyResendAndAutoSignIn() {
        var verifyCalls = 0
        val authRoutes: Map<String, () -> String> =
            mapOf(
                "auth/signUp" to { SIGN_UP_RESPONSE },
                "auth/verify" to {
                    verifyCalls += 1
                    if (verifyCalls == 1) VERIFY_INVALID_CODE_RESPONSE else VERIFY_OK_RESPONSE
                },
                "auth/resend" to { RESEND_OK_RESPONSE },
            )

        runAnikoSmokeTest(apiRoutes = authRoutes, koinDeclaration = forceEnglishLocale()) {
            // Форма регистрации.
            onNodeWithText("Sign up").performClick()
            onNodeWithTag(AnixTestTags.REGISTER_SCREEN_ROOT).assertIsDisplayed()
            onNodeWithText("Username").performTextInput("smoketester")
            onNodeWithText("Email").performTextInput("smoke@example.com")
            onNodeWithText("Password").performTextInput("secret-1")
            onNodeWithText("Confirm password").performTextInput("secret-1")

            // signUp → этап кода.
            onNode(hasText("Sign up") and hasClickAction()).performClick()
            awaitText("A confirmation code was sent to smoke@example.com")
            onNodeWithText("A confirmation code was sent to smoke@example.com").assertIsDisplayed()
            saveShot(captureToImage(), "auth_register_code")

            // Неверный код → typed error из i18n, флоу остаётся на этапе кода.
            onNodeWithText("Confirmation code").performTextInput("000000")
            onNode(hasText("Confirm") and hasClickAction()).performClick()
            awaitText("Invalid code")
            onNodeWithText("Invalid code").assertIsDisplayed()

            // Повторная отправка кода → auth/resend → подтверждение в UI.
            onNodeWithText("Resend code").performClick()
            awaitText("The code was sent again")
            onNodeWithText("The code was sent again").assertIsDisplayed()

            // Верный код → verify сохранил сессию → основной граф сам заменил auth-флоу.
            onNodeWithText("Confirmation code").performTextClearance()
            onNodeWithText("Confirmation code").performTextInput("123456")
            onNode(hasText("Confirm") and hasClickAction()).performClick()

            waitUntil(timeoutMillis = AUTH_FLOW_TIMEOUT_MS) {
                onAllNodesWithTag(AnixTestTags.HOME_SCREEN_ROOT).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(AnixTestTags.HOME_SCREEN_ROOT).assertIsDisplayed()
            onAllNodesWithTag(AnixTestTags.LOGIN_SCREEN_ROOT).fetchSemanticsNodes().isEmpty()
            onAllNodesWithTag(AnixTestTags.REGISTER_SCREEN_ROOT).fetchSemanticsNodes().isEmpty()

            saveShot(captureToImage(), "auth_home_after_register")
        }
    }
}

/** Дожидается появления видимого текста — путь «сабмит → сеть → Flow → перекомпозиция» асинхронен. */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.awaitText(
    text: String,
    timeoutMillis: Long = AUTH_FLOW_TIMEOUT_MS,
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
