package com.anixkmp.app.feature.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anixkmp.data.repository.EpisodeRepository
import com.anixkmp.data.repository.ReleaseRepository
import com.anixkmp.model.AnixError
import com.anixkmp.model.Episode
import com.anixkmp.model.EpisodeSource
import com.anixkmp.model.Release
import com.anixkmp.model.VoiceType
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
