package com.aniko.app.feature.auth

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран входа — часть auth-флоу (см. [AuthFlow]).
 *
 * Редизайн (2026-09-21): вордмарк «Aniko» над формой (см. [AuthWordmark]), под ним короткий
 * заголовок [Strings.loginTitle] («Вход»), поля логина/пароля, основная кнопка «Войти», под ней
 * текстовая кнопка «Регистрация» ([Strings.loginRegisterAction]) и приглушённая сноска внизу
 * экрана. После успешного входа навигация никуда не ведёт — основной граф переключается сам
 * в App.kt через `authRepository.sessionState`.
 */
@Composable
fun LoginScreen(
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(modifier = modifier.testTag(AnixTestTags.LOGIN_SCREEN_ROOT)) {
        LoginForm(state, viewModel, onRegisterClick)
    }
}

@Composable
private fun ColumnScope.LoginForm(
    state: LoginUiState,
    viewModel: LoginViewModel,
    onRegisterClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    Text(
        text = strings.loginTitle,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    OutlinedTextField(
        value = state.login,
        onValueChange = viewModel::onLoginChange,
        label = { Text(strings.loginLoginLabel) },
        singleLine = true,
        enabled = !state.isLoading,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
            ),
        modifier = Modifier.fillMaxWidth(),
    )

    AuthPasswordField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = strings.loginPasswordLabel,
        enabled = !state.isLoading,
    )

    AuthPrimaryButton(
        text = strings.loginSubmit,
        onClick = viewModel::submit,
        isLoading = state.isLoading,
        enabled = state.login.isNotBlank() && state.password.isNotBlank(),
    )

    TextButton(
        onClick = onRegisterClick,
        enabled = !state.isLoading,
        modifier = Modifier.heightIn(min = dimens.minTouchTarget),
    ) {
        Text(strings.loginRegisterAction)
    }

    val error = state.error
    if (error != null) {
        AuthErrorText(text = error.toMessage(strings))
    }
}

private fun LoginError.toMessage(strings: Strings): String =
    when (this) {
        LoginError.GENERIC -> strings.loginGenericError
        LoginError.INVALID_LOGIN -> strings.loginInvalidLogin
        LoginError.INVALID_PASSWORD -> strings.loginInvalidPassword
        LoginError.ACCOUNT_BANNED -> strings.commonAccountBanned
        LoginError.NO_CONNECTION -> strings.commonErrorNoConnection
    }
