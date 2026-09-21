@file:Suppress("MatchingDeclarationName") // `.desktop.kt` — общепринятый суффикс actual-файла в KMP.

package com.aniko.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * Desktop-реализация моста поверх нативного VLCJ `MediaPlayer` (Step 2/3 пересмотра P8.T1, см.
 * журнал `docs/REELWAVE_PLAN.md`) — заменяет прежний `CefMessageRouter`-мост к `<video>` внутри
 * JCEF-страницы. [DesktopStreamResolver] находит реальный URL потока чистым HTTP-парсингом
 * embed-страницы (Step 3 пересмотра, `feature/desktop-video-player`, см. её KDoc) — на Desktop
 * с этого шага браузерный движок для резолва вообще не нужен, этот класс им никогда и не управлял.
 *
 * **Почему это надёжнее старого JS-моста, а не даунгрейд.** Android/iOS/старый-Desktop читают
 * состояние чужого `<video>` через DOM-события и периодический опрос (`setInterval`, см.
 * [embedBridgeScript]) — единственный доступный им канал, раз видео живёт внутри чужой страницы.
 * Здесь видео — НАШ собственный `MediaPlayer` (libVLC воспроизводит уже резолвнутый прямой поток,
 * а не чужую embed-страницу), поэтому состояние приходит нативными событиями VLCJ
 * ([MediaPlayerEventAdapter] — `playing`/`paused`/`timeChanged`/`lengthChanged`/`rateChanged`),
 * без единого DOM-опроса. `EmbedVideoState` остаётся тем же commonMain-контрактом (не пересмотрен
 * этой веткой) — здесь просто другой источник его полей.
 *
 * [attach]/[detach] вызывает `EmbedPlayerView` (desktop) при создании/уничтожении VLCJ-компонента
 * видео-окна — тот же паттерн, что раньше был с `CefBrowser`/`CefClient` у JCEF-моста.
 *
 * **Потоки (важно).** События libVLC приходят на его нативный поток, а вызывать libVLC оттуда нельзя
 * (vlcj: «вызов неэффективен, возможно странное поведение либо фатальный краш JVM»). Прежняя
 * реализация делала именно это — `setTime`/`setPause` прямо в `playing()`. Теперь всё, что трогает
 * автомат смены качества и (через него) плеер, идёт через `MediaPlayer.submit` — один
 * сериализованный поток (см. [post]); события-«переключатели» (`mediaChanged`/`playing`/`error`)
 * лишь пересылаются туда.
 *
 * Смена качества — целиком в [QualitySwitchCoordinator] (чистый автомат, покрыт тестами); здесь
 * только пересылка событий и отражение его состояния в [EmbedVideoState].
 *
 * `@Suppress("TooManyFunctions")` — размер класса задан контрактом `expect class` + внутренними
 * [attach]/[detach]/[startResolvedStream]/[post]/[onSwitchState] — все обслуживают тот же самый
 * мост, резать по произвольной границе ради счётчика было бы хуже, чем чуть больший класс.
 */
@Suppress("TooManyFunctions")
actual class EmbedVideoController actual constructor() {
    private val stateFlow = MutableStateFlow(EmbedVideoState())

    actual val state: StateFlow<EmbedVideoState> = stateFlow.asStateFlow()

    /** `true` безусловно — тот же смысл, что и у прежнего JCEF-моста (см. историю KDoc): механизм
     *  (VLCJ `MediaPlayer` + нативные события) не зависит от версии ПО пользователя. Работоспособность
     *  конкретного запуска (нашёл ли `DesktopStreamResolver` реальный поток за embed-URL) —
     *  runtime-вопрос `EmbedPlayerView`/`EmbedVideoState.isVideoFound`, не свойство контроллера. */
    actual val isSupported: Boolean = true

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    /** Автомат смены качества текущего [mediaPlayer]; пересоздаётся на каждый [attach]. */
    @Volatile
    private var coordinator: QualitySwitchCoordinator? = null

    /** Таймауты загрузки потока: отдельный scope, чтобы [detach] гарантированно гасил все ожидания. */
    private var timeoutScope: CoroutineScope? = null

    /** «Качество по умолчанию» из настроек ([setPreferredQuality]); `null` — «Авто». */
    @Volatile
    private var preferredQualityHeight: Int? = null

    /** Сколько ждать `playing` нового качества до отката. `internal var` — только ради интеграционного теста
     *  (ждать 15 с в каждом сценарии таймаута незачем); продовый код значение не меняет. */
    internal var qualitySwitchTimeoutMs: Long = QualitySwitchCoordinator.DEFAULT_SWITCH_TIMEOUT_MS

    /** Referer, с которым нужно стримить потоки текущего источника — тот же `Resolved.referer`, что и
     *  у дефолтного потока (Kodik: `https://kodikplayer.com/`, AniLibria: фиксированный домен
     *  бренда), поэтому один на все качества. `null` — Referer не нужен (Sibnet). */
    @Volatile
    private var streamReferer: String? = null

    private val eventListener =
        object : MediaPlayerEventAdapter() {
            // Подтверждение, что libVLC принял именно нашу очередную загрузку: события, пришедшие
            // ДО него, относятся к предыдущему медиа (см. [QualitySwitchCoordinator.onMediaChanged]).
            override fun mediaChanged(
                mp: MediaPlayer,
                media: MediaRef?,
            ) = post(mp) { it.onMediaChanged() }

            // Первый реальный сигнал, что медиа-пайплайн реально ожил (не просто "URL резолвнулся"
            // — резолв может отдать URL, который libVLC не сможет открыть) — тем же смыслом, что и
            // isVideoFound у JS-моста ("есть на что реально смотреть"), но источник другой.
            override fun buffering(
                mp: MediaPlayer,
                newCache: Float,
            ) = stateFlow.update { it.copy(isVideoFound = true) }

            override fun playing(mp: MediaPlayer) {
                post(mp) { it.onPlaying() }
                updateUnlessSwitching { it.copy(isVideoFound = true, isPlaying = true) }
            }

            override fun paused(mp: MediaPlayer) = updateUnlessSwitching { it.copy(isPlaying = false) }

            override fun stopped(mp: MediaPlayer) = updateUnlessSwitching { it.copy(isPlaying = false) }

            override fun finished(mp: MediaPlayer) = updateUnlessSwitching { it.copy(isPlaying = false) }

            // Единственный сигнал, что libVLC ПОЛУЧИЛ резолвнутый URL, но не смог его реально
            // проиграть (неподдерживаемый формат/403 без верного Referer/битая ссылка,
            // истёкшая на середине CDN-сессия) — отдельный провал от "резолв не нашёл URL вовсе"
            // (там `isVideoFound` просто никогда не станет `true`, см. `EmbedPlayer.desktop.kt`).
            // Если идёт смена качества — это провал НОВОГО потока: автомат откатится на прежнее
            // качество, плеер «мёртвым» не объявляется. Иначе — откат `isVideoFound` в `false`, тот же
            // fallback, что и у полностью неудачного резолва: `PlayerOverlay` (commonMain) деградирует
            // до одной кнопки "назад" вместо полноценных контролов над мёртвым плеером.
            override fun error(mp: MediaPlayer) = post(mp) { if (!it.onError()) markPlayerDead() }

            override fun timeChanged(
                mp: MediaPlayer,
                newTime: Long,
            ) = updateUnlessSwitching { it.copy(currentTimeMs = newTime.coerceAtLeast(0L)) }

            // 0/отрицательное — переходное состояние между сериями/до готовности медиа, не настоящая
            // нулевая длительность (тот же смысл, что и "duration = NaN" у JS-моста, см. её KDoc).
            override fun lengthChanged(
                mp: MediaPlayer,
                newLength: Long,
            ) = updateUnlessSwitching { it.copy(durationMs = newLength.takeIf { length -> length > 0L }) }

            // vlcj 4.11.0's MediaPlayerEventListener не даёт отдельного rateChanged-события (в
            // отличие от JS-моста, где `ratechange` — реальное DOM-событие `<video>`) — скорость
            // обновляется прямо в [setPlaybackRate] по результату `ControlsApi.setRate`.
        }

    actual fun setExpectedSource(embedUrl: String) {
        streamReferer = null
        mediaPlayer?.let { player -> post(player) { it.reset() } }
        stateFlow.value = EmbedVideoState()
    }

    actual fun setPreferredQuality(heightPx: Int?) {
        preferredQualityHeight = heightPx
    }

    /**
     * Принимает итог резолва [DesktopStreamResolver] от `EmbedPlayerView` (desktop) и ЗАПУСКАЕТ поток:
     *  публикует качества в [EmbedVideoState] — именно этот список видит чип качества [PlayerScreen]
     *  (`availableQualities` у других платформ заполняет JS-мост из меню хоста, здесь меню нет —
     *  резолвер отдал список сам) — и стартует поток, выбранный по «качеству по умолчанию»
     *  ([setPreferredQuality]): точное/ближайшее нижнее/ближайшее верхнее из
     *  [DesktopStreamResolver.Resolved.qualityStreams]; «Авто» либо источник без списка качеств
     *  (Sibnet) — умолчание резолвера ([DesktopStreamResolver.Resolved.streamUrl]). Если
     *  предпочтительный поток не запустится, автомат откатится на умолчание резолвера.
     *
     * Текущее качество выводится сопоставлением `streamUrl` с URL-значениями map (дефолтный поток
     *  резолвера — всегда один из кандидатов, см. KDoc [KodikDirectLinkResolver.resolve]); совпадения
     *  нет только если хост отдал качества отдельно от дефолтного URL — тогда предпочтение не
     *  применяется (откатываться было бы некуда), играет умолчание резолвера, `currentQuality` — `null`.
     */
    internal fun startResolvedStream(resolved: DesktopStreamResolver.Resolved) {
        val player = mediaPlayer ?: return
        streamReferer = resolved.referer
        val streams = resolved.qualityStreams
        val defaultQuality = streams.entries.firstOrNull { (_, url) -> url == resolved.streamUrl }?.key
        if (streams.isNotEmpty()) {
            stateFlow.update { it.copy(availableQualities = streams.keys.toList(), currentQuality = defaultQuality) }
        }
        val preferred = pickQualityForPreference(preferredQualityHeight, streams.keys)
        post(player) { switcher ->
            if (defaultQuality != null) {
                switcher.startPlayback(streams, defaultQuality, preferred)
            } else {
                playDirect(player, resolved.streamUrl)
            }
        }
    }

    /** Запуск потока без списка качеств (Sibnet / умолчание без совпадения в map) — как раньше, без автомата. */
    private fun playDirect(
        player: MediaPlayer,
        url: String,
    ) {
        val referer = streamReferer
        if (referer != null) {
            player.media().play(url, ":http-referrer=$referer")
        } else {
            player.media().play(url)
        }
    }

    /** Вызывается `EmbedPlayerView` (desktop) один раз при создании VLCJ video-окна — до первого
     *  `media().play(...)`, чтобы ни одно событие не потерялось. */
    internal fun attach(player: MediaPlayer) {
        detach()
        mediaPlayer = player
        // InjectDispatcher: контроллер создаётся composable-функцией `rememberEmbedVideoController`,
        // а не Koin — внедрять диспетчер некуда; scope нужен только чтобы отложенно перепostить
        // таймаут обратно на поток плеера, конкурентной работы на нём нет.
        @Suppress("InjectDispatcher")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        timeoutScope = scope
        val engine =
            VlcQualitySwitchEngine(
                player = player,
                referer = { streamReferer },
                currentTimeMs = { stateFlow.value.currentTimeMs },
                scheduleTimeout = { token, delayMs ->
                    scope.launch {
                        delay(delayMs)
                        post(player) { it.onTimeout(token) }
                    }
                },
            )
        coordinator = QualitySwitchCoordinator(engine, ::onSwitchState, qualitySwitchTimeoutMs)
        player.events().addMediaPlayerEventListener(eventListener)
    }

    /** Снимает мост с текущего `MediaPlayer` (dispose видео-окна/смена источника) — идемпотентна. */
    internal fun detach() {
        val player = mediaPlayer
        mediaPlayer = null
        coordinator = null
        timeoutScope?.cancel()
        timeoutScope = null
        if (player != null) {
            runCatching { player.events().removeMediaPlayerEventListener(eventListener) }
        }
        streamReferer = null
        stateFlow.value = EmbedVideoState()
    }

    actual fun release() = detach()

    actual fun play() {
        mediaPlayer?.controls()?.play()
    }

    actual fun pause() {
        mediaPlayer?.controls()?.setPause(true)
    }

    actual fun togglePlayPause() {
        if (stateFlow.value.isPlaying) pause() else play()
    }

    actual fun seekTo(positionMs: Long) {
        mediaPlayer?.controls()?.setTime(positionMs.coerceAtLeast(0L))
    }

    actual fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        val target = (player.status().time() + deltaMs).coerceAtLeast(0L)
        player.controls().setTime(target)
    }

    actual fun setPlaybackRate(rate: Float) {
        val applied = mediaPlayer?.controls()?.setRate(rate) ?: false
        // Нет отдельного nativeRateChanged-события (см. комментарий у [eventListener]) — состояние
        // обновляется по результату самого вызова, а не по последующему колбэку.
        if (applied) stateFlow.update { it.copy(playbackRate = rate) }
    }

    /**
     * Переключение качества на Desktop: у libVLC нет клиентского quality-dropdown чужого хоста,
     *  которым управлял бы старый JS-мост ([embedBridgeScript].switchQuality) — поэтому переключаем
     *  САМИ: [DesktopStreamResolver] отдал отдельный playable URL на каждое качество (прозондированные —
     *  мёртвых вариантов там нет), выбор пользователя = перезапуск потока на новом URL с тем же
     *  `:http-referrer=`, что у дефолтного.
     *
     * Сама политика (снимок позиции/паузы/скорости/громкости ДО смены, старт нового потока сразу с той
     *  же позиции и на паузе, если пользователь был на паузе, защита от гонок при быстром повторном
     *  выборе, откат при ошибке/таймауте) — в [QualitySwitchCoordinator]; здесь запрос лишь уходит на
     *  сериализованный поток плеера. Безопасный no-op там, где переключать нечего: неизвестное качество,
     *  оно уже играет, плеер не приаттачен, качества не присланы (Sibnet/резолв ещё идёт).
     *
     * Ручной выбор действует на текущий источник и на настройку «по умолчанию» НЕ влияет.
     */
    actual fun setQuality(quality: String) {
        val player = mediaPlayer ?: return
        post(player) { it.request(quality) }
    }

    /**
     * Ставит [task] на сериализованный поток [player] (`MediaPlayer.submit`) — единственное место,
     * откуда автомат/плеер вызываются из событий libVLC и с UI. Задача тихо отбрасывается, если к
     * моменту исполнения плеер уже отцеплен ([detach]) — иначе очередь, которую vlcj дорабатывает при
     * `release()`, дёргала бы уже уничтожаемый нативный плеер.
     */
    private fun post(
        player: MediaPlayer,
        task: (QualitySwitchCoordinator) -> Unit,
    ) {
        val target = coordinator ?: return
        runCatching {
            player.submit {
                if (mediaPlayer === player && coordinator === target) runCatching { task(target) }
            }
        }
    }

    /**
     * События старого потока во время смены качества не должны трогать UI-состояние
     * (см. [EmbedVideoState.switchingQualityTo]).
     */
    private fun updateUnlessSwitching(transform: (EmbedVideoState) -> EmbedVideoState) {
        stateFlow.update { if (it.switchingQualityTo != null) it else transform(it) }
    }

    /** Отражает состояние автомата смены качества в [EmbedVideoState]. Вызывается на потоке плеера. */
    private fun onSwitchState(next: QualitySwitchState) {
        val finished = next.switchingTo == null && stateFlow.value.switchingQualityTo != null
        // Длительность нового медиа: `lengthChanged` во время смены игнорировался (см. updateUnlessSwitching).
        val length = if (finished) runCatching { mediaPlayer?.status()?.length() }.getOrNull() else null
        stateFlow.update { current ->
            val updated =
                current.copy(
                    currentQuality = next.currentQuality,
                    switchingQualityTo = next.switchingTo,
                    qualitySwitchFailure = next.failure,
                )
            when {
                next.failure?.restoredTo == null && next.failure != null ->
                    updated.copy(isVideoFound = false, isPlaying = false)
                length != null && length > 0L -> updated.copy(durationMs = length)
                else -> updated
            }
        }
    }

    private fun markPlayerDead() {
        stateFlow.update { it.copy(isVideoFound = false, isPlaying = false, switchingQualityTo = null) }
    }
}
