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
 */
@Composable
actual fun EmbedPlayerView(
    url: String,
    referer: String?,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            val configuration =
                WKWebViewConfiguration().apply {
                    defaultWebpagePreferences.allowsContentJavaScript = true
                }
            AnixEmbedWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                loadEmbed(url, referer)
            }
        },
        update = { webView -> webView.loadEmbed(url, referer) },
        modifier = modifier.fillMaxSize(),
    )
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
