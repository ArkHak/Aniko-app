package com.aniko.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val messageHandler = EmbedBridgeMessageHandler(::onBridgeMessage)

    actual fun setExpectedSource(embedUrl: String) {
        originFilter = EmbedOriginFilter(embedUrl)
        videoFrame = null
        stateFlow.value = EmbedVideoState()
    }

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

    actual fun release() {
        webView = null
        videoFrame = null
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

    private fun onBridgeMessage(
        origin: String?,
        body: String,
        frame: WKFrameInfo,
    ) {
        if (originFilter?.accepts(origin) != true) return
        val parsed = parseEmbedVideoState(body) ?: return
        if (parsed.isVideoFound) {
            videoFrame = frame
        } else if (videoFrame != null) {
            return
        }
        stateFlow.value = parsed
    }

    private fun send(command: String) {
        val target = webView ?: return
        val frame = videoFrame ?: return
        val script = "window.$EMBED_BRIDGE_EXEC_FN(${command.asJsStringLiteral()});"
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
