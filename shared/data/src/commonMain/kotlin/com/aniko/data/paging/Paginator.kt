package com.aniko.data.paging

import com.aniko.model.AnixError
import com.aniko.model.Paged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Состояние постраничного списка. */
data class PagingState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: AnixError? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading && !isRefreshing
}

/**
 * Минимальный пагинатор поверх `PageableResponse`.
 *
 * Room/Paging3 в MVP сознательно не используются (см. план), поэтому список
 * держится в памяти, а источник страниц передаётся лямбдой [fetch].
 * Нумерация страниц у Anixart начинается с 0.
 */
class Paginator<T>(
    private val firstPage: Int = 0,
    private val fetch: suspend (page: Int) -> Paged<T>,
) {
    private val mutex = Mutex()
    private var nextPage = firstPage

    private val _state = MutableStateFlow(PagingState<T>())
    val state: StateFlow<PagingState<T>> = _state.asStateFlow()

    /** Загружает следующую страницу. Повторные вызовы во время загрузки игнорируются. */
    suspend fun loadNext() {
        mutex.withLock {
            val current = _state.value
            if (current.isLoading || current.endReached) return
            _state.value = current.copy(isLoading = true, error = null)
        }
        runPageLoad(nextPage, append = true)
    }

    /** Сбрасывает список и загружает первую страницу заново. */
    suspend fun refresh() {
        mutex.withLock {
            if (_state.value.isRefreshing) return
            nextPage = firstPage
            _state.value = _state.value.copy(isRefreshing = true, endReached = false, error = null)
        }
        runPageLoad(firstPage, append = false)
    }

    private suspend fun runPageLoad(
        page: Int,
        append: Boolean,
    ) {
        val result = runCatching { fetch(page) }
        mutex.withLock {
            result
                .onSuccess { paged ->
                    nextPage = paged.currentPage + 1
                    val merged = if (append) _state.value.items + paged.items else paged.items
                    _state.value =
                        PagingState(
                            items = merged,
                            isLoading = false,
                            isRefreshing = false,
                            endReached = !paged.hasNextPage,
                            error = null,
                        )
                }.onFailure { throwable ->
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = throwable as? AnixError ?: AnixError.Unknown(throwable),
                        )
                }
        }
    }
}
