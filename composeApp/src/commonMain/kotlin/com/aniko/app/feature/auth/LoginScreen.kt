package com.aniko.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Surface(modifier = modifier.fillMaxSize().testTag(AnixTestTags.LOGIN_SCREEN_ROOT)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(dimens.spaceL),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.loginTitle,
                style = MaterialTheme.typography.headlineMedium,
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

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(strings.loginPasswordLabel) },
                singleLine = true,
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::submit,
                enabled = !state.isLoading && state.login.isNotBlank() && state.password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(strings.loginSubmit)
                }
            }

            val error = state.error
            if (error != null) {
                Text(
                    text = error.toMessage(strings),
                    color = AnixThemeTokens.colors.errorText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
