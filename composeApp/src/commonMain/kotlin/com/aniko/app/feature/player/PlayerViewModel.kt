package com.aniko.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.model.AnixError
import com.aniko.model.VideoHost
import com.aniko.player.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val source: PlaybackSource? = null,
    val error: PlayerError? = null,
)

/**
 * Причина ошибки резолва плеера, без готового текста — текст живёт в `Strings` (Фаза 2 плана,
 * P2.T9: ViewModel не знает про `LocalStrings`/Compose, локализация — забота [PlayerScreen]).
 * [SourceUnavailable] назван не `PlaybackSource`, чтобы не конфликтовать с
 * [com.aniko.player.PlaybackSource], уже импортированным в этом файле.
 */
sealed interface PlayerError {
    data object NoConnection : PlayerError

    data object Unauthorized : PlayerError

    data class SourceUnavailable(
        val hostKey: String,
    ) : PlayerError

    data object Generic : PlayerError
}

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
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private data class LoadKey(
        val releaseId: Int,
        val sourceId: Int,
        val position: Int,
        val host: VideoHost,
    )

    private var loadedKey: LoadKey? = null

    fun load(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        host: VideoHost,
    ) {
        val key = LoadKey(releaseId, sourceId, position, host)
        // Не повторяем загрузку, если тот же эпизод уже грузится или уже успешно загружен.
        // Ошибочное состояние (error != null) НЕ блокирует повтор — иначе повторный тап
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
                // Та же логика для истории просмотра ("Фаза 6"): плееру не нужно знать об успехе
                // синхронизации истории, ошибку тоже проглатываем, а не мешаем воспроизведению.
                runCatching { libraryRepository.addHistory(releaseId, sourceId, position) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PlayerUiState(isLoading = false, error = e.toPlayerError())
            }
        }
    }

    fun retry() {
        loadedKey?.let { (releaseId, sourceId, position, host) -> load(releaseId, sourceId, position, host) }
    }
}

private fun Exception.toPlayerError(): PlayerError {
    val error = this as? AnixError ?: return PlayerError.Generic
    return when (error) {
        is AnixError.Network -> PlayerError.NoConnection
        is AnixError.Unauthorized -> PlayerError.Unauthorized
        is AnixError.PlaybackResolve -> PlayerError.SourceUnavailable(error.host.key)
        else -> PlayerError.Generic
    }
}
