package com.aniko.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.model.AnixError
import com.aniko.model.VideoHost
import com.aniko.player.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @param hasNextEpisode следующая серия (`position + 1`) существует в этом же источнике —
 * условие показа баннера P8.T4 и кнопки «Следующая серия» на Desktop. `false`, пока проверка не
 * завершилась или если её не удалось выполнить (см. `EpisodeRepository.hasEpisode`).
 * @param isWatched текущая серия отмечена просмотренной. Читается из локального стора
 * (`EpisodeProgressStore`), поэтому меняется сразу после оптимистичной записи, не дожидаясь сети.
 */
data class PlayerUiState(
    val isLoading: Boolean = true,
    val source: PlaybackSource? = null,
    val error: PlayerError? = null,
    val hasNextEpisode: Boolean = false,
    val isWatched: Boolean = false,
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
 * Резолвит [PlaybackSource] через `EpisodeRepository.resolvePlaybackSource`, наблюдает локальную
 * отметку просмотра текущей серии и проверяет наличие следующей.
 *
 * **P8.T8 изменил модель отметки «просмотрено».** Раньше здесь стояла эвристика MVP Фазы 5
 * «успешный резолв ссылки = серия просмотрена»: отметка ставилась сразу после загрузки, ещё до
 * единого кадра. Теперь этого нет — отметку ставит тот, кто действительно знает, досмотрели ли
 * серию:
 * - Android/iOS — экран, по общему порогу `EmbedVideoState.isNearEnd()` (`:shared:player`),
 *   тем же самым, что поднимает баннер P8.T4;
 * - Desktop — пользователь вручную, кнопкой (там `EmbedVideoController.isSupported == false`,
 *   позиции воспроизведения не существует в принципе, см. P8.T1).
 *
 * История просмотра (`addHistory`) осталась на месте по факту открытия: «продолжить смотреть»
 * — это про «начал», а не про «досмотрел», и её семантику P8.T8 не трогает.
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

    /** Подписка на локальную отметку просмотра — своя на каждую серию, старую гасим при смене. */
    private var watchedJob: Job? = null

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
        observeWatched(key)
        viewModelScope.launch {
            try {
                val source = episodeRepository.resolvePlaybackSource(releaseId, sourceId, position, host)
                _uiState.update { it.copy(isLoading = false, source = source, error = null) }
                // Та же логика для истории просмотра ("Фаза 6"): плееру не нужно знать об успехе
                // синхронизации истории, ошибку тоже проглатываем, а не мешаем воспроизведению.
                runCatching { libraryRepository.addHistory(releaseId, sourceId, position) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, source = null, error = e.toPlayerError()) }
            }
        }
        viewModelScope.launch {
            // Отдельной корутиной: наличие следующей серии не должно ни задерживать показ кадра,
            // ни ронять экран — `hasEpisode` сам глотает сетевую ошибку в `false`.
            val hasNext = episodeRepository.hasEpisode(releaseId, sourceId, position + 1)
            if (loadedKey == key) _uiState.update { it.copy(hasNextEpisode = hasNext) }
        }
    }

    fun retry() {
        loadedKey?.let { (releaseId, sourceId, position, host) -> load(releaseId, sourceId, position, host) }
    }

    /**
     * P8.T8 — авто-отметка «просмотрено» при подходе к концу серии (Android/iOS).
     *
     * Идемпотентна и по построению дешёвая при повторном вызове: экран дёргает её из
     * `LaunchedEffect` по общему порогу `isNearEnd()`, а тот держится истинным все последние
     * секунды серии. Если серия уже отмечена — второй записи и второй операции в офлайн-очереди
     * не будет.
     */
    fun markWatchedIfNeeded() {
        val key = loadedKey ?: return
        if (_uiState.value.isWatched) return
        viewModelScope.launch {
            // Ошибку глотаем: запись оптимистичная и переживёт офлайн через SyncQueue, а мешать
            // воспроизведению из-за счётчика просмотра незачем (та же политика, что у addHistory).
            runCatching { episodeRepository.markWatched(key.releaseId, key.sourceId, key.position) }
        }
    }

    /** P8.T8 — ручной toggle для Desktop, где позиции воспроизведения нет (P8.T1). */
    fun toggleWatched() {
        val key = loadedKey ?: return
        val target = !_uiState.value.isWatched
        viewModelScope.launch {
            runCatching {
                if (target) {
                    episodeRepository.markWatched(key.releaseId, key.sourceId, key.position)
                } else {
                    episodeRepository.markUnwatched(key.releaseId, key.sourceId, key.position)
                }
            }
        }
    }

    private fun observeWatched(key: LoadKey) {
        watchedJob?.cancel()
        watchedJob =
            viewModelScope.launch {
                episodeRepository.observeWatched(key.releaseId, key.sourceId, key.position).collect { watched ->
                    if (loadedKey == key) _uiState.update { it.copy(isWatched = watched) }
                }
            }
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
