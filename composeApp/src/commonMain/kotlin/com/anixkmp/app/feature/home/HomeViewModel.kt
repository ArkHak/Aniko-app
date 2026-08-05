package com.anixkmp.app.feature.home

import androidx.lifecycle.ViewModel
import com.anixkmp.data.repository.AuthRepository
import com.anixkmp.network.ApiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    authRepository: AuthRepository,
    apiConfig: ApiConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isAuthorized = authRepository.isAuthorized,
            baseUrl = apiConfig.baseUrl,
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
