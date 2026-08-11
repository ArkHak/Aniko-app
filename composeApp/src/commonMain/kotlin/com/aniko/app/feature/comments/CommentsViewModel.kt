package com.aniko.app.feature.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.api.ReleaseCommentApi
import com.aniko.data.mapper.toDomain
import com.aniko.model.AnixError
import com.aniko.model.ReleaseComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommentsUiState(
    val isLoading: Boolean = false,
    val comments: List<ReleaseComment> = emptyList(),
    val errorMessage: LoadError? = null,
)

/** См. `LoadError` в `ReleaseDetailsViewModel` — тот же смысл значений, отдельная копия (не
 * шаренный тип), чтобы фича комментариев не тянула зависимость на пакет `feature.release`. */
enum class LoadError {
    NO_CONNECTION,
    UNAUTHORIZED,
    GENERIC,
}

/**
 * ViewModel экрана комментариев к релизу — минимальная версия для Фазы 5 (P5.T2): только первая
 * страница комментариев (`sort=0`), без пагинации/сортировки/спойлер-блюра. Полноценный экран —
 * Фаза 7 (P7.T12), эта версия лишь встраивает маршрут в pane-систему P5.T3.
 *
 * `releaseId` не идёт в конструктор через Koin — тот же паттерн, что у `ReleaseDetailsViewModel`
 * (см. её KDoc): экран сам вызывает [load] из `LaunchedEffect(releaseId)`.
 */
class CommentsViewModel(
    private val releaseCommentApi: ReleaseCommentApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private var loadedReleaseId: Int? = null

    // TooGenericExceptionCaught: намеренно — та же схема, что в `ReleaseDetailsViewModel.load`
    // (грандфазерено в её baseline.xml, здесь новый код, поэтому явный @Suppress): любая ошибка
    // сети/API маппится в типизированный `LoadError` для UI, `CancellationException` пробрасывается
    // отдельным catch выше, чтобы не глушить отмену корутины.
    @Suppress("TooGenericExceptionCaught")
    fun load(releaseId: Int) {
        val state = _uiState.value
        if (loadedReleaseId == releaseId && state.errorMessage == null && !state.isLoading) return
        loadedReleaseId = releaseId

        _uiState.value = CommentsUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val page =
                    releaseCommentApi.comments(releaseId = releaseId.toLong(), page = FIRST_PAGE, sort = DEFAULT_SORT)
                _uiState.value = CommentsUiState(comments = page.content.map { it.toDomain() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CommentsUiState(errorMessage = e.toLoadError())
            }
        }
    }

    fun retry() {
        loadedReleaseId?.let { load(it) }
    }

    private companion object {
        const val FIRST_PAGE = 0
        const val DEFAULT_SORT = 0
    }
}

private fun Exception.toLoadError(): LoadError {
    val error = this as? AnixError ?: return LoadError.GENERIC
    return when (error) {
        is AnixError.Network -> LoadError.NO_CONNECTION
        is AnixError.Unauthorized -> LoadError.UNAUTHORIZED
        else -> LoadError.GENERIC
    }
}
