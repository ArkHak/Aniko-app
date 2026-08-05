package com.anixkmp.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anixkmp.data.repository.AuthRepository
import com.anixkmp.data.session.SessionState
import com.anixkmp.network.ApiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isAuthorized: Boolean = false,
    val baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
)

/**
 * ViewModel стартового экрана.
 *
 * На этапе скелета она только показывает, что DI-граф и `androidx.lifecycle`
 * KMP-ViewModel работают на всех трёх таргетах. Загрузку «продолжить смотреть»
 * подключаем в следующей фазе через `ReleaseRepository.watchingPaginator()`.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    apiConfig: ApiConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(baseUrl = apiConfig.baseUrl))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Владелец bootstrap() — корневой уровень (App.kt), он же гейтит навигацию по
        // sessionState. HomeViewModel этот экран видит только когда сессия уже Authorized
        // (иначе App.kt его не покажет), поэтому повторный bootstrap() здесь не нужен —
        // достаточно просто слушать sessionState.
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                _uiState.value = _uiState.value.copy(isAuthorized = state is SessionState.Authorized)
            }
        }
    }
}
