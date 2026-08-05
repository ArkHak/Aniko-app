package com.anixkmp.app.feature.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anixkmp.data.repository.ReleaseRepository
import com.anixkmp.model.AnixError
import com.anixkmp.model.Release
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReleaseDetailsUiState(
    val isLoading: Boolean = false,
    val release: Release? = null,
    val errorMessage: String? = null,
)

/**
 * ViewModel карточки релиза.
 *
 * `releaseId` не передаётся в конструктор через Koin (`parametersOf` только усложнило бы DI
 * ради параметра, который и так приходит из type-safe навигации) — вместо этого экран сам
 * вызывает [load] из `LaunchedEffect(releaseId)`. [load] идемпотентен для одного и того же id,
 * пока не было ошибки — повторная композиция/пересоздание того же route не долбит сеть.
 */
class ReleaseDetailsViewModel(
    private val releaseRepository: ReleaseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReleaseDetailsUiState())
    val uiState: StateFlow<ReleaseDetailsUiState> = _uiState.asStateFlow()

    private var loadedReleaseId: Int? = null

    fun load(releaseId: Int) {
        val state = _uiState.value
        if (loadedReleaseId == releaseId && state.release != null && state.errorMessage == null) return
        loadedReleaseId = releaseId

        _uiState.value = ReleaseDetailsUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val release = releaseRepository.release(releaseId)
                _uiState.value = ReleaseDetailsUiState(release = release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ReleaseDetailsUiState(errorMessage = e.toReleaseErrorMessage())
            }
        }
    }

    fun retry() {
        loadedReleaseId?.let { load(it) }
    }
}

private fun Exception.toReleaseErrorMessage(): String {
    val error = this as? AnixError ?: return "Не удалось загрузить релиз"
    return when (error) {
        is AnixError.Network -> "Нет соединения с сервером"
        is AnixError.Unauthorized -> "Требуется вход в аккаунт"
        else -> "Не удалось загрузить релиз"
    }
}
