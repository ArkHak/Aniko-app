package com.aniko.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * ViewModel экрана настроек.
 *
 * После [AuthRepository.signOut] ничего специально не делает — `sessionState` сам станет
 * [com.aniko.data.session.SessionState.Unauthorized], и App.kt переключит на LoginScreen.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
