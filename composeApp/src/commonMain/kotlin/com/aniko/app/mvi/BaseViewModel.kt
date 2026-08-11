package com.aniko.app.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * База MVI-контракта (Фаза 5, P5.T7) — обновление паттерна, НЕ полная миграция всех ViewModel:
 * см. критерий "когда мигрировать" в KDoc [dispatch] и в журнале Фазы 5 плана.
 *
 * Интенты обрабатываются КОНКУРЕНТНО (отдельный `launch` на каждый вызов [dispatch]), не через
 * сериализующий actor — типичный интент здесь независимая сетевая подгрузка одной из нескольких
 * секций экрана (см. `HomeViewModel`, P5.T8), и сериализация блокировала бы одну секцию, пока
 * грузится другая. Если конкретному экрану нужна строгая последовательность интентов — это его
 * забота (например, `Mutex` внутри `handleIntent`), не контракт база-класса.
 */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * `Channel(BUFFERED)` + `receiveAsFlow()`, а не `SharedFlow(replay = 0)`: `SharedFlow` без
     * replay ТЕРЯЕТ эмит, если в момент отправки нет активного коллектора (экран в фоне,
     * пересоздание композиции) — `Channel` буферизует и доставит ровно один раз ровно одному
     * коллектору, что и нужно одноразовым UI-событиям.
     */
    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    /**
     * Единственная публичная точка входа UI в ViewModel. Непойманное исключение из
     * [handleIntent] (найдено ревью S3/Фазы 5 — раньше уронило бы весь [viewModelScope] молча)
     * не должно ронять всю ViewModel из-за бага в обработке одного интента — конкретные
     * ожидаемые ошибки [handleIntent] обязан ловить сам (см. `HomeViewModel` — `Paginator`
     * уже не бросает, ошибка оседает в `PagingState.error`), этот перехват — последний рубеж на
     * непредвиденный случай, а не основной путь обработки ошибок.
     *
     * `@Suppress("TooGenericExceptionCaught")`: база не может знать, какие конкретные исключения
     * бросит `handleIntent` произвольного наследника — ловим общий `Exception` намеренно.
     */
    @Suppress("TooGenericExceptionCaught")
    fun dispatch(intent: I) {
        viewModelScope.launch {
            try {
                handleIntent(intent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onUnhandledIntentError(intent, e)
            }
        }
    }

    protected abstract suspend fun handleIntent(intent: I)

    /** Хук на непойманное исключение из [handleIntent], см. KDoc [dispatch]. Дефолт — no-op. */
    protected open suspend fun onUnhandledIntentError(
        intent: I,
        error: Exception,
    ) {
    }

    protected fun updateState(reducer: S.() -> S) {
        _state.update(reducer)
    }

    protected suspend fun emitEffect(effect: E) {
        _effects.send(effect)
    }
}
