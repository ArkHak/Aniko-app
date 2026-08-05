package com.anixkmp.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anixkmp.data.repository.AuthRepository
import com.anixkmp.model.AnixError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * ViewModel экрана входа.
 *
 * После успешного [AuthRepository.signIn] ничего специально не делает — навигация на основной
 * граф переключается сама в App.kt через `authRepository.sessionState`, которое обновляет
 * [AuthRepository.signIn] (через `SessionStore.save`).
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onLoginChange(value: String) {
        _uiState.value = _uiState.value.copy(login = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading || state.login.isBlank() || state.password.isBlank()) return

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                authRepository.signIn(state.login, state.password)
                // Пароль в state больше не нужен ни при успехе (навигация переключится сама
                // через sessionState в App.kt), ни при ошибке — не держим его в памяти UI-state
                // дольше необходимого.
                _uiState.value = _uiState.value.copy(isLoading = false, password = "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    password = "",
                    errorMessage = e.toLoginErrorMessage(),
                )
            }
        }
    }
}

/**
 * Маппинг ошибок `auth/signIn` на понятные сообщения.
 *
 * Коды `auth/signIn` (см. `docs/api/jadx-out/.../network/response/auth/SignInResponse.java`
 * и базовый `network/Response.java`): SUCCESSFUL=0, FAILED=1, INVALID_LOGIN=2,
 * INVALID_PASSWORD=3, BANNED=402, PERM_BANNED=403.
 */
private fun Exception.toLoginErrorMessage(): String {
    val error = this as? AnixError ?: return "Не удалось войти, попробуйте снова"
    return when (error) {
        is AnixError.Api -> when (error.apiCode) {
            CODE_INVALID_LOGIN -> "Неверный логин"
            CODE_INVALID_PASSWORD -> "Неверный пароль"
            CODE_BANNED, CODE_PERM_BANNED -> "Аккаунт заблокирован"
            else -> "Не удалось войти, попробуйте снова"
        }

        is AnixError.Network -> "Нет соединения с сервером"
        else -> "Не удалось войти, попробуйте снова"
    }
}

private const val CODE_INVALID_LOGIN = 2
private const val CODE_INVALID_PASSWORD = 3
private const val CODE_BANNED = 402
private const val CODE_PERM_BANNED = 403
