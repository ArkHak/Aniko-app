package com.anixkmp.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android: обычная встроенная веб-страница поверх `android.webkit.WebView`.
 *
 * `javaScriptEnabled = true` и `domStorageEnabled = true` — большинству embed-плееров
 * (Kodik/Sibnet/...) они нужны, без них плеер на странице попросту не инициализируется.
 *
 * `webViewClient` блокирует переходы на не-`http(s)` схемы (`intent://`, `market://` и т.п.) —
 * embed-страницы видеохостингов нередко содержат рекламные редиректы именно на такие схемы.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun EmbedPlayerView(url: String, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                        !isSafeEmbedUrl(request.url.toString())
                }
                if (isSafeEmbedUrl(url)) loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url && isSafeEmbedUrl(url)) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
