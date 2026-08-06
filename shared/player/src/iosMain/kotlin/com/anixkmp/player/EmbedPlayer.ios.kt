@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anixkmp.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
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
actual fun EmbedPlayerView(url: String, modifier: Modifier) {
    UIKitView(
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                defaultWebpagePreferences.allowsContentJavaScript = true
            }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                loadUrlOrNull(url)
            }
        },
        update = { webView -> webView.loadUrlOrNull(url) },
        modifier = modifier.fillMaxSize(),
    )
}

private fun WKWebView.loadUrlOrNull(url: String) {
    // Без этой проверки `update` перезагружал бы страницу на каждой рекомпозиции.
    if (this.URL?.absoluteString == url) return
    // Untrusted URL из ответа API — грузим только http/https (см. код-ревью Фазы 5).
    if (!isSafeEmbedUrl(url)) return
    // `URLWithString` (в отличие от конструктора `NSURL(string:)`) явно объявлен nullable —
    // не полагаемся на то, как cinterop разрешит nullability обычного конструктора.
    val nsUrl = NSURL.URLWithString(url) ?: return
    loadRequest(NSURLRequest(uRL = nsUrl))
}
