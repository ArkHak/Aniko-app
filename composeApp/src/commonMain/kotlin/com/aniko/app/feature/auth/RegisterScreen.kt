package com.aniko.app.feature.auth

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран регистрации — часть auth-флоу (см. [AuthFlow]).
 *
 * Два этапа ([RegisterStage]): форма (логин/email/пароль/подтверждение → `auth/signUp`, код
 * уходит на email) и ввод кода (`auth/verify` → `auth/resend`). После успешного verify сессия
 * сохранена репозиторием и навигация на основной граф переключается сама в App.kt через
 * `authRepository.sessionState` — отдельный вход после регистрации не нужен.
 */
@Composable
fun RegisterScreen(
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(modifier = modifier.testTag(AnixTestTags.REGISTER_SCREEN_ROOT)) {
        Text(
            text = LocalStrings.current.registerTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        when (state.stage) {
            RegisterStage.FORM -> RegisterForm(state, viewModel, onBackToLogin)
            RegisterStage.CODE -> RegisterCode(state, viewModel)
        }
    }
}

@Composable
private fun ColumnScope.RegisterForm(
    state: RegisterUiState,
    viewModel: RegisterViewModel,
    onBackToLogin: () -> Unit,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    OutlinedTextField(
        value = state.login,
        onValueChange = viewModel::onLoginChange,
        label = { Text(strings.loginLoginLabel) },
        singleLine = true,
        enabled = !state.isLoading,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text(strings.registerEmailLabel) },
        singleLine = true,
        enabled = !state.isLoading,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Email,
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

    AuthPasswordField(
        value = state.confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChange,
        label = strings.registerConfirmPasswordLabel,
        enabled = !state.isLoading,
    )

    if (state.confirmPassword.isNotEmpty() && state.confirmPassword != state.password) {
        AuthErrorText(text = strings.registerPasswordMismatch)
    }

    AuthPrimaryButton(
        text = strings.registerSubmit,
        onClick = viewModel::submit,
        isLoading = state.isLoading,
        enabled = state.login.isNotBlank() && state.email.isNotBlank() && state.password.isNotBlank(),
    )

    TextButton(
        onClick = onBackToLogin,
        enabled = !state.isLoading,
        modifier = Modifier.heightIn(min = dimens.minTouchTarget),
    ) {
        Text(strings.registerBackToLogin)
    }

    val error = state.error
    if (error != null) {
        AuthErrorText(text = error.toMessage(strings))
    }
}

@Composable
private fun ColumnScope.RegisterCode(
    state: RegisterUiState,
    viewModel: RegisterViewModel,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    Text(
        text = strings.registerCodeSentFormat(state.email),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        label = { Text(strings.registerCodeLabel) },
        singleLine = true,
        enabled = !state.isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    AuthPrimaryButton(
        text = strings.registerVerifySubmit,
        onClick = viewModel::verify,
        isLoading = state.isLoading,
        enabled = state.code.isNotBlank(),
    )

    TextButton(
        onClick = viewModel::resendCode,
        enabled = !state.isLoading && !state.resendLoading,
        modifier = Modifier.heightIn(min = dimens.minTouchTarget),
    ) {
        if (state.resendLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(strings.registerResendCode)
        }
    }

    if (state.codeResent) {
        Text(
            text = strings.registerCodeResent,
            style = MaterialTheme.typography.bodySmall,
            color = AnixThemeTokens.colors.successText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val verifyError = state.verifyError
    if (verifyError != null) {
        AuthErrorText(text = verifyError.toMessage(strings))
    }
}

private fun RegisterError.toMessage(strings: Strings): String =
    when (this) {
        RegisterError.GENERIC -> strings.registerGenericError
        RegisterError.INVALID_LOGIN -> strings.registerInvalidLogin
        RegisterError.INVALID_EMAIL -> strings.registerInvalidEmail
        RegisterError.INVALID_PASSWORD -> strings.registerInvalidPassword
        RegisterError.LOGIN_TAKEN -> strings.registerLoginTaken
        RegisterError.EMAIL_TAKEN -> strings.registerEmailTaken
        RegisterError.CODE_ALREADY_SENT -> strings.registerCodeAlreadySent
        RegisterError.CODE_CANNOT_SEND -> strings.registerCodeCannotSend
        RegisterError.EMAIL_DISALLOWED -> strings.registerEmailDisallowed
        RegisterError.TOO_MANY -> strings.registerTooManyRegistrations
        RegisterError.NO_CONNECTION -> strings.commonErrorNoConnection
    }

private fun VerifyError.toMessage(strings: Strings): String =
    when (this) {
        VerifyError.GENERIC -> strings.registerGenericError
        VerifyError.INVALID_CODE -> strings.registerInvalidCode
        VerifyError.CODE_EXPIRED -> strings.registerCodeExpired
        VerifyError.NO_CONNECTION -> strings.commonErrorNoConnection
    }
