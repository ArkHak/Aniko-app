package com.aniko.app.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.ScheduleRepository
import com.aniko.model.AnixError
import com.aniko.model.Schedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val isLoading: Boolean = false,
    val schedule: Schedule? = null,
    val errorMessage: LoadError? = null,
)

/** См. `LoadError` в `ReleaseDetailsViewModel` — тот же смысл значений, отдельная копия (не
 * шаренный тип), чтобы фича расписания не тянула зависимость на пакет `feature.release`. */
enum class LoadError {
    NO_CONNECTION,
    UNAUTHORIZED,
    GENERIC,
}

/**
 * ViewModel экрана расписания — тонкая обёртка над [ScheduleRepository.observeSchedule] (реактивный
 * cache-first Flow, Фаза 4). Проще `HomeViewModel`: один стейт вместо двух независимых пагинаторов,
 * т.к. расписание — не постраничный листинг, а один снимок из 7 дней сразу.
 *
 * `observeSchedule()` — не вечная подписка (см. её же KDoc и KDoc `ReleaseDetailsViewModel.load`):
 * максимум два эмита (кэш, затем сеть) на вызов, поток сам завершается — поэтому [retry] не может
 * просто "подождать" уже запущенный collect, а перезапускает [load] заново.
 */
class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    // TooGenericExceptionCaught: намеренно — та же схема, что в `ReleaseDetailsViewModel.load`
    // (грандфазерено в её baseline.xml, здесь новый код, поэтому явный @Suppress): любая ошибка
    // сети/API маппится в типизированный `LoadError` для UI, `CancellationException` пробрасывается
    // отдельным catch выше, чтобы не глушить отмену корутины.
    @Suppress("TooGenericExceptionCaught")
    private fun load() {
        _uiState.value = ScheduleUiState(isLoading = true)
        viewModelScope.launch {
            try {
                scheduleRepository.observeSchedule().collect { cached ->
                    _uiState.update { it.copy(isLoading = false, schedule = cached.value, errorMessage = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState(errorMessage = e.toLoadError())
            }
        }
    }
}

private inline fun MutableStateFlow<ScheduleUiState>.update(block: (ScheduleUiState) -> ScheduleUiState) {
    value = block(value)
}

private fun Exception.toLoadError(): LoadError {
    val error = this as? AnixError ?: return LoadError.GENERIC
    return when (error) {
        is AnixError.Network -> LoadError.NO_CONNECTION
        is AnixError.Unauthorized -> LoadError.UNAUTHORIZED
        else -> LoadError.GENERIC
    }
}
