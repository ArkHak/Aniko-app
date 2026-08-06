package com.aniko.app.feature.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.repository.ReleaseRepository
import com.aniko.model.AnixError
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.VoiceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReleaseDetailsUiState(
    val isLoading: Boolean = false,
    val release: Release? = null,
    val errorMessage: String? = null,

    // Флоу выбора серии: типы озвучки → источники → серии (см. `docs/api/ENDPOINTS.md`).
    val voiceTypes: List<VoiceType> = emptyList(),
    val selectedTypeId: Int? = null,
    val sources: List<EpisodeSource> = emptyList(),
    val selectedSourceId: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val isEpisodesStepLoading: Boolean = false,
    val episodesStepError: String? = null,
)

/**
 * ViewModel карточки релиза.
 *
 * `releaseId` не передаётся в конструктор через Koin (`parametersOf` только усложнило бы DI
 * ради параметра, который и так приходит из type-safe навигации) — вместо этого экран сам
 * вызывает [load] из `LaunchedEffect(releaseId)`. [load] идемпотентен для одного и того же id,
 * пока не было ошибки — повторная композиция/пересоздание того же route не долбит сеть.
 *
 * Флоу выбора серии (типы → источники → серии) — простой линейный стейт-машина без пагинации:
 * выбор типа сбрасывает источники и серии, выбор источника сбрасывает серии. Ошибки на этом
 * флоу не путаются с ошибкой загрузки самого релиза — у них отдельное поле
 * [ReleaseDetailsUiState.episodesStepError], чтобы неудачный запрос источников не перекрывал
 * уже отрисованную карточку релиза.
 */
class ReleaseDetailsViewModel(
    private val releaseRepository: ReleaseRepository,
    private val episodeRepository: EpisodeRepository,
    private val libraryRepository: LibraryRepository,
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
                return@launch
            }
            loadVoiceTypes(releaseId)
        }
    }

    fun retry() {
        loadedReleaseId?.let { load(it) }
    }

    private fun loadVoiceTypes(releaseId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isEpisodesStepLoading = true, episodesStepError = null) }
            try {
                val types = episodeRepository.voiceTypes(releaseId)
                _uiState.update { it.copy(voiceTypes = types, isEpisodesStepLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isEpisodesStepLoading = false, episodesStepError = e.toEpisodesErrorMessage())
                }
            }
        }
    }

    fun selectVoiceType(typeId: Int) {
        val releaseId = loadedReleaseId ?: return
        if (_uiState.value.selectedTypeId == typeId) return
        _uiState.update {
            it.copy(
                selectedTypeId = typeId,
                sources = emptyList(),
                selectedSourceId = null,
                episodes = emptyList(),
                isEpisodesStepLoading = true,
                episodesStepError = null,
            )
        }
        viewModelScope.launch {
            try {
                val sources = episodeRepository.sources(releaseId, typeId)
                _uiState.update { it.copy(sources = sources, isEpisodesStepLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isEpisodesStepLoading = false, episodesStepError = e.toEpisodesErrorMessage())
                }
            }
        }
    }

    fun selectSource(sourceId: Int) {
        val releaseId = loadedReleaseId ?: return
        val typeId = _uiState.value.selectedTypeId ?: return
        if (_uiState.value.selectedSourceId == sourceId) return
        _uiState.update {
            it.copy(
                selectedSourceId = sourceId,
                episodes = emptyList(),
                isEpisodesStepLoading = true,
                episodesStepError = null,
            )
        }
        viewModelScope.launch {
            try {
                val episodes = episodeRepository.episodes(releaseId, typeId, sourceId)
                _uiState.update { it.copy(episodes = episodes, isEpisodesStepLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isEpisodesStepLoading = false, episodesStepError = e.toEpisodesErrorMessage())
                }
            }
        }
    }

    /**
     * Меняет статус релиза в списке пользователя ([status] `null` — снять статус). Обновляет
     * `state.release` оптимистично и, в отличие от `PlayerViewModel`'а с его молчаливым
     * `runCatching { episodeRepository.markWatched(...) }`, откатывает локальное состояние при
     * ошибке сети — здесь это видимый переключатель в UI, и разъехавшийся с сервером статус
     * пользователь заметит.
     */
    fun changeListStatus(status: ListStatus?) {
        val release = _uiState.value.release ?: return
        val previousStatus = release.myListStatus
        if (previousStatus == status) return

        _uiState.update { it.copy(release = it.release?.copy(myListStatus = status)) }
        viewModelScope.launch {
            runCatching {
                if (status != null) {
                    libraryRepository.addToList(status, release.id)
                } else {
                    previousStatus?.let { libraryRepository.removeFromList(it, release.id) }
                }
            }.onFailure {
                _uiState.update { it.copy(release = it.release?.copy(myListStatus = previousStatus)) }
            }
        }
    }

    /** Тоггл избранного — та же схема оптимистичного обновления с откатом при ошибке, см. [changeListStatus]. */
    fun toggleFavorite() {
        val release = _uiState.value.release ?: return
        val previousFavorite = release.isFavorite
        val nextFavorite = !previousFavorite

        _uiState.update { it.copy(release = it.release?.copy(isFavorite = nextFavorite)) }
        viewModelScope.launch {
            runCatching {
                if (nextFavorite) libraryRepository.addFavorite(release.id) else libraryRepository.removeFavorite(release.id)
            }.onFailure {
                _uiState.update { it.copy(release = it.release?.copy(isFavorite = previousFavorite)) }
            }
        }
    }
}

private inline fun MutableStateFlow<ReleaseDetailsUiState>.update(
    block: (ReleaseDetailsUiState) -> ReleaseDetailsUiState,
) {
    value = block(value)
}

private fun Exception.toReleaseErrorMessage(): String {
    val error = this as? AnixError ?: return "Не удалось загрузить релиз"
    return when (error) {
        is AnixError.Network -> "Нет соединения с сервером"
        is AnixError.Unauthorized -> "Требуется вход в аккаунт"
        else -> "Не удалось загрузить релиз"
    }
}

private fun Exception.toEpisodesErrorMessage(): String {
    val error = this as? AnixError ?: return "Не удалось загрузить серии"
    return when (error) {
        is AnixError.Network -> "Нет соединения с сервером"
        is AnixError.Unauthorized -> "Требуется вход в аккаунт"
        else -> "Не удалось загрузить серии"
    }
}
