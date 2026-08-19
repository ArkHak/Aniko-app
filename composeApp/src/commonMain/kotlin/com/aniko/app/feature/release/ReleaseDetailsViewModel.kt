package com.aniko.app.feature.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.repository.ReleaseRepository
import com.aniko.model.AnixError
import com.aniko.model.Episode
import com.aniko.model.ListStatus
import com.aniko.model.Release
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel карточки релиза (Title Detail, P7.T7-T13, Трек C).
 *
 * `releaseId` не передаётся в конструктор через Koin (`parametersOf` только усложнило бы DI
 * ради параметра, который и так приходит из type-safe навигации) — вместо этого экран сам
 * вызывает [load] из `LaunchedEffect(releaseId)`. [load] идемпотентен для одного и того же id,
 * пока не было ошибки — повторная композиция/пересоздание того же route не долбит сеть.
 *
 * Три независимых шага загрузки после успешного [load]:
 * 1. [loadVoiceTypes] — типы озвучки, первый шаг цепочки резолвинга плеера.
 * 2. [loadDetails] — расширенная карточка `ReleaseDetails` (метаданные/скриншоты/похожее),
 *    сетевая one-shot модель, падает независимо от базового релиза (D1: см. KDoc
 *    [ReleaseDetailsUiState] и [com.aniko.model.ReleaseDetails]).
 *
 * Флоу выбора серии (типы → источники → серии) — простой линейный стейт-машина без пагинации:
 * выбор типа сбрасывает источники и серии, выбор источника сбрасывает серии и переподписывается
 * на локальный оверрайд просмотренного (см. [observeWatchedForSource]). Ошибки на этом флоу не
 * путаются с ошибкой загрузки самого релиза — у них отдельное поле
 * [ReleaseDetailsUiState.episodesStepError], чтобы неудачный запрос источников не перекрывал уже
 * отрисованную карточку релиза.
 *
 * `TooManyFunctions`: один экран — один ViewModel, каждая функция отвечает ровно за один
 * пользовательский интент (load/retry/select.../toggle...) — дробить дальше означало бы либо
 * мигрировать на MVI-контракт (вне объёма трека C), либо резать по произвольной границе ради
 * самого счётчика.
 */
@Suppress("TooManyFunctions")
class ReleaseDetailsViewModel(
    private val releaseRepository: ReleaseRepository,
    private val episodeRepository: EpisodeRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReleaseDetailsUiState())
    val uiState: StateFlow<ReleaseDetailsUiState> = _uiState.asStateFlow()

    private var loadedReleaseId: Int? = null
    private var watchedJob: Job? = null

    /**
     * Реактивная загрузка через `ReleaseRepository.observeRelease` (P4.T7, S3) — БД остаётся SSOT,
     * поэтому оптимистичные локальные записи `changeListStatus`/`toggleFavorite` доходят до UI без
     * ручного патчинга стейта. Деградация при отсутствии сети реализована самим
     * `cacheFirstFlow`/`observeRelease` (см. их KDoc): если в кэше уже есть строка, сетевая ошибка
     * фонового обновления не роняет поток — сюда просто продолжает приходить кэшированное
     * значение. Полный `errorMessage` (блокирующий экран) — только если кэша не было вовсе.
     *
     * `cacheFirstFlow` (см. `CacheFirst.kt`, не трогать) — не вечная подписка, а один
     * (max два: кэш + сеть) эмит на вызов, после чего сам поток завершается — поэтому именно
     * здесь, после успешного `collect`, как и раньше, запускаются [loadVoiceTypes]/[loadDetails]:
     * точка «релиз успешно получен» не изменилась, изменился только источник (кэш вместо прямого
     * сетевого вызова). Оба шага независимы и запускаются параллельно (каждый — свой
     * `viewModelScope.launch`), ошибка одного не блокирует другой.
     */
    fun load(releaseId: Int) {
        val state = _uiState.value
        if (loadedReleaseId == releaseId && state.release != null && state.errorMessage == null) return
        loadedReleaseId = releaseId
        watchedJob?.cancel()

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
            loadDetails(releaseId)
        }
    }

    fun retry() {
        loadedReleaseId?.let { load(it) }
    }

    /** Повторная попытка только расширенной карточки — не трогает уже отрисованный базовый релиз. */
    fun retryDetails() {
        loadedReleaseId?.let { loadDetails(it) }
    }

    private fun loadDetails(releaseId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailsLoading = true, detailsError = null) }
            try {
                val details = releaseRepository.releaseDetails(releaseId)
                _uiState.update { it.copy(details = details, isDetailsLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isDetailsLoading = false, detailsError = e.toLoadError()) }
            }
        }
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
        watchedJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTypeId = typeId,
                sources = emptyList(),
                selectedSourceId = null,
                episodes = emptyList(),
                isEpisodesStepLoading = true,
                episodesStepError = null,
                watchedOverrides = emptySet(),
                localToggleOverrides = emptyMap(),
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
        watchedJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSourceId = sourceId,
                episodes = emptyList(),
                isEpisodesStepLoading = true,
                episodesStepError = null,
                watchedOverrides = emptySet(),
                localToggleOverrides = emptyMap(),
            )
        }
        viewModelScope.launch {
            try {
                val episodes = episodeRepository.episodes(releaseId, typeId, sourceId)
                _uiState.update { it.copy(episodes = episodes, isEpisodesStepLoading = false) }
                observeWatchedForSource(releaseId, sourceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isEpisodesStepLoading = false, episodesStepError = e.toLoadError())
                }
            }
        }
    }

    /** Подписка на локальный оверрайд просмотренного текущего источника — см. D4 в KDoc класса. */
    private fun observeWatchedForSource(
        releaseId: Int,
        sourceId: Int,
    ) {
        watchedJob =
            viewModelScope.launch {
                episodeRepository.observeWatchedPositions(releaseId, sourceId).collect { positions ->
                    _uiState.update { it.copy(watchedOverrides = positions) }
                }
            }
    }

    /**
     * Долгий тап по ячейке `EpisodeGrid` (D4) — переключает watched/unwatched. [episode] уже
     * пришёл смерженным (см. [displayEpisodes]), поэтому `!episode.isWatched` — корректная целевая
     * сторона тоггла без отдельного поиска текущего состояния.
     *
     * Пишет в [ReleaseDetailsUiState.localToggleOverrides] СИНХРОННО, до ухода в
     * `EpisodeRepository` — грид обновляется немедленно, не дожидаясь оптимистичной записи в БД
     * (которая тоже придёт следом через [observeWatchedForSource], но позже одного тика).
     */
    fun toggleWatched(episode: Episode) {
        val releaseId = loadedReleaseId ?: return
        val sourceId = _uiState.value.selectedSourceId ?: return
        val target = !episode.isWatched
        _uiState.update {
            it.copy(localToggleOverrides = it.localToggleOverrides + (episode.position to target))
        }
        viewModelScope.launch {
            if (target) {
                episodeRepository.markWatched(releaseId, sourceId, episode.position)
            } else {
                episodeRepository.markUnwatched(releaseId, sourceId, episode.position)
            }
        }
    }

    /**
     * Кнопка "Смотреть" (P7.T7) — сама проходит цепочку типы → источники → серии (первый тип,
     * первый источник — у `VoiceType`/`EpisodeSource` в этом проекте нет поля `pinned`, см.
     * `docs/REELWAVE_PLAN.md`, "Сейчас" по Player+озвучке — выбор первого варианта, не рискованное
     * упрощение, а буквально то, что доступно), резолвит стартовую позицию — продолжение с
     * `release.lastViewEpisode`, если такая серия есть в списке, иначе первая серия. По пути
     * заполняет тот же стейт выбора серии, что и ручной флоу чипов (voiceTypes/sources/episodes),
     * чтобы после возврата из плеера пользователь видел уже сделанный выбор, а не пустые чипы.
     *
     * Возвращает `null`, если резолвить нечего (нет типов/источников/серий) или шаг упал —
     * `ReleaseDetailsScreen` в этом случае просто не переходит в плеер, ошибка попадает в то же
     * поле [ReleaseDetailsUiState.episodesStepError], что и у ручного флоу.
     */
    suspend fun resolvePlayTarget(): PlayTarget? {
        val releaseId = loadedReleaseId
        val release = _uiState.value.release
        if (releaseId == null || release == null) return null

        _uiState.update { it.copy(isResolvingPlay = true) }
        return try {
            resolvePlayTargetChain(releaseId, release)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(episodesStepError = e.toLoadError()) }
            null
        } finally {
            _uiState.update { it.copy(isResolvingPlay = false) }
        }
    }

    /**
     * Цепочка `types -> sources -> episodes` вынесена из [resolvePlayTarget] отдельной функцией
     * (detekt `ReturnCount`) — сама цепочка остаётся серией guard clauses (идиоматичнее вложенных
     * `let`/`when` для линейного разрешения "первый доступный на каждом шаге").
     */
    @Suppress("ReturnCount")
    private suspend fun resolvePlayTargetChain(
        releaseId: Int,
        release: Release,
    ): PlayTarget? {
        val types = _uiState.value.voiceTypes.ifEmpty { episodeRepository.voiceTypes(releaseId) }
        val type = types.firstOrNull() ?: return null
        val sources = episodeRepository.sources(releaseId, type.id)
        val source = sources.firstOrNull() ?: return null
        val episodes = episodeRepository.episodes(releaseId, type.id, source.id)
        if (episodes.isEmpty()) return null
        val resumePosition = release.lastViewEpisode?.let { last -> episodes.firstOrNull { it.position == last } }
        val position = (resumePosition ?: episodes.first()).position

        watchedJob?.cancel()
        _uiState.update {
            it.copy(
                voiceTypes = types,
                selectedTypeId = type.id,
                sources = sources,
                selectedSourceId = source.id,
                episodes = episodes,
                watchedOverrides = emptySet(),
                localToggleOverrides = emptyMap(),
            )
        }
        observeWatchedForSource(releaseId, source.id)
        return PlayTarget(sourceId = source.id, position = position, host = source.host)
    }

    /**
     * Резолвит deep link на конкретную серию (P10.T7, `AnixDestination.ReleaseDetails.
     * pendingEpisodeSourceId`/`pendingEpisodePosition`) в [PlayTarget] — вызывается
     * `ReleaseDetailsScreen` один раз после успешной загрузки релиза, если маршрут пришёл с этой
     * парой параметров.
     *
     * URL деплинка несёт только `sourceId`/`position` — БЕЗ `typeId` (см. обоснование схемы в
     * `DeepLink.kt`), а `EpisodeRepository.sources`/`episodes` требуют его явно. Поэтому цепочка
     * перебирает `voiceTypes` по очереди и на каждом ищет источник с нужным `id` — источники
     * (`EpisodeSource.id`) в Anixart отдельные записи БД на каждую пару тип-озвучки/хост, а не общий
     * пул id, переиспользуемый между типами, поэтому первое совпадение по `id` уже верное, не
     * просто "первое попавшееся" — более одного успешного попадания по построению не ожидается.
     *
     * Возвращает `null`, если сеть недоступна, серия/источник не существуют (протухшая или битая
     * ссылка) — экран в этом случае просто остаётся на карточке тайтла, ошибка НЕ выставляется в
     * [ReleaseDetailsUiState.episodesStepError]: неудачный deep link не должен выглядеть как
     * поломка обычного флоу выбора серии.
     */
    suspend fun resolveDeepLinkEpisode(
        sourceId: Int,
        position: Int,
    ): PlayTarget? {
        val releaseId = loadedReleaseId ?: return null
        _uiState.update { it.copy(isResolvingPlay = true) }
        return try {
            resolveDeepLinkEpisodeChain(releaseId, sourceId, position)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
        ) {
            // Намеренно проглочено, см. KDoc функции выше: битый/протухший deep link не должен
            // выглядеть как ошибка обычного флоу выбора серии (episodesStepError не трогаем).
            null
        } finally {
            _uiState.update { it.copy(isResolvingPlay = false) }
        }
    }

    @Suppress("ReturnCount") // серия guard clauses по цепочке типы→источники→серии, см. KDoc выше.
    private suspend fun resolveDeepLinkEpisodeChain(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ): PlayTarget? {
        val types = _uiState.value.voiceTypes.ifEmpty { episodeRepository.voiceTypes(releaseId) }
        for (type in types) {
            val sources = episodeRepository.sources(releaseId, type.id)
            val source = sources.firstOrNull { it.id == sourceId } ?: continue
            val episodes = episodeRepository.episodes(releaseId, type.id, sourceId)
            val episode = episodes.firstOrNull { it.position == position } ?: return null

            watchedJob?.cancel()
            _uiState.update {
                it.copy(
                    voiceTypes = types,
                    selectedTypeId = type.id,
                    sources = sources,
                    selectedSourceId = source.id,
                    episodes = episodes,
                    watchedOverrides = emptySet(),
                    localToggleOverrides = emptyMap(),
                )
            }
            observeWatchedForSource(releaseId, source.id)
            return PlayTarget(sourceId = source.id, position = episode.position, host = source.host)
        }
        return null
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
