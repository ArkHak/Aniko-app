package com.aniko.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

/**
 * Снимок `<video>`, живущего внутри чужой embed-страницы.
 *
 * Это НЕ [PlaybackState]: там — состояние нативного плеера ([PlayerController], задел на
 * будущее), здесь — то, что удалось вычитать из DOM чужой страницы через JS-мост.
 *
 * @param isVideoFound элемент `<video>` найден в подфрейме видеохоста. Пока `false`, любые
 * команды уходят в никуда, а оверлей плеера показывать нечего.
 * @param durationMs `null`, пока не пришло событие `loadedmetadata`: до него `duration`
 * у элемента равен `NaN` (подтверждено спайком). Прогресс-бар до этого момента включать нельзя.
 */
data class EmbedVideoState(
    val isVideoFound: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTimeMs: Long = 0L,
    val durationMs: Long? = null,
    val playbackRate: Float = 1f,
    /** Качества, которые объявляет хост-плеер (порядок хоста), напр. `360p,480p,720p`. */
    val availableQualities: List<String> = emptyList(),
    /** Текущее качество по данным хоста, если он его показывает. */
    val currentQuality: String? = null,
)

/**
 * Управление `<video>` внутри embed-страницы через JS-мост.
 *
 * Что мост даёт и чего не даёт (закрыто живой проверкой Фазы 8, не переисследовать):
 * - **даёт** play/pause, чтение и запись `currentTime` (seek), чтение `duration`,
 *   чтение и запись `playbackRate` — всё проверено вживую на реальном воспроизведении;
 * - **не даёт** качество, субтитры и аудиодорожку: это внутренний UI чужого плеера,
 *   единого DOM-контракта под ним нет (см. «находку №1» в `docs/REELWAVE_PLAN.md`).
 *
 * Платформы:
 * - Android — `androidx.webkit` (`addDocumentStartJavaScript` + `addWebMessageListener`);
 * - iOS — `WKUserScript(forMainFrameOnly = false)` + `WKScriptMessageHandler`;
 * - Desktop — **не поддержан** ([isSupported] == `false`), все методы no-op: там нет
 *   видео-поверхности под контролем приложения, видео играет в системном браузере
 *   (P8.T1, см. `EmbedPlayer.desktop.kt`).
 *
 * Создавать напрямую не нужно — есть [rememberEmbedVideoController].
 */
expect class EmbedVideoController() {
    /**
     * `false` — платформа без JS-моста (Desktop) либо системный WebView без нужных фич
     * `androidx.webkit`. UI обязан проверять это перед отрисовкой оверлея: при `false`
     * состояние навсегда останется дефолтным, а команды ничего не сделают.
     */
    val isSupported: Boolean

    /** Состояние видео. Обновляется от DOM-событий элемента, а не от резолва промисов. */
    val state: StateFlow<EmbedVideoState>

    /**
     * Сообщает контроллеру, какой embed-URL сейчас грузится: от него берётся домен, по которому
     * фильтруются сообщения из фреймов (в странице их много, включая рекламные). Сбрасывает
     * [state] и забывает предыдущий видео-фрейм.
     */
    fun setExpectedSource(embedUrl: String)

    fun play()

    fun pause()

    fun togglePlayPause()

    /** Абсолютный seek. Значение клампится в JS: отрицательное превратится в 0. */
    fun seekTo(positionMs: Long)

    /** Относительный seek (для тап-зон «−10с / +10с»); [deltaMs] может быть отрицательной. */
    fun seekBy(deltaMs: Long)

    /** Скорость воспроизведения, 1.0 — обычная. Фактический результат придёт событием `ratechange`. */
    fun setPlaybackRate(rate: Float)

    /**
     * Просит плеер хоста переключить качество видео (P16-фикс 2026-09-09). Поддерживается
     * там, где хост даёт клиентское переключение (Kodik/flowplayer — его quality-dropdown);
     * на хостах без такого UI (Sibnet/VideoJS без плагина уровней) — безопасный no-op.
     * [quality] — отображаемое имя («720p»/«480p»).
     */
    fun setQuality(quality: String)

    /** Снимает мост с WebView. Вызывается из [rememberEmbedVideoController], вручную не нужен. */
    fun release()
}

/**
 * Контроллер, привязанный к жизненному циклу композиции.
 *
 * Инстанс намеренно НЕ пересоздаётся при смене [embedUrl] (`remember` без ключа): мост на
 * Android ставится один раз в `AndroidView.factory` — до первой загрузки страницы, иначе
 * `addDocumentStartJavaScript` уже не успеет отработать, — а на iOS вообще прописывается
 * в `WKWebViewConfiguration` до создания `WKWebView`. Смена серии меняет только ожидаемый
 * домен через [EmbedVideoController.setExpectedSource].
 *
 * Результат передаётся в [EmbedPlayerView] параметром `controller`.
 */
@Composable
fun rememberEmbedVideoController(embedUrl: String): EmbedVideoController {
    val controller = remember { EmbedVideoController() }
    // Намеренно в `remember`, а не в `SideEffect`/`LaunchedEffect`: смена ожидаемого домена
    // должна произойти ДО того, как `AndroidView.update`/`WKWebView` начнут грузить новый URL,
    // иначе первые сообщения новой страницы отфильтруются по домену предыдущей.
    remember(controller, embedUrl) {
        controller.setExpectedSource(embedUrl)
        embedUrl
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
