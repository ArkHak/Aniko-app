package com.aniko.player

import android.net.Uri
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

    private val messageListener =
        WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, replyProxy ->
            onBridgeMessage(sourceOrigin, message, replyProxy)
        }

    actual fun setExpectedSource(embedUrl: String) {
        originFilter = EmbedOriginFilter(embedUrl)
        videoFrame = null
        stateFlow.value = EmbedVideoState()
    }

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
        runCatching { scriptHandler?.remove() }
        scriptHandler = null
        runCatching { WebViewCompat.removeWebMessageListener(attached, EMBED_BRIDGE_CHANNEL) }
        stateFlow.value = EmbedVideoState()
    }

    actual fun release() = detach()

    actual fun play() = send(EmbedVideoCommand.PLAY)

    actual fun pause() = send(EmbedVideoCommand.PAUSE)

    actual fun togglePlayPause() {
        if (stateFlow.value.isPlaying) pause() else play()
    }

    actual fun seekTo(positionMs: Long) = send(EmbedVideoCommand.seek(positionMs))

    actual fun seekBy(deltaMs: Long) = send(EmbedVideoCommand.seekBy(deltaMs))

    actual fun setPlaybackRate(rate: Float) = send(EmbedVideoCommand.rate(rate))

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
        val parsed = runCatching { message.data }.getOrNull()?.let(::parseEmbedVideoState) ?: return
        if (parsed.isVideoFound) {
            videoFrame = replyProxy
            stateFlow.value = parsed
        } else if (videoFrame == null) {
            // Второй фрейм того же домена без видео не должен затирать состояние настоящего.
            stateFlow.value = parsed
        }
    }

    /**
     * `JavaScriptReplyProxy.postMessage` обязан вызываться на UI-потоке WebView — команды
     * приходят из Compose (тоже UI-поток), но `post` снимает вопрос порядка относительно
     * ещё не завершённой навигации.
     */
    private fun send(command: String) {
        val proxy = videoFrame ?: return
        val target = webView
        if (target != null) {
            target.post { runCatching { proxy.postMessage(command) } }
        } else {
            runCatching { proxy.postMessage(command) }
        }
    }

    private companion object {
        val ALLOWED_ORIGIN_RULES = setOf("*")
    }
}
