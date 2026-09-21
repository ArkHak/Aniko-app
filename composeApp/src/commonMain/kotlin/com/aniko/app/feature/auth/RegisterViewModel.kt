package com.aniko.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.AuthRepository
import com.aniko.model.AnixError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Этап регистрационного флоу: форма → ввод кода из email. */
enum class RegisterStage {
    FORM,
    CODE,
}

data class RegisterUiState(
    val login: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val code: String = "",
    val stage: RegisterStage = RegisterStage.FORM,
    val isLoading: Boolean = false,
    val resendLoading: Boolean = false,
    /** Ошибки этапа формы (`auth/signUp`). */
    val error: RegisterError? = null,
    /** Ошибки этапа кода (`auth/verify`). */
    val verifyError: VerifyError? = null,
    /** «Код отправлен повторно» — одноразовый флаг подтверждения `auth/resend`. */
    val codeResent: Boolean = false,
)

/**
 * Причина ошибки `auth/signUp`, без готового текста — текст живёт в `Strings` (та же конвенция,
 * что и у `LoginError`/`LoginViewModel`: локализация — забота экрана, не ViewModel).
 *
 * Коды `auth/signUp` (см. `docs/api/jadx-out-21/.../network/response/auth/SignUpResponse.java`):
 * SUCCESSFUL=0, INVALID_LOGIN=2, INVALID_EMAIL=3, INVALID_PASSWORD=4, LOGIN_ALREADY_TAKEN=5,
 * EMAIL_ALREADY_TAKEN=6, CODE_ALREADY_SEND=7, CODE_CANNOT_SEND=8, EMAIL_SERVICE_DISALLOWED=9,
 * TOO_MANY_REGISTRATIONS=10.
 */
enum class RegisterError {
    GENERIC,
    INVALID_LOGIN,
    INVALID_EMAIL,
    INVALID_PASSWORD,
    LOGIN_TAKEN,
    EMAIL_TAKEN,
    CODE_ALREADY_SENT,
    CODE_CANNOT_SEND,
    EMAIL_DISALLOWED,
    TOO_MANY,
    NO_CONNECTION,
}

/**
 * Причина ошибки `auth/verify` (ввод кода из email).
 *
 * Коды `auth/verify` (см. `docs/api/jadx-out-21/.../network/response/auth/VerifyResponse.java`):
 * SUCCESSFUL=0, INVALID_LOGIN=2, INVALID_EMAIL=3, INVALID_PASSWORD=4, LOGIN_ALREADY_TAKEN=5,
 * EMAIL_ALREADY_TAKEN=6, CODE_INVALID=7, CODE_EXPIRED=8, INVALID_HASH=9,
 * EMAIL_SERVICE_DISALLOWED=10, TOO_MANY_REGISTRATIONS=11.
 */
enum class VerifyError {
    GENERIC,
    INVALID_CODE,
    CODE_EXPIRED,
    NO_CONNECTION,
}

/**
 * ViewModel регистрационного флоу (форма + подтверждение кода).
 *
 * После успешного [AuthRepository.verifyEmail] ничего специально не делает — verify сохраняет
 * сессию в `SessionStore`, и навигация на основной граф переключается сама в App.kt через
 * `authRepository.sessionState` (то же поведение, что и после `signIn` в [LoginViewModel]).
 */
class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    /** `hash` из `SignUpResponse`, связывает `auth/verify`/`auth/resend` с серверной регистрацией. */
    private var hash: String = ""

    fun onLoginChange(value: String) {
        _uiState.value = _uiState.value.copy(login = value, error = null)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = null)
    }

    fun onCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(code = value, verifyError = null)
    }

    /** `auth/signUp` → при успехе переход на этап ввода кода (код уходит на email). */
    fun submit() {
        val state = _uiState.value
        if (state.isLoading || !state.isFormValid) return

        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val registration = authRepository.signUp(state.login, state.email, state.password)
                hash = registration.hash
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        stage = RegisterStage.CODE,
                        // Пароль на этапе кода больше не показываем, но для verify/resend он
                        // нужен — оставляем в памяти VM до конца флоу (как и логин/email).
                    )
            } catch (e: AnixError) {
                _uiState.value =
                    _uiState.value.copy(isLoading = false, error = e.toRegisterError())
            }
        }
    }

    /** `auth/verify` → при успехе сессия сохранена репозиторием, навигация переключится сама. */
    fun verify() {
        val state = _uiState.value
        if (state.isLoading || state.code.isBlank()) return

        _uiState.value = state.copy(isLoading = true, verifyError = null)
        viewModelScope.launch {
            try {
                authRepository.verifyEmail(state.login, state.email, state.password, hash, state.code)
                _uiState.value = _uiState.value.copy(isLoading = false, password = "", confirmPassword = "")
            } catch (e: AnixError) {
                _uiState.value =
                    _uiState.value.copy(isLoading = false, verifyError = e.toVerifyError())
            }
        }
    }

    /** `auth/resend` — повторная отправка кода на email того же регистрационного флоу. */
    fun resendCode() {
        val state = _uiState.value
        if (state.resendLoading || hash.isBlank()) return

        _uiState.value = state.copy(resendLoading = true, codeResent = false, verifyError = null)
        viewModelScope.launch {
            try {
                authRepository.resendCode(state.login, state.email, state.password, hash)
                _uiState.value = _uiState.value.copy(resendLoading = false, codeResent = true)
            } catch (e: AnixError) {
                // Ошибки resend (2..6 INVALID_*/CODE_CANNOT_SEND) по смыслу близки к проблемам
                // отправки кода — показываем их на этапе кода, не возвращая пользователя на форму.
                _uiState.value =
                    _uiState.value.copy(resendLoading = false, verifyError = e.toResendError())
            }
        }
    }

    private val RegisterUiState.isFormValid: Boolean
        get() = login.isNotBlank() && email.isNotBlank() && password.isNotBlank()
}

// Коды auth/signUp (jadx SignUpResponse.java).
private const val CODE_INVALID_LOGIN = 2
private const val CODE_INVALID_EMAIL = 3
private const val CODE_INVALID_PASSWORD = 4
private const val CODE_LOGIN_TAKEN = 5
private const val CODE_EMAIL_TAKEN = 6
private const val CODE_ALREADY_SEND = 7
private const val CODE_CANNOT_SEND = 8
private const val CODE_EMAIL_DISALLOWED = 9
private const val CODE_TOO_MANY = 10

// Коды auth/verify (jadx VerifyResponse.java).
private const val CODE_VERIFY_INVALID_CODE = 7
private const val CODE_VERIFY_CODE_EXPIRED = 8

private fun AnixError.toRegisterError(): RegisterError =
    when (this) {
        is AnixError.Api ->
            when (apiCode) {
                CODE_INVALID_LOGIN -> RegisterError.INVALID_LOGIN
                CODE_INVALID_EMAIL -> RegisterError.INVALID_EMAIL
                CODE_INVALID_PASSWORD -> RegisterError.INVALID_PASSWORD
                CODE_LOGIN_TAKEN -> RegisterError.LOGIN_TAKEN
                CODE_EMAIL_TAKEN -> RegisterError.EMAIL_TAKEN
                CODE_ALREADY_SEND -> RegisterError.CODE_ALREADY_SENT
                CODE_CANNOT_SEND -> RegisterError.CODE_CANNOT_SEND
                CODE_EMAIL_DISALLOWED -> RegisterError.EMAIL_DISALLOWED
                CODE_TOO_MANY -> RegisterError.TOO_MANY
                else -> RegisterError.GENERIC
            }

        is AnixError.Network -> RegisterError.NO_CONNECTION
        else -> RegisterError.GENERIC
    }

private fun AnixError.toVerifyError(): VerifyError =
    when (this) {
        is AnixError.Api ->
            when (apiCode) {
                CODE_VERIFY_INVALID_CODE -> VerifyError.INVALID_CODE
                CODE_VERIFY_CODE_EXPIRED -> VerifyError.CODE_EXPIRED
                else -> VerifyError.GENERIC
            }

        is AnixError.Network -> VerifyError.NO_CONNECTION
        else -> VerifyError.GENERIC
    }

/** Ошибки `auth/resend` (коды 2..6) показываем на этапе кода: сеть отдельно, остальное — GENERIC. */
private fun AnixError.toResendError(): VerifyError =
    when (this) {
        is AnixError.Network -> VerifyError.NO_CONNECTION
        else -> VerifyError.GENERIC
    }
