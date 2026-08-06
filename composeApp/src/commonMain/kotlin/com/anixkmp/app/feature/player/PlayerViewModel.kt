package com.anixkmp.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anixkmp.data.repository.EpisodeRepository
import com.anixkmp.model.AnixError
import com.anixkmp.model.VideoHost
import com.anixkmp.player.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val source: PlaybackSource? = null,
    val errorMessage: String? = null,
)

/**
 * ViewModel экрана плеера.
 *
 * Резолвит [PlaybackSource] через `EpisodeRepository.resolvePlaybackSource` и после успешного
 * резолва отмечает серию просмотренной — эвристика MVP «начал смотреть = просмотрено», без
 * отслеживания реальной позиции воспроизведения (осознанное упрощение фазы 5, `docs/plan`).
 *
 * `releaseId`/`sourceId`/`position` приходят из `AnixDestination.Player` через `toRoute()` в
 * `App.kt`, аналогично `ReleaseDetailsViewModel.load(releaseId)` — не через Koin `parametersOf`.
 */
class PlayerViewModel(
    private val episodeRepository: EpisodeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private data class LoadKey(val releaseId: Int, val sourceId: Int, val position: Int, val host: VideoHost)

    private var loadedKey: LoadKey? = null

    fun load(releaseId: Int, sourceId: Int, position: Int, host: VideoHost) {
        val key = LoadKey(releaseId, sourceId, position, host)
        // Не повторяем загрузку, если тот же эпизод уже грузится или уже успешно загружен.
        // Ошибочное состояние (errorMessage != null) НЕ блокирует повтор — иначе повторный тап
        // по той же серии после сбоя молча ничего не делал бы, и единственным способом
        // повторить попытку оставалась бы кнопка "Повторить" на AnixErrorBox.
        if (loadedKey == key && (_uiState.value.isLoading || _uiState.value.source != null)) return
        loadedKey = key

        _uiState.value = PlayerUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val source = episodeRepository.resolvePlaybackSource(releaseId, sourceId, position, host)
                _uiState.value = PlayerUiState(isLoading = false, source = source)
                // Эвристика MVP: успешный резолв ссылки = серия просмотрена. Ошибку отметки
                // просмотра намеренно проглатываем — пользователю плеер важнее счётчика.
                runCatching { episodeRepository.markWatched(releaseId, sourceId, position) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PlayerUiState(isLoading = false, errorMessage = e.toPlayerErrorMessage())
            }
        }
    }

    fun retry() {
        loadedKey?.let { (releaseId, sourceId, position, host) -> load(releaseId, sourceId, position, host) }
    }
}

private fun Exception.toPlayerErrorMessage(): String {
    val error = this as? AnixError ?: return "Не удалось загрузить видео"
    return when (error) {
        is AnixError.Network -> "Нет соединения с сервером"
        is AnixError.Unauthorized -> "Требуется вход в аккаунт"
        is AnixError.PlaybackResolve -> "Не удалось получить видео с источника ${error.host.key}"
        else -> "Не удалось загрузить видео"
    }
}
