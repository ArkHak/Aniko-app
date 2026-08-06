package com.anixkmp.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
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
 *
 * Kodik-страница плеера (`kodikplayer.com/seria/...`) проверяет в своём JS `isIframe()` и
 * рендерит "данной страницы не существует", если её открыть как top-level документ (проверено
 * вживую) — легитимный способ встраивания (см. код `get_code_copy` на самой странице) — это
 * `<iframe src="...">` на чужом домене. Поэтому для Kodik грузим не саму ссылку, а маленькую
 * HTML-обёртку с этим iframe внутри (через `loadDataWithBaseURL`), а не `loadUrl` напрямую.
 * `baseUrl` обёртки — [referer] (`https://anixmirai.com/`, авторизованный на стороне Kodik
 * партнёрский домен) — WebView использует его как `Referer` при запросе iframe, и Kodik сам
 * генерирует по нему корректные `d_sign`/`pd_sign`/`ref_sign` (без него — `500 "Error code: ds"`,
 * тоже проверено вживую).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun EmbedPlayerView(url: String, referer: String?, modifier: Modifier) {
    val headers = referer?.let { mapOf("Referer" to it) } ?: emptyMap()
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
                // Без гранта PROTECTED_MEDIA_ID WebView отклоняет запрос EME/Widevine, который
                // делает html5-плеер при инициализации видео — экран остаётся чёрным без единой
                // ошибки в логе. Оригинал (`KodikAdActivity$webChromeClient$1`) грантит его же.
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        request.grant(request.resources)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                        !isSafeEmbedUrl(request.url.toString())
                }
                loadEmbed(url, referer, headers)
            }
        },
        update = { webView ->
            if (webView.tag != url && isSafeEmbedUrl(url)) {
                webView.loadEmbed(url, referer, headers)
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

private fun WebView.loadEmbed(url: String, referer: String?, headers: Map<String, String>) {
    if (!isSafeEmbedUrl(url)) return
    // Для Kodik грузим не сам [url] через `loadUrl`, а HTML-обёртку через `loadDataWithBaseURL` —
    // после неё `WebView.getUrl()` возвращает `baseUrl` (referer), а не [url], поэтому сравнивать
    // с ним в `update` (как раньше) нельзя: на каждой рекомпозиции обёртка грузилась бы заново,
    // перезапуская воспроизведение. `tag` хранит именно то, что запрошено этой функцией.
    tag = url
    if (isKodikEmbedUrl(url)) {
        val baseUrl = referer ?: "https://anixmirai.com/"
        loadDataWithBaseURL(baseUrl, kodikIframeHtml(url), "text/html", "UTF-8", null)
    } else {
        loadUrl(url, headers)
    }
}
