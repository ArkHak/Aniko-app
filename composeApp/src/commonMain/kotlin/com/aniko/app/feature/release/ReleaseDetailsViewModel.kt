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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReleaseDetailsUiState(
    val isLoading: Boolean = false,
    val release: Release? = null,
    val errorMessage: LoadError? = null,
    // Флоу выбора серии: типы озвучки → источники → серии (см. `docs/api/ENDPOINTS.md`).
    val voiceTypes: List<VoiceType> = emptyList(),
    val selectedTypeId: Int? = null,
    val sources: List<EpisodeSource> = emptyList(),
    val selectedSourceId: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val isEpisodesStepLoading: Boolean = false,
    val episodesStepError: LoadError? = null,
)

/**
 * Причина ошибки загрузки релиза/серий, без готового текста — текст живёт в `Strings`
 * (Фаза 2 плана, P2.T9: ViewModel не знает про `LocalStrings`/Compose). Одно и то же значение
 * [GENERIC] размечает и «не удалось загрузить релиз», и «не удалось загрузить серии» — какой
 * именно текст показать, решает [ReleaseDetailsScreen] по тому, в какое поле стейта попала
 * ошибка ([ReleaseDetailsUiState.errorMessage] vs [ReleaseDetailsUiState.episodesStepError]).
 */
enum class LoadError {
    NO_CONNECTION,
    UNAUTHORIZED,
    GENERIC,
}

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

    /**
     * Реактивная загрузка через `ReleaseRepository.observeRelease` (P4.T7, S3) — БД остаётся SSOT,
     * поэтому оптимистичные локальные записи `changeListStatus`/`toggleFavorite` доходят до UI без
     * ручного патчинга стейта.
     *
     * `cacheFirstFlow` (см. `CacheFirst.kt`, не трогать) — не вечная подписка, а один
     * (max два: кэш + сеть) эмит на вызов, после чего сам поток завершается — поэтому именно
     * здесь, после успешного `collect`, как и раньше, запускается [loadVoiceTypes]: точка «релиз
     * успешно получен» не изменилась, изменился только источник (кэш вместо прямого сетевого вызова).
     */
    fun load(releaseId: Int) {
        val state = _uiState.value
        if (loadedReleaseId == releaseId && state.release != null && state.errorMessage == null) return
        loadedReleaseId = releaseId

        _uiState.value = ReleaseDetailsUiState(isLoading = true)
        viewModelScope.launch {
            try {
                releaseRepository.observeRelease(releaseId).collect { cached ->
                    _uiState.update { it.copy(isLoading = false, release = cached.value, errorMessage = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ReleaseDetailsUiState(errorMessage = e.toLoadError())
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
                    it.copy(isEpisodesStepLoading = false, episodesStepError = e.toLoadError())
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
                    it.copy(isEpisodesStepLoading = false, episodesStepError = e.toLoadError())
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
                    it.copy(isEpisodesStepLoading = false, episodesStepError = e.toLoadError())
                }
            }
        }
    }

    /**
     * Меняет статус релиза в списке пользователя ([status] `null` — снять статус).
     *
     * `LibraryRepository.addToList`/`removeFromList` (P4.T7) сами пишут новый статус в БД
     * оптимистично, синхронно, до постановки в офлайн-очередь — тут больше не нужен ручной
     * `_uiState`-патчинг с откатом: провал отправки на сервер (сеть/401/перманентная ошибка)
     * теперь забота `SyncQueueWorker` (см. его KDoc про last-write-wins и обработку ошибок), а не
     * этого ViewModel. [refreshRelease] сразу после — перечитывает уже обновлённую строку из кэша
     * (см. KDoc [refreshRelease] про то, почему это одноразовое чтение, а не продолжение
     * подписки [load]).
     */
    fun changeListStatus(status: ListStatus?) {
        val release = _uiState.value.release ?: return
        if (release.myListStatus == status) return

        viewModelScope.launch {
            if (status != null) {
                libraryRepository.addToList(status, release.id)
            } else {
                libraryRepository.removeFromList(release.id)
            }
            refreshRelease(release.id)
        }
    }

    /**
     * Тоггл избранного — та же схема, что и [changeListStatus] (оптимистичная запись в БД
     * репозиторием + перечитывание).
     */
    fun toggleFavorite() {
        val release = _uiState.value.release ?: return

        viewModelScope.launch {
            if (release.isFavorite) {
                libraryRepository.removeFavorite(release.id)
            } else {
                libraryRepository.addFavorite(release.id)
            }
            refreshRelease(release.id)
        }
    }

    /**
     * Одноразовое чтение свежего значения релиза из кэша после мутации `LibraryRepository`.
     *
     * Не переиспользует уже запущенный в [load] `collect`: `cacheFirstFlow` (см. `CacheFirst.kt`)
     * — не вечная подписка на БД, а один (максимум два — кэш, затем сеть) эмит на вызов, после
     * чего сам поток завершается, так что коллектор из [load] к этому моменту уже неактивен.
     * Локальная запись `LibraryRepository.addToList`/... происходит синхронно до возврата из
     * suspend-функции, поэтому здесь `observeRelease(...).first()` гарантированно видит
     * обновлённый JOIN с `listMembership`, не задевая сеть (штамп свежести самого релиза при
     * этом не менялся).
     */
    private suspend fun refreshRelease(releaseId: Int) {
        val cached = releaseRepository.observeRelease(releaseId).first()
        _uiState.update { it.copy(release = cached.value) }
    }
}

private inline fun MutableStateFlow<ReleaseDetailsUiState>.update(block: (ReleaseDetailsUiState) -> ReleaseDetailsUiState) {
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
