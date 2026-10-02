package com.aniko.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSLog
import platform.WebKit.WKContentWorld
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * iOS-реализация моста поверх `WKWebView`.
 *
 * Ключевой параметр — `forMainFrameOnly = false` у [WKUserScript]: без него скрипт получает
 * только главный документ, а `<video>` у всех проверенных хостов лежит в cross-origin подфрейме
 * (ровно тот же провал, что и с `evaluateJavaScript` без `inFrame:` — см. `EmbedVideoBridge.kt`).
 *
 * Направления:
 * - JS → Kotlin: `window.webkit.messageHandlers.<CHANNEL>.postMessage(...)` →
 *   [WKScriptMessageHandlerProtocol]. Origin фрейма-отправителя берётся из
 *   `WKScriptMessage.frameInfo.securityOrigin` — его выдаёт сам WebKit, страница подделать
 *   его не может;
 * - Kotlin → JS: `evaluateJavaScript(inFrame:)` с сохранённым [WKFrameInfo] того фрейма,
 *   который отрапортовал найденное видео. Обычный `evaluateJavaScript` без `inFrame:` до
 *   подфрейма не достаёт.
 *
 * И скрипт, и обработчик сообщений живут на [WKWebViewConfiguration], а её нельзя менять после
 * создания `WKWebView` — отсюда [install], вызываемый из фабрики [EmbedPlayerView] до
 * конструктора web view.
 */
actual class EmbedVideoController actual constructor() {
    private val stateFlow = MutableStateFlow(EmbedVideoState())

    actual val state: StateFlow<EmbedVideoState> = stateFlow.asStateFlow()

    /** `WKUserScript`/`WKScriptMessageHandler` есть во всех поддерживаемых версиях iOS. */
    actual val isSupported: Boolean = true

    private var originFilter: EmbedOriginFilter? = null
    private var webView: WKWebView? = null
    private var userContentController: WKUserContentController? = null
    private var videoFrame: WKFrameInfo? = null

    /**
     * Фреймы хоста без `<video>` — адресаты команд до первого старта (см. одноимённое поле
     * Android-контроллера). `WKFrameInfo` приходит новым объектом на каждое сообщение, поэтому
     * ключ — главный/дочерний фрейм + origin: последний экземпляр на ключ.
     */
    private val candidateFrames = LinkedHashMap<String, WKFrameInfo>()

    /** Применяет «качество по умолчанию» из настроек к меню хоста — один раз на источник. */
    private val qualityApplier = PreferredQualityApplier()

    private val messageHandler = EmbedBridgeMessageHandler(::onBridgeMessage)

    actual fun setExpectedSource(embedUrl: String) {
        originFilter = EmbedOriginFilter(embedUrl)
        videoFrame = null
        candidateFrames.clear()
        qualityApplier.reset()
        stateFlow.value = EmbedVideoState()
    }

    actual fun setPreferredQuality(heightPx: Int?) = qualityApplier.setPreferred(heightPx)

    /** Вызывать до создания `WKWebView` — конфигурация копируется в web view конструктором. */
    internal fun install(configuration: WKWebViewConfiguration) {
        val contentController = configuration.userContentController
        if (userContentController === contentController) return
        userContentController = contentController
        contentController.addUserScript(
            WKUserScript(
                source = embedBridgeScript(),
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = false,
            ),
        )
        runCatching {
            contentController.addScriptMessageHandler(messageHandler, name = EMBED_BRIDGE_CHANNEL)
        }
    }

    internal fun attach(target: WKWebView) {
        webView = target
    }

    /** Страница источника не загрузилась — см. [PlaybackEngineProblem.SourceUnavailable]. */
    internal fun reportEngineProblem(problem: PlaybackEngineProblem) {
        stateFlow.value = stateFlow.value.copy(engineProblem = problem)
    }

    actual fun release() {
        webView = null
        videoFrame = null
        candidateFrames.clear()
        runCatching { userContentController?.removeScriptMessageHandlerForName(EMBED_BRIDGE_CHANNEL) }
        userContentController = null
        stateFlow.value = EmbedVideoState()
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

    private fun onBridgeMessage(
        origin: String?,
        body: String,
        frame: WKFrameInfo,
    ) {
        if (originFilter?.accepts(origin) != true) return
        // Сообщения, не являющиеся state-апдейтом (отладочный отчёт о видимом chrome — см.
        // parseEmbedChromeDebug), состояния не меняют, только логируются.
        // Сообщённая страницей ошибка загрузки (engineProblem) переживает апдейты моста — см. Android.
        val parsed = parseEmbedVideoState(body)?.copy(engineProblem = stateFlow.value.engineProblem)
        if (parsed == null) {
            reportChromeDebug(body)
            return
        }
        // Второй фрейм того же домена без видео не должен затирать состояние настоящего.
        val usableFrame = parsed.isVideoFound || videoFrame == null
        if (parsed.isVideoFound) {
            videoFrame = frame
        } else if (videoFrame == null && candidateFrames.size < MAX_CANDIDATE_FRAMES) {
            candidateFrames["${frame.mainFrame}|$origin"] = frame
        }
        if (usableFrame) {
            stateFlow.value = parsed
            // Видео найдено и меню качеств известно — единственный момент, когда клик по пункту меню
            // хоста имеет смысл; применяется один раз на источник (см. PreferredQualityApplier).
            if (parsed.isVideoFound) qualityApplier.onState(parsed)?.let(::setQuality)
        }
    }

    private fun reportChromeDebug(raw: String) {
        val entries = parseEmbedChromeDebug(raw)
        // Без аргументов формата: `NSLog("%@", kotlinString)` роняет процесс (EXC_BAD_ACCESS в
        // CFStringAppendFormat — живой краш 2026-10-02), `%` в тексте экранируем.
        if (entries.isNotEmpty()) NSLog("embed chrome over video: ${entries.joinToString(" ; ").replace("%", "%%")}")
    }

    private fun send(command: String) {
        val target = webView ?: return
        val frames = videoFrame?.let(::listOf) ?: candidateFrames.values.toList()
        // `typeof` — фрейм мог перезагрузиться и ещё не получить мост; тогда команда — no-op.
        val script =
            "if (typeof window.$EMBED_BRIDGE_EXEC_FN === 'function') " +
                "window.$EMBED_BRIDGE_EXEC_FN(${command.asJsStringLiteral()});"
        frames.forEach { frame ->
            runCatching {
                target.evaluateJavaScript(
                    javaScriptString = script,
                    inFrame = frame,
                    inContentWorld = WKContentWorld.pageWorld,
                    completionHandler = null,
                )
            }
        }
    }
}

/** Ограничение на случай страницы с десятками фреймов того же домена. */
private const val MAX_CANDIDATE_FRAMES = 8

/**
 * Команды формируются нами и состоят из `[a-zA-Z0-9:.\-]`, но литерал всё равно экранируем —
 * чтобы будущая команда с произвольным текстом не превратилась в инъекцию в чужую страницу.
 */
private fun String.asJsStringLiteral(): String {
    val escaped = replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
    return "'$escaped'"
}

/**
 * `WKScriptMessageHandler` — обычный ObjC-делегат, в Kotlin/Native реализуется наследником
 * [NSObject] с реализацией протокола.
 */
private class EmbedBridgeMessageHandler(
    private val onMessage: (origin: String?, body: String, frame: WKFrameInfo) -> Unit,
) : NSObject(),
    WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? String ?: return
        val frame = didReceiveScriptMessage.frameInfo
        val securityOrigin = frame.securityOrigin
        val origin = "${securityOrigin.protocol}://${securityOrigin.host}"
        onMessage(origin, body, frame)
    }
}
