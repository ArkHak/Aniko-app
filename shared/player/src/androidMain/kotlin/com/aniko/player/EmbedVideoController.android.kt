package com.aniko.player

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-реализация моста поверх `androidx.webkit`.
 *
 * Почему именно `androidx.webkit`, а не голый `android.webkit`:
 * `WebView.evaluateJavascript` исполняется ТОЛЬКО в главном фрейме, а `<video>` у всех
 * проверенных хостов лежит в cross-origin подфрейме — до него так не дотянуться (проверено
 * вживую на эмуляторе). Нужны две фичи, которых в платформенном API нет:
 * - [WebViewFeature.DOCUMENT_START_SCRIPT] — инжект скрипта в каждый фрейм до его собственных
 *   скриптов;
 * - [WebViewFeature.WEB_MESSAGE_LISTENER] — двусторонний канал: JS → Kotlin через
 *   `onPostMessage`, Kotlin → JS через [JavaScriptReplyProxy] того же фрейма.
 *
 * Обе фичи зависят от версии установленного на устройстве WebView, а не от `minSdk`, поэтому
 * проверяются в рантайме — при их отсутствии контроллер честно рапортует
 * [isSupported] == `false`, а не падает.
 *
 * `@Suppress("TooManyFunctions")` — размер класса задан контрактом `expect class` (8 методов) +
 * 4 внутренних (`attach`/`detach`/`onBridgeMessage`/`send`), обслуживающих мост; резать эту
 * реализацию на части ради порога значило бы прятать логику, а не упрощать её.
 */
@Suppress("TooManyFunctions")
actual class EmbedVideoController actual constructor() {
    private val stateFlow = MutableStateFlow(EmbedVideoState())

    actual val state: StateFlow<EmbedVideoState> = stateFlow.asStateFlow()

    actual val isSupported: Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    private var originFilter: EmbedOriginFilter? = null
    private var webView: WebView? = null
    private var scriptHandler: ScriptHandler? = null

    /**
     * Прокси именно того фрейма, который отрапортовал найденный `<video>`.
     *
     * Скрипт получают все фреймы страницы, включая рекламные и аналитические (спайк вживую
     * поймал `mc.yandex.ru`), поэтому адресатом команд назначается не «последний, кто написал»,
     * а только фрейм, прошедший [EmbedOriginFilter] и сообщивший `isVideoFound = true`.
     */
    private var videoFrame: JavaScriptReplyProxy? = null

    /**
     * Фреймы хоста, приславшие сообщение моста, пока `<video>` ещё не найден. Команда `play` до
     * первого старта обязана дойти хоть куда-то: раньше [send] молча её терял (адресат был только
     * [videoFrame]), и наша кнопка ▶ не запускала Kodik — работал лишь тап по его собственной
     * кнопке. Мост в любом из этих фреймов умеет стартовать хост (см. `startHostPlayer`).
     */
    private val candidateFrames = LinkedHashSet<JavaScriptReplyProxy>()

    /** Применяет «качество по умолчанию» из настроек к меню хоста — один раз на источник. */
    private val qualityApplier = PreferredQualityApplier()

    private val messageListener =
        WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, replyProxy ->
            onBridgeMessage(sourceOrigin, message, replyProxy)
        }

    actual fun setExpectedSource(embedUrl: String) {
        originFilter = EmbedOriginFilter(embedUrl)
        videoFrame = null
        candidateFrames.clear()
        qualityApplier.reset()
        stateFlow.value = EmbedVideoState()
    }

    actual fun setPreferredQuality(heightPx: Int?) = qualityApplier.setPreferred(heightPx)

    /**
     * Ставит мост на [target]. Обязан быть вызван ДО первой загрузки страницы: скрипт
     * document-start применяется к навигациям, начатым после его регистрации.
     *
     * `allowedOriginRules = ["*"]` — намеренно: ограничить инъекцию доменом из ссылки нельзя,
     * не рискуя промахнуться мимо фрейма с видео (хосты редиректят на соседние домены и CDN,
     * а у Kodik страница и плеер вообще из разных доменов группы). Безопасность даёт не правило
     * инъекции, а проверка `sourceOrigin` в [onBridgeMessage]: origin приходит от самого WebView
     * и подделать его страница не может, а команды уходят только в уже принятый фрейм.
     */
    internal fun attach(target: WebView) {
        if (!isSupported || webView === target) return
        detach()
        val installed =
            runCatching {
                WebViewCompat.addWebMessageListener(target, EMBED_BRIDGE_CHANNEL, ALLOWED_ORIGIN_RULES, messageListener)
                scriptHandler =
                    WebViewCompat.addDocumentStartJavaScript(target, embedBridgeScript(), ALLOWED_ORIGIN_RULES)
            }.isSuccess
        webView = if (installed) target else null
    }

    internal fun detach() {
        val attached = webView ?: return
        webView = null
        videoFrame = null
        candidateFrames.clear()
        runCatching { scriptHandler?.remove() }
        scriptHandler = null
        runCatching { WebViewCompat.removeWebMessageListener(attached, EMBED_BRIDGE_CHANNEL) }
        stateFlow.value = EmbedVideoState()
    }

    actual fun release() = detach()

    /** Страница источника не загрузилась — см. [PlaybackEngineProblem.SourceUnavailable]. */
    internal fun reportEngineProblem(problem: PlaybackEngineProblem) {
        stateFlow.value = stateFlow.value.copy(engineProblem = problem)
    }

    actual fun play() = send(EmbedVideoCommand.PLAY)

    actual fun pause() = send(EmbedVideoCommand.PAUSE)

    actual fun togglePlayPause() {
        if (stateFlow.value.isPlaying) pause() else play()
    }

    actual fun seekTo(positionMs: Long) = send(EmbedVideoCommand.seek(positionMs))

    actual fun seekBy(deltaMs: Long) = send(EmbedVideoCommand.seekBy(deltaMs))

    actual fun setPlaybackRate(rate: Float) = send(EmbedVideoCommand.rate(rate))

    actual fun setQuality(quality: String) = send(EmbedVideoCommand.quality(quality))

    // detekt здесь ошибочно считает код после guard-условия недостижимым — судя по всему, не может
    // разрешить тип `EmbedOriginFilter.accepts` (internal-класс из commonMain) при анализе
    // androidMain и считает `originFilter?.accepts(...) != true` тождественно истинным.
    // Реальный компилятор (`:composeApp:assembleDebug`) этой веткой довлен, юнит-тест на парсинг
    // сообщения моста (`EmbedVideoBridgeTest`) зелёный — код действительно достижим и работает.
    @Suppress("UnreachableCode")
    private fun onBridgeMessage(
        sourceOrigin: Uri,
        message: WebMessageCompat,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (originFilter?.accepts(sourceOrigin.toString()) != true) return
        // `WebMessageCompat.getData()` бросает, если сообщение пришло не строкой (ArrayBuffer).
        val data = runCatching { message.data }.getOrNull() ?: return
        // Сообщения, не являющиеся state-апдейтом (отладочный отчёт о видимом chrome — см.
        // parseEmbedChromeDebug), состояния не меняют, только логируются.
        // Сообщённая страницей ошибка загрузки (engineProblem) переживает апдейты моста: на странице
        // ошибки хоста мост тоже работает и шлёт «видео не найдено», затирая бы флаг.
        val parsed = parseEmbedVideoState(data)?.copy(engineProblem = stateFlow.value.engineProblem)
        if (parsed == null) {
            reportChromeDebug(data)
        } else if (parsed.isVideoFound) {
            videoFrame = replyProxy
            stateFlow.value = parsed
            // Видео найдено и меню качеств известно — единственный момент, когда клик по пункту меню
            // хоста имеет смысл; применяется один раз на источник (см. PreferredQualityApplier).
            qualityApplier.onState(parsed)?.let(::setQuality)
        } else if (videoFrame == null) {
            // Второй фрейм того же домена без видео не должен затирать состояние настоящего.
            if (candidateFrames.size < MAX_CANDIDATE_FRAMES) candidateFrames += replyProxy
            stateFlow.value = parsed
        }
    }

    /** Логирует отладочный список видимых chrome-элементов хоста (включается `window.__anikoDebugChrome`). */
    private fun reportChromeDebug(raw: String) {
        val entries = parseEmbedChromeDebug(raw)
        if (entries.isNotEmpty()) Log.d(TAG, "chrome over video: ${entries.joinToString(" ; ")}")
    }

    /**
     * `JavaScriptReplyProxy.postMessage` обязан вызываться на UI-потоке WebView — команды
     * приходят из Compose (тоже UI-поток), но `post` снимает вопрос порядка относительно
     * ещё не завершённой навигации.
     */
    private fun send(command: String) {
        val proxies = videoFrame?.let(::listOf) ?: candidateFrames.toList()
        if (proxies.isEmpty()) return
        val deliver = { proxies.forEach { proxy -> runCatching { proxy.postMessage(command) } } }
        val target = webView
        if (target != null) target.post { deliver() } else deliver()
    }

    private companion object {
        val ALLOWED_ORIGIN_RULES = setOf("*")

        const val TAG = "EmbedVideoController"

        /** Ограничение на случай страницы с десятками фреймов того же домена. */
        const val MAX_CANDIDATE_FRAMES = 8
    }
}
