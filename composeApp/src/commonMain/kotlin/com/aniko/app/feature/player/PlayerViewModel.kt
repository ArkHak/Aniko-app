package com.aniko.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.playerposition.LocalPlayerPositionStore
import com.aniko.data.playerposition.PositionKey
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.voicepreference.TitleVoicePreferenceStore
import com.aniko.model.AnixError
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.player.PlaybackSource
import com.aniko.player.playerOpensFullscreen
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
 * @param nextEpisodePosition `position` следующей серии этого же источника — цель кнопки
 * «Следующая серия» и карточки автоперехода. После загрузки [episodes] — позиция СОСЕДНЕГО
 * элемента списка (нумерация источников не обязана быть непрерывной: дырки, старт с 0 или 1);
 * до загрузки — fallback `position + 1`, если `EpisodeRepository.hasEpisode` его подтвердил.
 * `null` — следующей серии нет или проверка ещё не завершилась.
 * @param prevEpisodePosition симметрично [nextEpisodePosition] для «Предыдущей серии».
 * @param episodes список серий текущего источника (`EpisodeRepository.episodes`, нужен `typeId`,
 * который подбирается там же, что и для [currentVoiceType], — см. [resolveCurrentVoiceType]).
 * Догружается отдельной веткой после подбора озвучки и не задерживает показ кадра; пустой, пока
 * не загрузился или если запрос упал (шторка серий тогда просто не откроется — кнопка её гейтится
 * непустым списком). Отметки просмотра в нём — серверный слепок на момент запроса; живые локальные
 * отметки — в [watchedPositions], мерджатся в UI при отрисовке шторки.
 * @param watchedPositions весь известный локальный статус просмотра источника (позиция →
 * is_watched), collect из `EpisodeRepository.observeWatchedPositions` — обновляется сразу после
 * оптимистичной записи (P4.T7), в отличие от снепшота [episodes].
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
 * @param isFullscreen режим отображения плеера (P13). Дефолт платформенный
 * ([playerOpensFullscreen]: Desktop открывается сразу во всю высоту окна приложения,
 * Android/iOS — в компактном chrome). Живёт здесь, а не в `remember` на
 * [PlayerScreen] — живая проверка нашла: реальный поворот экрана в альбомную ориентацию нередко
 * пересекает границу `AnixWindowSize` (Compact/Medium/Expanded), а [com.aniko.ui.adaptive.AdaptiveScaffold]
 * вызывает свой `content(...)` из ТРЁХ разных call site по одному на каждую ветку — при смене
 * ветки Compose разбирает и заново строит всё поддерево `content`, включая `NavHost` с этим
 * экраном, и любой `remember` внутри [PlayerScreen] стирается до значения по умолчанию, хотя сама
 * `Activity` не пересоздаётся (подтверждено логами: `DisposableEffect` у
 * `LockLandscapeOrientationEffect` диспозится без единого вызова колбэка сворачивания). ViewModel
 * же живёт в `ViewModelStore` конкретной `NavBackStackEntry`, которая не зависит от того, через
 * какую ветку `AdaptiveScaffold` сейчас отрисован `NavHost` — переживает эту перестройку.
 * @param resumeNoticeMs P16.T7 (переработано 2026-10-01) — серия продолжена с сохранённой позиции
 * (мс): экран показывает поверх видео плашку «Продолжено с M:SS · С начала». Раньше был блокирующий
 * диалог «Продолжить / С начала» — отдельное окно, которое в fullscreen возвращало системные панели
 * и держало серию на паузе до ответа; медиаприложения продолжают молча. `null` — плашки нет (позиции
 * не было, или плашку уже закрыли — [PlayerViewModel.onResumeNoticeDismiss]/[PlayerViewModel.onResumeStartOver]).
 * @param pendingSeekToMs позиция, на которую нужно перемотать видео сразу, как только мост найдёт
 * `<video>` (`EmbedVideoState.isVideoFound`); выставляется вместе с [resumeNoticeMs], потребляется
 * и сбрасывается [PlayerScreen] через [PlayerViewModel.onResumeSeekConsumed] после фактического `seekTo`.
 * @param alternativeSource другой источник ТОЙ ЖЕ озвучки (непустой, не текущий) — цель кнопки
 * «Другой источник» на экране сбоя загрузки: у озвучки нередко два хоста (Sibnet + Kodik), и если один
 * недоступен (403 вне РФ), второй обычно играет. `null` — альтернативы нет или подбор не завершён.
 * @param positionKey ключ локального хранилища позиции РЕАЛЬНО загруженного источника — он совпадает
 * с ключом маршрута экрана, пока озвучку не переключали, и отличается после [PlayerViewModel.selectVoiceType]
 * (тот перезагружает плеер другим `sourceId`/`position`, а маршрут остаётся прежним). Выставляется
 * вместе с [source]; `null` — пока источник не загружен. Именно по нему [PlayerScreen] сохраняет позицию
 * при разборе плеера: маршрутные `sourceId`/`position` после смены озвучки записали бы позицию новой
 * озвучки под ключ исходной.
 */
data class PlayerUiState(
    val isLoading: Boolean = true,
    val source: PlaybackSource? = null,
    val error: PlayerError? = null,
    val nextEpisodePosition: Int? = null,
    val prevEpisodePosition: Int? = null,
    val isWatched: Boolean = false,
    val episodeName: String? = null,
    val episodes: List<Episode> = emptyList(),
    val watchedPositions: Map<Int, Boolean> = emptyMap(),
    val voiceTypes: List<VoiceType> = emptyList(),
    val currentVoiceType: VoiceType? = null,
    val isAudioSwitching: Boolean = false,
    val isFullscreen: Boolean = playerOpensFullscreen(),
    val resumeNoticeMs: Long? = null,
    val pendingSeekToMs: Long? = null,
    val positionKey: PositionKey? = null,
    val alternativeSource: EpisodeSource? = null,
) {
    /** Следующая серия существует — гейт кнопки «Следующая серия» и карточки автоперехода. */
    val hasNextEpisode: Boolean get() = nextEpisodePosition != null

    /** Предыдущая серия существует — гейт кнопки «Предыдущая серия». */
    val hasPrevEpisode: Boolean get() = prevEpisodePosition != null
}

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
 * - при работающем мосте (`isSupported == true` — Android/iOS/Desktop) — экран, по общему порогу
 *   `EmbedVideoState.isNearEnd()` (`:shared:player`), тем же самым, что поднимает баннер P8.T4;
 * - без моста (`isSupported == false`, фолбэк — см. `PlayerDesktopControls`) — пользователь
 *   вручную, кнопкой: позиции воспроизведения не существует в принципе.
 *
 * История просмотра (`addHistory`) осталась на месте по факту открытия: «продолжить смотреть»
 * — это про «начал», а не про «досмотрел», и её семантику P8.T8 не трогает.
 *
 * `releaseId`/`sourceId`/`position` приходят из `AnixDestination.Player` через `toRoute()` в
 * `App.kt`, аналогично `ReleaseDetailsViewModel.load(releaseId)` — не через Koin `parametersOf`.
 *
 * TooManyFunctions подавлен: функций больше дюжины — интенты экрана плеера + resume-поток
 * (P16.T7) + догрузка списка серий/карты отметок для шторки серий; объединение смешало бы
 * независимые интенты.
 */
@Suppress("TooManyFunctions") // См. KDoc класса: рост функций — от независимых интентов плеера.
class PlayerViewModel(
    private val episodeRepository: EpisodeRepository,
    private val libraryRepository: LibraryRepository,
    private val positionStore: LocalPlayerPositionStore,
    private val titleVoicePreferenceStore: TitleVoicePreferenceStore,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            PlayerUiState(isFullscreen = PlayerFullscreenCarry.consume() ?: playerOpensFullscreen()),
        )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private data class LoadKey(
        val releaseId: Int,
        val sourceId: Int,
        val position: Int,
        val host: VideoHost,
    ) {
        /** P16.T7 — ключ локального хранилища позиции воспроизведения для этой серии. */
        fun toPositionKey() = PositionKey(releaseId = releaseId, sourceId = sourceId, episodeOrdinal = position)
    }

    /**
     * Источник, который РЕАЛЬНО загружен (или грузится) сейчас. После [selectVoiceType] отличается от
     * [acceptedRouteKey]: маршрут экрана остаётся прежним, а играет уже другая озвучка.
     */
    private var loadedKey: LoadKey? = null

    /**
     * Ключ маршрута, который экран передал в [load] и который был принят: только маршрут, без
     * внутренних перезагрузок ([selectVoiceType], [retry]). Нужен, чтобы повторный вызов [load] с
     * тем же ключом (экран покинул композицию и вошёл в неё снова — поворот/ресайз, см. KDoc
     * [PlayerUiState.isFullscreen]) не откатывал уже выбранную пользователем озвучку к исходной.
     */
    private var acceptedRouteKey: LoadKey? = null

    /** Подписка на локальную отметку просмотра — своя на каждую серию, старую гасим при смене. */
    private var watchedJob: Job? = null

    /** Подписка на карту отметок просмотра всего источника (для шторки серий) — аналогично [watchedJob]. */
    private var watchedPositionsJob: Job? = null

    /**
     * Точка входа экрана: грузит серию по параметрам МАРШРУТА.
     *
     * Повторный вызов с ключом маршрута, уже принятым раньше, — no-op, пока источник грузится или
     * загружен (см. [shouldStartLoad]), даже если пользователь с тех пор сменил озвучку
     * ([loadedKey] уже другой). Смена серии (другой ключ маршрута) и повтор после ошибки работают
     * как раньше.
     */
    fun load(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        host: VideoHost,
    ) {
        val key = LoadKey(releaseId, sourceId, position, host)
        val state = _uiState.value
        if (!shouldStartLoad(acceptedRouteKey, key, state.isLoading, state.source != null)) return
        acceptedRouteKey = key
        startLoad(key)
    }

    /**
     * Внутренняя перезагрузка ([selectVoiceType], [retry]): сверяется с [loadedKey] (что реально
     * играет), а не с ключом маршрута, и сам маршрут не трогает.
     */
    private fun reload(key: LoadKey) {
        val state = _uiState.value
        if (!shouldStartLoad(loadedKey, key, state.isLoading, state.source != null)) return
        startLoad(key)
    }

    private fun startLoad(key: LoadKey) {
        loadedKey = key

        // Озвучка нового источника ещё не известна (её выясняет `resolveCurrentVoiceType` заново
        // по новому `sourceId`) — список типов из прошлого источника переиспользуем как есть,
        // чтобы пикер не мигал пустым списком при переключении на серию того же релиза. Так же
        // переносим `isFullscreen` — `selectVoiceType` вызывает эту же загрузку ([reload]) посреди
        // воспроизведения в fullscreen, терять режим отображения при смене озвучки не должны.
        _uiState.value =
            PlayerUiState(
                isLoading = true,
                voiceTypes = _uiState.value.voiceTypes,
                isFullscreen = _uiState.value.isFullscreen,
            )
        observeWatched(key)
        observeWatchedPositions(key)
        viewModelScope.launch {
            try {
                val resolved =
                    episodeRepository.resolveEpisodeTarget(key.releaseId, key.sourceId, key.position, key.host)
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
                            positionKey = key.toPositionKey(),
                        )
                    }
                }
                // Та же логика для истории просмотра ("Фаза 6"): плееру не нужно знать об успехе
                // синхронизации истории, ошибку тоже проглатываем, а не мешаем воспроизведению.
                runCatching { libraryRepository.addHistory(key.releaseId, key.sourceId, key.position) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (loadedKey == key) {
                    _uiState.update { it.copy(isLoading = false, source = null, error = e.toPlayerError()) }
                }
            }
        }
        checkNeighbourFallback(key)
        // P16.T7 — авто-продолжение: сохранённая позиция читается параллельно с кадром видео и не
        // задерживает его показ. Только при позиции дальше самого начала (см. [RESUME_MIN_POSITION_MS])
        // — иначе плашка всплывала бы на каждой почти нетронутой серии.
        viewModelScope.launch {
            val saved = runCatching { positionStore.load(key.toPositionKey()) }.getOrNull()
            if (loadedKey == key && saved != null && saved >= RESUME_MIN_POSITION_MS) {
                _uiState.update { it.copy(resumeNoticeMs = saved, pendingSeekToMs = saved) }
            }
        }
        resolveCurrentVoiceType(key)
    }

    /**
     * Fallback соседних серий до загрузки списка ([loadEpisodes]): `hasEpisode(position ± 1)`.
     * Отдельными корутинами — не задерживают кадр и не роняют экран (`hasEpisode` глотает ошибку).
     */
    private fun checkNeighbourFallback(key: LoadKey) {
        viewModelScope.launch {
            // Отдельной корутиной: наличие следующей серии не должно ни задерживать показ кадра,
            // ни ронять экран — `hasEpisode` сам глотает сетевую ошибку в `false`. Это fallback до
            // загрузки [PlayerUiState.episodes]; как только список пришёл, флаги пересчитываются
            // по индексу текущей позиции в нём (см. [loadEpisodes]) — `position` у источников
            // нумеруется не обязательно с единицы и не обязательно непрерывно.
            val hasNext = episodeRepository.hasEpisode(key.releaseId, key.sourceId, key.position + 1)
            // Список серий мог прийти раньше — тогда его соседи точнее, fallback их не перетирает.
            if (loadedKey == key && hasNext && _uiState.value.episodes.isEmpty()) {
                _uiState.update { it.copy(nextEpisodePosition = key.position + 1) }
            }
        }
        viewModelScope.launch {
            // Симметричный fallback для кнопки «Предыдущая серия» — тот же `hasEpisode`, той же ценой.
            val hasPrev =
                key.position > 0 && episodeRepository.hasEpisode(key.releaseId, key.sourceId, key.position - 1)
            if (loadedKey == key && hasPrev && _uiState.value.episodes.isEmpty()) {
                _uiState.update { it.copy(prevEpisodePosition = key.position - 1) }
            }
        }
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

            val matchedWithSources =
                coroutineScope {
                    types
                        .map { type -> type to async { sourcesOf(key.releaseId, type.id) } }
                        .firstOrNull { (_, sourcesDeferred) -> sourcesDeferred.await().any { it.id == key.sourceId } }
                        ?.let { (type, sourcesDeferred) -> type to sourcesDeferred.await() }
                }
            val matched = matchedWithSources?.first
            val alternative =
                matchedWithSources
                    ?.second
                    ?.firstOrNull { it.id != key.sourceId && it.episodesCount != 0 }
            if (loadedKey == key) {
                _uiState.update { it.copy(currentVoiceType = matched, alternativeSource = alternative) }
            }
            // Список серий нужен `typeId` текущего источника — он известен ровно после этого
            // подбора, поэтому догрузка идёт здесь же, следующим шагом той же ветки (своя корутина
            // внутри [loadEpisodes] — показ кадра по-прежнему не ждёт ни подбора, ни списка).
            loadEpisodes(key, matched?.id)
        }
    }

    /**
     * Догружает [PlayerUiState.episodes] текущего источника для шторки серий плеера. `typeId`
     * берётся из подбора [resolveCurrentVoiceType] (маршрут плеера его не несёт, см. там) —
     * поэтому функция вызывается только после завершения подбора и получает его результат.
     *
     * По факту загрузки пересчитывает [PlayerUiState.nextEpisodePosition]/
     * [PlayerUiState.prevEpisodePosition] как позиции СОСЕДНИХ элементов списка (см.
     * [neighbourPositions]): `position ± 1` — лишь fallback, пока списка нет, и он врёт для
     * источников с нестандартной нумерацией (дырки в номерах — см. живую проверку в KDoc
     * `EpisodeRepository.matchPosition`). Если текущей позиции в списке нет (список пуст/упал/
     * позиция вне списка) — значения не трогаем, остаётся fallback.
     */
    private suspend fun loadEpisodes(
        key: LoadKey,
        typeId: Int?,
    ) {
        if (typeId == null) return
        val list =
            runCatching { episodeRepository.episodes(key.releaseId, typeId, key.sourceId) }
                .getOrDefault(emptyList())
        if (loadedKey != key) return
        val neighbours = neighbourPositions(list, key.position)
        _uiState.update {
            if (neighbours == null) {
                it.copy(episodes = list)
            } else {
                it.copy(
                    episodes = list,
                    prevEpisodePosition = neighbours.first,
                    nextEpisodePosition = neighbours.second,
                )
            }
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
     * (тише всего для пользователя — тот же плеер под капотом), иначе первый доступный; опустевшие
     * источники (`episodesCount == 0`) в выбор не попадают, пока есть непустые.
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
            // Опустевшие источники (episodesCount == 0 — у хоста нет серий этого тайтла) не
            // выбираем, пока есть непустые: иначе переключение озвучки уходило в мёртвый
            // источник и воспроизведение останавливалось (та же находка, что и в
            // `ReleaseDetailsViewModel.resolvePlayTargetChain`, 2026-09-28).
            val pool = sources.filter { it.episodesCount != 0 }.ifEmpty { sources }
            val newSource = pool.firstOrNull { it.host == key.host } ?: pool.firstOrNull()
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
            // Dubbing memory: переключение озвучки в пикере — явный выбор пары typeId+sourceId
            // для этого тайтла, фиксируем его до перезагрузки плеера.
            titleVoicePreferenceStore.save(key.releaseId, typeId, newSource.id)
            // `currentVoiceType = type` здесь не пишем: перезагрузка ниже тут же сбросит стейт в новый
            // `PlayerUiState` и сам заново подберёт тип через `resolveCurrentVoiceType` (теперь уже
            // по-настоящему — источник `newSource.id` принадлежит `typeId`, подбор найдёт его сразу).
            _uiState.update { it.copy(isAudioSwitching = false) }
            reload(LoadKey(key.releaseId, newSource.id, matchedPosition, newSource.host))
        }
    }

    /**
     * «Другой источник» на экране сбоя загрузки: та же озвучка, другой хост ([PlayerUiState.alternativeSource]).
     * Позиция — через `matchPosition` (нумерация у источников разная), как при смене озвучки.
     */
    fun switchToAlternativeSource() {
        val key = loadedKey
        val target = _uiState.value.alternativeSource
        val typeId = _uiState.value.currentVoiceType?.id
        if (key == null || target == null || typeId == null) return
        val episodeName = _uiState.value.episodeName
        viewModelScope.launch {
            val matchedPosition =
                runCatching {
                    episodeRepository.matchPosition(key.releaseId, typeId, target.id, episodeName, key.position)
                }.getOrDefault(key.position)
            if (loadedKey != key) return@launch
            titleVoicePreferenceStore.save(key.releaseId, typeId, target.id)
            reload(LoadKey(key.releaseId, target.id, matchedPosition, target.host))
        }
    }

    /** P13 — переключатель compact/fullscreen, см. KDoc [PlayerUiState.isFullscreen] про то,
     *  почему это состояние здесь, а не `remember` на [PlayerScreen]. */
    fun setFullscreen(value: Boolean) {
        _uiState.update { it.copy(isFullscreen = value) }
    }

    /** Плашка «Продолжено с M:SS» скрылась сама (таймер) — позиция остаётся как есть. */
    fun onResumeNoticeDismiss() {
        _uiState.update { it.copy(resumeNoticeMs = null) }
    }

    /**
     * P16.T7 — «С начала» на плашке авто-продолжения: плашка закрывается, отложенная перемотка
     * отменяется (если ещё не выполнена), сохранённая позиция стирается — иначе следующее открытие
     * той же серии снова продолжило бы с отвергнутой позиции. Саму перемотку на 0 делает [PlayerScreen]
     * (ViewModel не знает про контроллер).
     */
    fun onResumeStartOver() {
        val key = loadedKey ?: return
        _uiState.update { it.copy(resumeNoticeMs = null, pendingSeekToMs = null) }
        viewModelScope.launch { runCatching { positionStore.clear(key.toPositionKey()) } }
    }

    /** P16.T7 — [PlayerScreen] вызывает это сразу после того, как реально выполнил `seekTo`
     *  авто-продолжения — снимает [PlayerUiState.pendingSeekToMs], чтобы повторная
     *  рекомпозиция не перемотала видео второй раз. */
    fun onResumeSeekConsumed() {
        _uiState.update { it.copy(pendingSeekToMs = null) }
    }

    /**
     * P16.T7 — сохранение позиции воспроизведения (троттлинг ~2с и финальное сохранение на
     * паузе/dispose — забота [PlayerScreen], здесь только запись). Позиция у конца серии
     * (`>=` [RESUME_NEAR_END_PROGRESS] от длительности, если она известна) не сохраняется, а
     * стирает существующую запись — досмотренную серию не нужно предлагать «продолжить».
     */
    fun onPlaybackPositionChanged(
        currentMs: Long,
        durationMs: Long?,
    ) {
        val key = loadedKey ?: return
        // Пока перемотка на точку продолжения не выполнена, автосохранение запрещено: видео уже
        // играет с 0, и запись затёрла бы настоящую точку resume (ревью Волны 3, P3).
        if (_uiState.value.pendingSeekToMs != null) return
        persistPosition(key.toPositionKey(), currentMs, durationMs)
    }

    /**
     * P16.T7 — сохранение позиции при dispose контроллера. Принимает ключ ЯВНО: к моменту
     * dispose (смена серии/озвучки) [loadedKey] может уже указывать на новую серию, и запись под
     * «живым» ключом положила бы позицию старой серии в ключ новой (ревью Волны 3, P3).
     * Подавление до перемотки на точку продолжения — только если она относится к ЭТОМУ ключу.
     */
    fun onControllerDisposed(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        currentMs: Long,
        durationMs: Long?,
    ) {
        val positionKey = PositionKey(releaseId = releaseId, sourceId = sourceId, episodeOrdinal = position)
        val resumePendingForSameKey =
            _uiState.value.pendingSeekToMs != null &&
                loadedKey?.let {
                    it.releaseId == releaseId && it.sourceId == sourceId && it.position == position
                } == true
        if (resumePendingForSameKey) return
        persistPosition(positionKey, currentMs, durationMs)
    }

    private fun persistPosition(
        positionKey: PositionKey,
        currentMs: Long,
        durationMs: Long?,
    ) {
        val progress = durationMs?.takeIf { it > 0 }?.let { currentMs.toDouble() / it }
        viewModelScope.launch {
            runCatching {
                if (progress != null && progress >= RESUME_NEAR_END_PROGRESS) {
                    positionStore.clear(positionKey)
                } else {
                    positionStore.save(positionKey, currentMs)
                }
            }
        }
    }

    /**
     * Повтор после ошибки: перезагружает ТЕКУЩИЙ ([loadedKey]) источник — тот, на котором упало, —
     * а не исходный маршрут: если пользователь успел сменить озвучку и сбой случился на ней, повторять
     * нужно её. Вне ошибки (грузится/загружено) — no-op, как и раньше.
     */
    fun retry() {
        loadedKey?.let(::reload)
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

    /** P8.T8 — ручной toggle для фолбэка без моста (`PlayerDesktopControls`), где позиции воспроизведения нет. */
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

    /**
     * Карта отметок просмотра ВСЕГО источника (позиция → is_watched) — для watched-отметок ячеек
     * в шторке серий. Своя подписка на каждую загрузку, как у [observeWatched]: старая гасится,
     * иначе смена серии оставляла бы collect за прошлым ключом. Живёт в стейте отдельно от
     * [PlayerUiState.episodes], потому что обновляется сразу после оптимистичной локальной записи
     * (P4.T7), а не по сети.
     */
    private fun observeWatchedPositions(key: LoadKey) {
        watchedPositionsJob?.cancel()
        watchedPositionsJob =
            viewModelScope.launch {
                episodeRepository.observeWatchedPositions(key.releaseId, key.sourceId).collect { positions ->
                    if (loadedKey == key) _uiState.update { it.copy(watchedPositions = positions) }
                }
            }
    }
}

/**
 * Режим отображения, переносимый на СЛЕДУЮЩИЙ экран плеера при смене серии.
 *
 * Смена серии — навигация на новый маршрут (`TitleNavigator.openPlayer` заменяет текущий Player), а
 * значит новый [PlayerViewModel] со стартовым [PlayerUiState.isFullscreen] по умолчанию: смотрел в
 * fullscreen, нажал «Следующая серия» — и плеер выпадал в компактный режим (живая проверка
 * 2026-10-01). [PlayerScreen] кладёт сюда текущий режим прямо перед навигацией, новая ViewModel
 * забирает его один раз при создании. Обычное открытие плеера (из Detail) значения не находит и
 * стартует с платформенного дефолта.
 */
internal object PlayerFullscreenCarry {
    private var pending: Boolean? = null

    fun put(isFullscreen: Boolean) {
        pending = isFullscreen
    }

    fun consume(): Boolean? = pending.also { pending = null }
}

/**
 * Позиции соседних серий `(предыдущая, следующая)` для [currentPosition] в списке [episodes].
 *
 * Список сортируется по `position` — API отдаёт его упорядоченным, но кнопки «пред./след.» не должны
 * зависеть от этого неявного контракта. `null` — текущей позиции в списке нет (тогда у вызывающей
 * стороны остаётся fallback `position ± 1`); внутри пары `null` — соседа с этой стороны нет.
 */
internal fun neighbourPositions(
    episodes: List<Episode>,
    currentPosition: Int,
): Pair<Int?, Int?>? {
    val sorted = episodes.map(Episode::position).distinct().sorted()
    val index = sorted.indexOf(currentPosition)
    if (index < 0) return null
    return sorted.getOrNull(index - 1) to sorted.getOrNull(index + 1)
}

/**
 * Нужно ли (пере)запускать загрузку по запросу с ключом [requestedKey], если предыдущий принятый
 * этой точкой входа ключ — [acceptedKey] (`null` — ещё ничего не принималось), а состояние —
 * [isLoading]/[hasSource].
 *
 * Пропускаем (`false`) только повтор ТОГО ЖЕ ключа, пока источник грузится или уже загружен:
 * - другой ключ (следующая серия) — грузим;
 * - тот же ключ после ошибки (не грузится и источника нет) — грузим, иначе повторный вход на ту же
 *   серию после сбоя молча ничего не делал бы (единственным способом повторить оставалась бы
 *   кнопка «Повторить»).
 *
 * Чистая функция вынесена из [PlayerViewModel] ради теста таблицей случаев: сам ViewModel завязан
 * на конкретный `EpisodeRepository`. Дженерик по ключу — чтобы не светить приватный `LoadKey`.
 */

internal fun <K> shouldStartLoad(
    acceptedKey: K?,
    requestedKey: K,
    isLoading: Boolean,
    hasSource: Boolean,
): Boolean = acceptedKey != requestedKey || !(isLoading || hasSource)

private fun Exception.toPlayerError(): PlayerError {
    val error = this as? AnixError ?: return PlayerError.Generic
    return when (error) {
        is AnixError.Network -> PlayerError.NoConnection
        is AnixError.Unauthorized -> PlayerError.Unauthorized
        is AnixError.PlaybackResolve -> PlayerError.SourceUnavailable(error.host.key)
        else -> PlayerError.Generic
    }
}

/** P16.T7 — минимальная сохранённая позиция, при которой имеет смысл предлагать resume-диалог:
 *  меньше — считается «серия почти не начата», диалог только раздражал бы. */
private const val RESUME_MIN_POSITION_MS = 5_000L

/** P16.T7 — доля длительности, начиная с которой позиция считается «серия досмотрена» — такую
 *  позицию не сохраняем (и стираем прежнюю), resume от неё не имеет смысла. */
private const val RESUME_NEAR_END_PROGRESS = 0.95
