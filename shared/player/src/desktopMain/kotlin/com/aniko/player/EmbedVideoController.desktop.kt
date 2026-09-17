@file:Suppress("MatchingDeclarationName") // `.desktop.kt` — общепринятый суффикс actual-файла в KMP.

package com.aniko.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * `@Suppress("TooManyFunctions")` — как и на прежнем JCEF-мосте: размер класса задан контрактом
 * `expect class` + внутренние [attach]/[detach]/[onStreamsResolved] (вызывает `EmbedPlayerView`
 * при ресолве источника) + приватный [playStream] (единая точка запуска потока для
 * [setQuality]) — все обслуживают тот же самый мост, резать по произвольной границе ради
 * счётчика было бы хуже, чем чуть больший класс.
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

    /** Качества текущего источника (лейбл «720p» → URL), присланные `EmbedPlayerView` через
     *  [onStreamsResolved] после [DesktopStreamResolver] — единственный источник правды для
     *  переключения качества на Desktop (у libVLC нет клиентского dropdown хоста, поэтому
     *  переключаем САМИ, перезапуская поток; см. KDoc [setQuality]). Пустая map — хост отдал
     *  одно качество (Sibnet) или резолв ещё не завершился. */
    @Volatile
    private var qualityStreams: Map<String, String> = emptyMap()

    /** Referer, с которым нужно стримить потоки из [qualityStreams] — тот же `Resolved.referer`,
     *  что и у дефолтного потока (Kodik: `https://kodikplayer.com/`, AniLibria: фиксированный
     *  домен бренда), поэтому один на все качества. `null` — Referer не нужен (Sibnet, но у него
     *  и качеств нет; поле на будущее, см. KDoc [setQuality]). */
    @Volatile
    private var streamReferer: String? = null

    /** URL потока, который реально играет сейчас — чтобы [setQuality] не перезапускал поток,
     *  если выбрано качество, которое и так играет (опция уже выбрана в пикере). */
    @Volatile
    private var lastPlayedUrl: String? = null

    /** Позиция (мс), на которую откатываем воспроизведение после переключения качества: читаем
     *  ДО `media().play(newUrl)` (после него `status().time()` уже про новый поток), применяем
     *  в `playing` нового медиа (см. [eventListener]) — до этого момента seek не имеет смысла:
     *  длительность нового потока ещё не распарсена. `null`/0 — начать с начала. */
    @Volatile
    private var pendingSeekAfterSwitchMs: Long? = null

    /** `true` — пользователь был на паузе в момент переключения качества: новый поток стартует
     *  с `media().play()` (он всегда начинает играть), поэтому паузу восстанавливаем по первому
     *  `playing` нового медиа, а не мгновенно (setPause до готовности медиа молча теряется). */
    @Volatile
    private var pendingPauseAfterSwitch: Boolean = false

    private val eventListener =
        object : MediaPlayerEventAdapter() {
            // Первый реальный сигнал, что медиа-пайплайн реально ожил (не просто "URL резолвнулся"
            // — резолв может отдать URL, который libVLC не сможет открыть) — тем же смыслом, что и
            // isVideoFound у JS-моста ("есть на что реально смотреть"), но источник другой.
            override fun buffering(
                mp: MediaPlayer,
                newCache: Float,
            ) = stateFlow.update { it.copy(isVideoFound = true) }

            override fun playing(mp: MediaPlayer) {
                // Переключение качества: позиция/пауза восстанавливаются по ПЕРВОМУ `playing`
                // нового медиа — см. KDoc [pendingSeekAfterSwitchMs]/[pendingPauseAfterSwitch].
                // На обычном resume после паузы оба флага `null`/`false` — ветка ничего не делает.
                val resumeMs = pendingSeekAfterSwitchMs
                pendingSeekAfterSwitchMs = null
                if (resumeMs != null && resumeMs > 0L) {
                    runCatching { mp.controls().setTime(resumeMs) }
                }
                if (pendingPauseAfterSwitch) {
                    pendingPauseAfterSwitch = false
                    runCatching { mp.controls().setPause(true) }
                }
                stateFlow.update { it.copy(isVideoFound = true, isPlaying = true) }
            }

            override fun paused(mp: MediaPlayer) = stateFlow.update { it.copy(isPlaying = false) }

            override fun stopped(mp: MediaPlayer) = stateFlow.update { it.copy(isPlaying = false) }

            override fun finished(mp: MediaPlayer) = stateFlow.update { it.copy(isPlaying = false) }

            // Единственный сигнал, что libVLC ПОЛУЧИЛ резолвнутый URL, но не смог его реально
            // проиграть (неподдерживаемый формат/403 без верного Referer/битая ссылка,
            // истёкшая на середине CDN-сессия) — отдельный провал от "резолв не нашёл URL вовсе"
            // (там `isVideoFound` просто никогда не станет `true`, см. `EmbedPlayer.desktop.kt`).
            // Откат `isVideoFound` в `false` — тот же fallback, что и у полностью неудачного
            // резолва: `PlayerOverlay` (commonMain) деградирует до одной кнопки "назад" вместо
            // того, чтобы держать на экране полноценные контролы над мёртвым плеером.
            override fun error(mp: MediaPlayer) = stateFlow.update { it.copy(isVideoFound = false, isPlaying = false) }

            override fun timeChanged(
                mp: MediaPlayer,
                newTime: Long,
            ) = stateFlow.update { it.copy(currentTimeMs = newTime.coerceAtLeast(0L)) }

            // 0/отрицательное — переходное состояние между сериями/до готовности медиа, не настоящая
            // нулевая длительность (тот же смысл, что и "duration = NaN" у JS-моста, см. её KDoc).
            override fun lengthChanged(
                mp: MediaPlayer,
                newLength: Long,
            ) = stateFlow.update { it.copy(durationMs = newLength.takeIf { length -> length > 0L }) }

            // vlcj 4.11.0's MediaPlayerEventListener не даёт отдельного rateChanged-события (в
            // отличие от JS-моста, где `ratechange` — реальное DOM-событие `<video>`) — скорость
            // обновляется прямо в [setPlaybackRate] по результату `ControlsApi.setRate`.
        }

    actual fun setExpectedSource(embedUrl: String) {
        qualityStreams = emptyMap()
        streamReferer = null
        lastPlayedUrl = null
        pendingSeekAfterSwitchMs = null
        pendingPauseAfterSwitch = false
        stateFlow.value = EmbedVideoState()
    }

    /**
     * Принимает итог резолва [DesktopStreamResolver] от `EmbedPlayerView` (desktop): запоминает
     *  [DesktopStreamResolver.Resolved.qualityStreams]/`referer` для будущих [setQuality] и
     *  публикует качества в [EmbedVideoState] — именно этот список видит чип качества
     *  [PlayerScreen] (`availableQualities` у других платформ заполняет JS-мост из меню хоста,
     *  здесь меню нет — резолвер отдал список сам).
     *
     * Текущее качество выводится сопоставлением `streamUrl` с URL-значениями map (дефолтный
     *  поток резолвера — всегда один из кандидатов, см. KDoc [KodikDirectLinkResolver.resolve]);
     *  совпадения нет только если хост отдал качества отдельно от дефолтного URL — тогда
     *  `currentQuality` остаётся `null`, и чип показывает первый доступный вариант по договорён-
     *  ности UI (лейбл чипа — не источник истины, переключение идёт по [setQuality]).
     */
    internal fun onStreamsResolved(resolved: DesktopStreamResolver.Resolved) {
        qualityStreams = resolved.qualityStreams
        streamReferer = resolved.referer
        lastPlayedUrl = resolved.streamUrl
        if (resolved.qualityStreams.isNotEmpty()) {
            val current =
                resolved.qualityStreams.entries
                    .firstOrNull { (_, url) -> url == resolved.streamUrl }
                    ?.key
            stateFlow.update {
                it.copy(availableQualities = resolved.qualityStreams.keys.toList(), currentQuality = current)
            }
        }
    }

    /** Вызывается `EmbedPlayerView` (desktop) один раз при создании VLCJ video-окна — до первого
     *  `media().play(...)`, чтобы ни одно событие не потерялось. */
    internal fun attach(player: MediaPlayer) {
        detach()
        mediaPlayer = player
        player.events().addMediaPlayerEventListener(eventListener)
    }

    /** Снимает мост с текущего `MediaPlayer` (dispose видео-окна/смена источника) — идемпотентна. */
    internal fun detach() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.events().removeMediaPlayerEventListener(eventListener) }
        }
        qualityStreams = emptyMap()
        streamReferer = null
        lastPlayedUrl = null
        pendingSeekAfterSwitchMs = null
        pendingPauseAfterSwitch = false
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
     * Переключение качества на Desktop (заменяет прежний CUT этой ветки): у libVLC нет
     *  клиентского quality-dropdown чужого хоста, которым управлял бы старый JS-мост
     *  ([embedBridgeScript].switchQuality) — поэтому переключаем САМИ: [DesktopStreamResolver]
     *  отдал отдельный playable URL на каждое качество ([qualityStreams], прозондированные —
     *  мёртвых вариантов там нет), и выбор пользователя = перезапуск потока на новом URL с
     *  тем же `:http-referrer=`, что у дефолтного (см. KDoc [streamReferer]).
     *
     * Сохранение позиции: текущую позицию читаем ДО перезапуска (`status().time()`), применяем
     *  по первому `playing` нового медиа ([pendingSeekAfterSwitchMs]) — seek до готовности
     *  нового потока не имеет смысла (длительность ещё не распарсена, setTime потерялся бы).
     *  Состояние паузы восстанавливается так же ([pendingPauseAfterSwitch]): `media().play()`
     *  всегда начинает играть, и если пользователь был на паузе, новый поток останавливаем
     *  по первому `playing`. Пользователю виден мгновенный seek при старте нового качества —
     *  тот же UX, что даёт переключение качества в WebView-плеере хоста.
     *
     * Безопасный no-op остаётся там, где переключать нечего: неизвестное [quality] (не из
     *  [qualityStreams]), [quality] уже играет ([lastPlayedUrl]), плеер не приаттачен,
     *  качества не присланы (Sibnet/резолв ещё идёт) — честный no-op, как и раньше, UI это
     *  предусматривает (чип качества не рисуется при пустом списке, см. KDoc [PlayerScreen]).
     */
    actual fun setQuality(quality: String) {
        val player = mediaPlayer ?: return
        val url = qualityStreams[quality]
        // null — качество не из списка/список пуст (Sibnet, резолв ещё идёт); equals lastPlayedUrl —
        // это качество уже играет (опция уже выбрана в пикере). Оба случая — честный no-op.
        if (url == null || url == lastPlayedUrl) return
        pendingSeekAfterSwitchMs = player.status().time().coerceAtLeast(0L)
        pendingPauseAfterSwitch = !stateFlow.value.isPlaying
        playStream(player, url)
    }

    /**
     * Запускает URL потока на приаттаченном плеере с сохранённым referer и обновляет
     *  [EmbedVideoState.currentQuality] под фактически играющий вариант — единая точка запуска
     *  для [setQuality] (первоначальный запуск дефолтного потока делает сам `EmbedPlayerView`,
     *  см. `EmbedPlayer.desktop.kt` — ему и `onStreamsResolved` шлёт).
     */
    private fun playStream(
        player: MediaPlayer,
        url: String,
    ) {
        val referer = streamReferer
        if (referer != null) {
            player.media().play(url, ":http-referrer=$referer")
        } else {
            player.media().play(url)
        }
        lastPlayedUrl = url
        stateFlow.update { state ->
            state.copy(currentQuality = qualityStreams.entries.firstOrNull { (_, stream) -> stream == url }?.key)
        }
    }
}
