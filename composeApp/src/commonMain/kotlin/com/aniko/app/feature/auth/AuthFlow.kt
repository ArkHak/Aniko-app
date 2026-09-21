package com.aniko.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Auth-флоу целиком (вход + регистрация) — точка подключения в `App.kt` при
 * `SessionState.Unauthorized`.
 *
 * Навигация между [LoginScreen] и [RegisterScreen] — локальное состояние флоу (nav-графа в
 * приложении нет, переключение по `sessionState`), поэтому отдельный граф не заводим: регистрация
 * и вход — два состояния одного флоу, и обратно на логин пользователь может вернуться с экрана
 * регистрации без пересоздания сессионного гейта.
 */
@Composable
fun AuthFlow(modifier: Modifier = Modifier) {
    var isRegister by rememberSaveable { mutableStateOf(false) }
    if (isRegister) {
        RegisterScreen(onBackToLogin = { isRegister = false }, modifier = modifier)
    } else {
        LoginScreen(onRegisterClick = { isRegister = true }, modifier = modifier)
    }
}

/**
 * Общий каркас auth-экранов: прозрачный [Surface] (иначе непрозрачный bg-elevated перекрывает
 * фоновую заливку приложения — Login/Register рисуются до `AdaptiveScaffold`), по центру —
 * форма [widthIn] до [AnixThemeTokens.dimens.authFormMaxWidth] с вордмарком [AuthWordmark],
 * внизу — приглушённая сноска [Strings.loginAnixartCredentialsNote].
 *
 * testTag корня (см. `AnixTestTags`) передаётся вызывающим экраном через [modifier].
 */
@Composable
internal fun AuthScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Surface(modifier = modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize().padding(dimens.spaceL)) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .widthIn(max = dimens.authFormMaxWidth)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AuthWordmark()
                content()
            }

            Text(
                text = strings.loginAnixartCredentialsNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Вордмарк «Aniko» — крупная типографика с линейным градиентом брендовых акцентов темы
 * (primary → secondary, тот же приём, что и в палитре иконки приложения).
 */
@Composable
internal fun AuthWordmark() {
    val strings = LocalStrings.current
    // TextStyle с brush — нет в текущей версии CMP (Text(brush=...) не компилируется), поэтому
    // градиент задаётся через SpanStyle внутри AnnotatedString.
    val wordmark =
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                        ),
                ),
            ) {
                append(strings.brand)
            }
        }

    Text(
        text = wordmark,
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
    )
}

/** Текст типизированной ошибки auth-флоу (цвет — WCAG-проверенный `errorText` темы). */
@Composable
internal fun AuthErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = AnixThemeTokens.colors.errorText,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Основная кнопка auth-формы — с индикатором загрузки и тач-таргетом [AnixThemeTokens.dimens.minTouchTarget]. */
@Composable
internal fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.fillMaxWidth().heightIn(min = AnixThemeTokens.dimens.minTouchTarget),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text)
        }
    }
}

/**
 * Поле ввода пароля с show/hide-переключателем — общий паттерн всех форм auth-флоу.
 * Состояние видимости локально (не в UiState), `contentDescription` переключателя — из Strings.
 */
@Composable
internal fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation =
            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (passwordVisible) KeyboardType.Text else KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
            ),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                AnixIcon(
                    name = if (passwordVisible) "visibility_off" else "visibility",
                    contentDescription =
                        if (passwordVisible) strings.loginHidePassword else strings.loginShowPassword,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
