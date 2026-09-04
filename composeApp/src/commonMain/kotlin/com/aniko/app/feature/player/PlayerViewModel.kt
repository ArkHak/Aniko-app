package com.aniko.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.model.AnixError
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.player.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * @param episodeName человекочитаемое имя текущей серии (`"1 серия"`) из `episode/target` —
 * держим его не ради отображения (в оверлее его нет), а как вход для
 * `EpisodeRepository.matchPosition` при переключении озвучки (P13.T10, см. [voiceTypes]).
 * @param voiceTypes список озвучек релиза для чипа «Audio» (P13.T10). Догружается лениво отдельной
 * корутиной после основной загрузки — открытие плеера не обязано ждать её ради кадра видео.
 * @param currentVoiceType озвучка текущего источника — определяется подбором (маршрут плеера несёт
 * только `sourceId`, не `typeId`, см. KDoc [resolveCurrentVoiceType]). `null`, пока подбор не
 * завершился или для источника не нашлось соответствия.
 * @param isAudioSwitching идёт переключение озвучки — блокирует повторный тап по строке в пикере,
 * пока не разрешится сеть (выбор источника + список серий + резолв ссылки).
 * @param isFullscreen режим отображения плеера (P13). Живёт здесь, а не в `remember` на
 * [PlayerScreen] — живая проверка нашла: реальный поворот экрана в альбомную ориентацию нередко
 * пересекает границу `AnixWindowSize` (Compact/Medium/Expanded), а [com.aniko.ui.adaptive.AdaptiveScaffold]
 * вызывает свой `content(...)` из ТРЁХ разных call site по одному на каждую ветку — при смене
 * ветки Compose разбирает и заново строит всё поддерево `content`, включая `NavHost` с этим
 * экраном, и любой `remember` внутри [PlayerScreen] стирается до значения по умолчанию, хотя сама
 * `Activity` не пересоздаётся (подтверждено логами: `DisposableEffect` у
 * `LockLandscapeOrientationEffect` диспозится без единого вызова колбэка сворачивания). ViewModel
 * же живёт в `ViewModelStore` конкретной `NavBackStackEntry`, которая не зависит от того, через
 * какую ветку `AdaptiveScaffold` сейчас отрисован `NavHost` — переживает эту перестройку.
 */
data class PlayerUiState(
    val isLoading: Boolean = true,
    val source: PlaybackSource? = null,
    val error: PlayerError? = null,
    val hasNextEpisode: Boolean = false,
    val isWatched: Boolean = false,
    val episodeName: String? = null,
    val voiceTypes: List<VoiceType> = emptyList(),
    val currentVoiceType: VoiceType? = null,
    val isAudioSwitching: Boolean = false,
    val isFullscreen: Boolean = false,
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

        // Озвучка нового источника ещё не известна (её выясняет `resolveCurrentVoiceType` заново
        // по новому `sourceId`) — список типов из прошлого источника переиспользуем как есть,
        // чтобы пикер не мигал пустым списком при переключении на серию того же релиза. Так же
        // переносим `isFullscreen` — `selectVoiceType` вызывает этот же `load()` посреди
        // воспроизведения в fullscreen, терять режим отображения при смене озвучки не должны.
        _uiState.value =
            PlayerUiState(
                isLoading = true,
                voiceTypes = _uiState.value.voiceTypes,
                isFullscreen = _uiState.value.isFullscreen,
            )
        observeWatched(key)
        viewModelScope.launch {
            try {
                val resolved = episodeRepository.resolveEpisodeTarget(releaseId, sourceId, position, host)
                // loadedKey могла уже смениться (пользователь быстро переключил серию/озвучку,
                // пока этот запрос летел) — тот же guard, что и у остальных асинхронных записей в
                // uiState в этом классе (см. hasNextEpisode/resolveCurrentVoiceType/selectVoiceType
                // ниже), иначе устаревший ответ перетёр бы уже актуальное состояние.
                if (loadedKey == key) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            source = resolved.source,
                            error = null,
                            episodeName = resolved.episodeName,
                        )
                    }
                }
                // Та же логика для истории просмотра ("Фаза 6"): плееру не нужно знать об успехе
                // синхронизации истории, ошибку тоже проглатываем, а не мешаем воспроизведению.
                runCatching { libraryRepository.addHistory(releaseId, sourceId, position) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (loadedKey == key) {
                    _uiState.update { it.copy(isLoading = false, source = null, error = e.toPlayerError()) }
                }
            }
        }
        viewModelScope.launch {
            // Отдельной корутиной: наличие следующей серии не должно ни задерживать показ кадра,
            // ни ронять экран — `hasEpisode` сам глотает сетевую ошибку в `false`.
            val hasNext = episodeRepository.hasEpisode(releaseId, sourceId, position + 1)
            if (loadedKey == key) _uiState.update { it.copy(hasNextEpisode = hasNext) }
        }
        resolveCurrentVoiceType(key)
    }

    /**
     * P13.T10 — узнаёт, какой озвучке принадлежит текущий `sourceId`, и заодно список всех
     * озвучек релиза (для пикера в чипе «Audio»).
     *
     * Маршрут плеера (`AnixDestination.Player`) несёт только `sourceId`/`hostKey`, не `typeId`
     * (см. KDoc `PlayerViewModel` про источник параметров) — расширять маршрут ради одного чипа
     * не стали, чтобы не тащить `typeId` через всю навигацию (Detail-экран, deep links, «следующая
     * серия»). Вместо этого typeId подбирается: под каждой озвучкой релиза (их обычно 2-4) просим
     * список источников и ищем среди них текущий `sourceId`, все запросы — параллельно. Не самый
     * дешёвый способ, но выполняется один раз за открытие плеера и не блокирует показ кадра
     * видео (совсем отдельная корутина, кадр грузится своей веткой чуть выше).
     */
    private fun resolveCurrentVoiceType(key: LoadKey) {
        viewModelScope.launch {
            val types = runCatching { episodeRepository.voiceTypes(key.releaseId) }.getOrDefault(emptyList())
            if (loadedKey != key) return@launch
            _uiState.update { it.copy(voiceTypes = types) }

            val matched =
                coroutineScope {
                    types
                        .map { type -> type to async { sourcesOf(key.releaseId, type.id) } }
                        .firstOrNull { (_, sourcesDeferred) -> sourcesDeferred.await().any { it.id == key.sourceId } }
                        ?.first
                }
            if (loadedKey == key) _uiState.update { it.copy(currentVoiceType = matched) }
        }
    }

    /** Сетевую ошибку глотаем в пустой список — не знать источники одной озвучки не должно ронять весь подбор. */
    private suspend fun sourcesOf(
        releaseId: Int,
        typeId: Int,
    ) = runCatching { episodeRepository.sources(releaseId, typeId) }.getOrDefault(emptyList())

    /**
     * Переключение озвучки прямо в плеере (P13.T10) — выбор строки в пикере поверх [PlayerOverlay],
     * не отдельный экран/маршрут: перезагружает этот же [PlayerScreen] новым `sourceId`/`host` тем
     * же вызовом [load], без навигации и без `popBackStack`.
     *
     * Источник для новой озвучки выбирается предпочтительно с тем же хостом, что играл сейчас
     * (тише всего для пользователя — тот же плеер под капотом), иначе первый доступный.
     * Позиция — через `EpisodeRepository.matchPosition` (см. её KDoc про то, почему не сам
     * `position`): по номеру серии, а не по индексу.
     *
     * `ReturnCount`: guard clauses по «нет активной загрузки» / «та же озвучка уже выбрана» /
     * «тип не из списка» — линейная цепочка условий читается лучше вложенного `when`.
     */
    @Suppress("ReturnCount")
    fun selectVoiceType(typeId: Int) {
        val key = loadedKey ?: return
        if (typeId == _uiState.value.currentVoiceType?.id) return
        if (_uiState.value.voiceTypes.none { it.id == typeId }) return // typeId не из списка озвучек этого релиза
        val episodeName = _uiState.value.episodeName
        _uiState.update { it.copy(isAudioSwitching = true) }
        viewModelScope.launch {
            val sources = sourcesOf(key.releaseId, typeId)
            val newSource = sources.firstOrNull { it.host == key.host } ?: sources.firstOrNull()
            if (newSource == null) {
                // Нет ни одного источника у выбранной озвучки — переключаться некуда, оставляем
                // как было и просто снимаем индикатор загрузки пикера.
                if (loadedKey == key) _uiState.update { it.copy(isAudioSwitching = false) }
                return@launch
            }
            val matchedPosition =
                runCatching {
                    episodeRepository.matchPosition(key.releaseId, typeId, newSource.id, episodeName, key.position)
                }.getOrDefault(key.position)
            if (loadedKey != key) return@launch // пользователь уже успел уйти с экрана/переключить снова
            // `currentVoiceType = type` здесь не пишем: `load()` ниже тут же сбросит стейт в новый
            // `PlayerUiState` и сам заново подберёт тип через `resolveCurrentVoiceType` (теперь уже
            // по-настоящему — источник `newSource.id` принадлежит `typeId`, подбор найдёт его сразу).
            _uiState.update { it.copy(isAudioSwitching = false) }
            load(key.releaseId, newSource.id, matchedPosition, newSource.host)
        }
    }

    /** P13 — переключатель compact/fullscreen, см. KDoc [PlayerUiState.isFullscreen] про то,
     *  почему это состояние здесь, а не `remember` на [PlayerScreen]. */
    fun setFullscreen(value: Boolean) {
        _uiState.update { it.copy(isFullscreen = value) }
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
