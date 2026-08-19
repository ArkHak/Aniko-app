package com.aniko.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.NotificationPreferenceRepository
import com.aniko.model.AnixError
import com.aniko.model.NotificationPreferenceToggle
import com.aniko.model.NotificationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val preferences: NotificationPreferences? = null,
    val isLoading: Boolean = true,
    val error: AnixError? = null,
)

/**
 * ViewModel секции уведомлений на экране настроек (P10.T6).
 *
 * Отдельный ViewModel, а не расширение [SettingsViewModel]: у того по-прежнему нет собственного
 * состояния (см. его KDoc и решение «не мигрировать на MVI-контракт»), и добавление сюда сетевой
 * загрузки со стейтом обесценило бы это решение. Секция настроек уведомлений — единственная часть
 * экрана, которая ходит в сеть.
 *
 * Мутации **не** оптимистичные, в отличие от `ProfileViewModel`: серверные эндпоинты
 * инвертирующие (`GET .../edit` без тела, сервер сам меняет флаг на противоположный), поэтому
 * достоверное новое значение существует только в ответе последующего
 * `profile/preference/notification/my`. Нарисовать предполагаемое `!value` и откатить при ошибке
 * можно было бы, но при параллельной правке из официального клиента такой «оптимизм» показал бы
 * заведомо неверное состояние. Вместо этого тумблер блокируется на время запроса
 * ([NotificationSettingsUiState.isLoading]).
 */
class NotificationSettingsViewModel(
    private val repository: NotificationPreferenceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatchingAnix { repository.preferences() }
        }
    }

    fun toggle(toggle: NotificationPreferenceToggle) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatchingAnix { repository.toggle(toggle) }
        }
    }

    /**
     * [AnixError] — единственный тип ошибки, который может прилететь из репозитория: маппинг всего
     * остального делает `apiCall` в `shared/data` (см. его KDoc). Ловим именно его, а не
     * `Throwable`, чтобы не проглотить `CancellationException` вместе с ним.
     */
    private suspend fun runCatchingAnix(block: suspend () -> NotificationPreferences) {
        _uiState.value =
            try {
                _uiState.value.copy(preferences = block(), isLoading = false, error = null)
            } catch (error: AnixError) {
                _uiState.value.copy(isLoading = false, error = error)
            }
    }
}
