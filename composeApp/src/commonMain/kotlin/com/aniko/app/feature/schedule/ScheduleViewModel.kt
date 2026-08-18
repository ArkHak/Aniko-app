package com.aniko.app.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.ScheduleRepository
import com.aniko.model.AnixError
import com.aniko.model.Schedule
import com.aniko.model.WeekDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class ScheduleUiState(
    val isLoading: Boolean = false,
    val schedule: Schedule? = null,
    val errorMessage: LoadError? = null,
    // P9.T5: день, чей контент сейчас показан (навигация по дням) и день "сегодня" для подсветки
    // в селекторе — оба инициализируются в конструкторе ViewModel одним и тем же значением
    // (см. [ScheduleViewModel]) и переживают перезагрузку/retry расписания (см. [load]).
    val selectedDay: WeekDay = WeekDay.MONDAY,
    val today: WeekDay = WeekDay.MONDAY,
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
    private val clock: Clock,
) : ViewModel() {
    // Считается один раз при создании ViewModel, не пересчитывается на каждый retry/эмит —
    // ровно то же приближение, что уже принято в `HomeViewModel.todayWeekDay()` (P7.T1): около
    // полуночи по местному времени возможен короткий сдвиг на день, не блокер для расписания
    // без точного времени выхода серии в самом API (см. KDoc `Schedule`).
    private val today = clock.todayWeekDay()

    private val _uiState = MutableStateFlow(ScheduleUiState(selectedDay = today, today = today))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    /** P9.T5: навигация по дням — переключает, чей контент показывает [ScheduleUiState.selectedDay]. */
    fun selectDay(day: WeekDay) {
        _uiState.update { it.copy(selectedDay = day) }
    }

    // TooGenericExceptionCaught: намеренно — та же схема, что в `ReleaseDetailsViewModel.load`
    // (грандфазерено в её baseline.xml, здесь новый код, поэтому явный @Suppress): любая ошибка
    // сети/API маппится в типизированный `LoadError` для UI, `CancellationException` пробрасывается
    // отдельным catch выше, чтобы не глушить отмену корутины.
    @Suppress("TooGenericExceptionCaught")
    private fun load() {
        // `.copy` вместо замены всего стейта на свежий `ScheduleUiState(...)` (как было до P9.T5) —
        // иначе retry() сбрасывал бы выбранный пользователем день обратно на "сегодня".
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                scheduleRepository.observeSchedule().collect { cached ->
                    _uiState.update { it.copy(isLoading = false, schedule = cached.value, errorMessage = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, schedule = null, errorMessage = e.toLoadError()) }
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

/**
 * День недели "сегодня" — намеренный дубликат `HomeViewModel.todayWeekDay()` (P7.T1, тот файл вне
 * зоны этой задачи), не общая функция: `WeekDay` не зависит от `kotlinx-datetime` (см. её же KDoc),
 * поэтому день считается вручную по эпохе UTC — 1970-01-01 (день эпохи 0) был четвергом → номер 3
 * при `MONDAY = 0 .. SUNDAY = 6`. Та же схема дублирования, что уже принята в этом файле для
 * `LoadError` (см. его KDoc) — фича расписания сознательно не тянет зависимость на `feature.home`
 * ради одной короткой функции.
 */
private fun Clock.todayWeekDay(): WeekDay {
    val epochDay = now().epochSeconds.floorDiv(SECONDS_PER_DAY)
    val ordinal = ((epochDay + EPOCH_DAY_ZERO_WEEKDAY_ORDINAL) % DAYS_IN_WEEK + DAYS_IN_WEEK) % DAYS_IN_WEEK
    return WeekDay.entries[ordinal.toInt()]
}

private const val SECONDS_PER_DAY = 86_400L
private const val DAYS_IN_WEEK = 7L
private const val EPOCH_DAY_ZERO_WEEKDAY_ORDINAL = 3L
