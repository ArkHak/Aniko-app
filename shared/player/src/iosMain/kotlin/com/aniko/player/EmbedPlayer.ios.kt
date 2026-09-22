@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aniko.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.setValue
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * iOS: `WKWebView` в `UIKitView` (стиль cinterop — как в `IosKeychainTokenStorage.kt`, но здесь
 * интероп ограничен голыми `platform.WebKit`/`platform.Foundation`, без ручной работы с CoreFoundation).
 *
 * JS в современном `WKWebView` включён по умолчанию, но выставляем явно через
 * `defaultWebpagePreferences.allowsContentJavaScript` — не полагаемся молча на дефолт, раз
 * большинству embed-плееров (Kodik/Sibnet/...) JS обязателен.
 *
 * `allowsInlineMediaPlayback = true` — без него `<video>.play()` внутри embed-страницы форсит
 * нативный полноэкранный AVKit-плеер поверх нашего Compose-оверлея (кнопка «Назад», чипы
 * «Аудио»/«Качество»/«1x»), т.к. дефолт `WKWebViewConfiguration` на iOS — `false`. Это не
 * конфликтует с тем, что явный тап пользователя по кругу play в нашем UI уже требуется:
 * ничего не выставляет `mediaTypesRequiringUserActionForPlayback` (дефолт `.all` — жест
 * пользователя нужен что для инлайн-, что для полноэкранного воспроизведения), так что это
 * только меняет, ЧТО происходит после разрешённого play — инлайн вместо форс-фуллскрина.
 *
 * [controller] (см. [rememberEmbedVideoController]) — опциональный JS-мост к `<video>` внутри
 * страницы. Его `WKUserScript`/`WKScriptMessageHandler` прописываются в `WKWebViewConfiguration`
 * ДО конструктора `WKWebView`: после создания web view конфигурация уже скопирована и правки
 * в неё ни на что не влияют.
 *
 * Уход из композиции (смена озвучки/серии, выход с экрана) — окончательный: `onRelease` останавливает
 * загрузку и подменяет страницу пустой ([releaseEmbed]), чтобы у убранного вида не продолжали
 * играть медиа и реклама, пока Kotlin/Native GC не освободит сам `WKWebView`. Снятие обработчика
 * сообщений моста (и разрыв цикла `WKWebView` → конфигурация → обработчик → контроллер) делает
 * `EmbedVideoController.release()` из `rememberEmbedVideoController`, здесь оно не дублируется.
 */
@Composable
actual fun EmbedPlayerView(
    url: String,
    referer: String?,
    controller: EmbedVideoController?,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            val configuration =
                WKWebViewConfiguration().apply {
                    defaultWebpagePreferences.allowsContentJavaScript = true
                    allowsInlineMediaPlayback = true
                }
            controller?.install(configuration)
            AnixEmbedWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                controller?.attach(this)
                loadEmbed(url, referer)
            }
        },
        update = { webView -> webView.loadEmbed(url, referer) },
        onRelease = { webView -> webView.releaseEmbed() },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Останавливает загрузку и подменяет документ пустым: страница вместе с её скриптами, рекламными
 * фреймами и `<video>` выгружается сразу. Без этого убранный из композиции `WKWebView` мог жить,
 * пока его не освободит Kotlin/Native, и всё это время играть звук и грузить рекламу.
 *
 * Порядок относительно `EmbedVideoController.release()` (тот снимает `WKScriptMessageHandler`)
 * не важен: если обработчик уже снят, скрипт моста на пустой странице до него не достучится
 * (`post` в `embedBridgeScript` проверяет наличие обработчика), а если ещё нет — origin пустой
 * страницы (`about:blank`) отбросит `EmbedOriginFilter`. `loadedEmbedUrl` намеренно не трогаем:
 * пока он совпадает с запрошенным `url`, `update` не перезагрузит embed в убранный вид.
 */
private fun AnixEmbedWebView.releaseEmbed() {
    stopLoading()
    loadHTMLString("", baseURL = null)
}

/**
 * `WKWebView` без публичного эквивалента `View.getTag()/setTag()` из Android — [loadedEmbedUrl]
 * нужен, чтобы `update` не перезагружал страницу на каждой рекомпозиции: для Kodik после
 * [loadHTMLString] `WKWebView.URL` равен `baseURL` (referer), а не запрошенному [url].
 */
private class AnixEmbedWebView(
    frame: CValue<CGRect>,
    configuration: WKWebViewConfiguration,
) : WKWebView(frame, configuration) {
    var loadedEmbedUrl: String? = null
}

private fun AnixEmbedWebView.loadEmbed(
    url: String,
    referer: String?,
) {
    if (loadedEmbedUrl == url) return
    // Untrusted URL из ответа API — грузим только http/https (см. код-ревью Фазы 5).
    if (!isSafeEmbedUrl(url)) return
    loadedEmbedUrl = url
    if (isKodikEmbedUrl(url)) {
        // См. комментарий у [isKodikEmbedUrl]/`kodikIframeHtml` в commonMain: Kodik отдаёт
        // "данной страницы не существует", если открыть страницу плеера напрямую, а не в
        // `<iframe>`. `baseURL` — [referer] (`https://anixmirai.com/`), WKWebView шлёт его как
        // `Referer` при запросе iframe.
        val baseUrl = NSURL.URLWithString(referer ?: "https://anixmirai.com/") ?: return
        loadHTMLString(kodikIframeHtml(url), baseURL = baseUrl)
    } else {
        // `URLWithString` (в отличие от конструктора `NSURL(string:)`) явно объявлен nullable —
        // не полагаемся на то, как cinterop разрешит nullability обычного конструктора.
        val nsUrl = NSURL.URLWithString(url) ?: return
        val request =
            if (referer != null) {
                NSMutableURLRequest(uRL = nsUrl).apply {
                    setValue(referer, forHTTPHeaderField = "Referer")
                }
            } else {
                NSURLRequest(uRL = nsUrl)
            }
        loadRequest(request)
    }
}
