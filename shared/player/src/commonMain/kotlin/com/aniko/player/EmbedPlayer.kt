package com.aniko.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Простой embed-рендерер для MVP плеера.
 *
 * Архитектурное решение (Фаза 5, зафиксировано пользователем, не пересматривать):
 * ВСЁ воспроизведение идёт через встраиваемую веб-страницу источника (Kodik/Sibnet/...),
 * независимо от того, отдаёт ли сервер прямой поток или iframe-страницу
 * (см. `EpisodeTargetDto.iframe` в `:shared:data`). Нативный [PlayerController]/[VideoSurface]
 * из этого же модуля НЕ используются здесь — это отдельный, более простой механизм,
 * задел на нативное воспроизведение остаётся нетронутым на будущее.
 *
 * Платформенные реализации:
 * - Android — `android.webkit.WebView` в `AndroidView`.
 * - iOS — `WKWebView` в `UIKitView`.
 * - Desktop — полноценного WebView в Compose Desktop без тяжёлых зависимостей (JCEF/KCEF) нет,
 *   поэтому осознанно упрощаем: открываем URL в системном браузере и показываем заглушку.
 */
/**
 * [referer] у большинства embed-хостов (Sibnet и т.п.) — self-referer (тот же URL, что и [url]):
 * они отдают `403`/страницу-заглушку без заголовка `Referer` (защита от хотлинкинга), а
 * оригинальное Anixart-приложение шлёт именно значение самого запрашиваемого URL, а не
 * фиксированный домен — см. `WebPlayerActivity.onCreate` в `docs/api/jadx-out`.
 *
 * У Kodik ([isKodikEmbedUrl]) — наоборот, фиксированный `https://anixmirai.com/`: это
 * авторизованный на стороне Kodik партнёрский домен (тот же, что видно у оригинального
 * приложения), по нему Kodik сам генерирует корректные подписи для страницы плеера.
 */
@Composable
expect fun EmbedPlayerView(url: String, referer: String? = null, modifier: Modifier = Modifier)

/**
 * `true`, если [url] безопасно передавать в системный WebView/браузер — только `http`/`https`.
 *
 * `url` приходит из ответа Anixart API (`episode/target`) и формально untrusted: без этой
 * проверки платформенные реализации передали бы схему как есть в `WebView.loadUrl` /
 * `WKWebView.loadRequest` / `Desktop.browse`, что для `javascript:`/`file:`/подобных схем
 * потенциально небезопасно (см. код-ревью Фазы 5).
 */
fun isSafeEmbedUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

/**
 * `true`, если [url] — страница плеера Kodik (`kodikplayer.com`/`kodik.cc`/... или уже
 * `anixmirai.com`). Kodik-страница плеера в своём JS проверяет `isIframe()` и рендерит
 * "данной страницы не существует", если её открыть как top-level документ, а не как
 * `<iframe>` на чужом домене (проверено вживую) — платформенные реализации [EmbedPlayerView]
 * оборачивают такой URL в HTML с `<iframe>` вместо прямой навигации. Другим embed-хостам
 * (Sibnet и т.п.) обёртка не нужна.
 */
fun isKodikEmbedUrl(url: String): Boolean =
    listOf("kodik.cc", "kodik.info", "kodik-hd.com", "kodik.biz", "aniqit.com", "kodikplayer.com", "anixmirai.com")
        .any { host -> url.contains(host, ignoreCase = true) }

/**
 * HTML-страница с [url] в `<iframe>` на весь экран — общая для платформенных реализаций
 * [EmbedPlayerView], которым для Kodik ([isKodikEmbedUrl]) нужно грузить не саму ссылку, а такую
 * обёртку (см. её комментарий в [isKodikEmbedUrl]).
 */
internal fun kodikIframeHtml(url: String): String {
    val escapedUrl = url.replace("&", "&amp;").replace("\"", "&quot;")
    return """
        <!DOCTYPE html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
        <body style="margin:0;padding:0;background:#000;overflow:hidden">
        <iframe src="$escapedUrl" style="position:absolute;top:0;left:0;width:100%;height:100%;border:0"
         allow="autoplay *; fullscreen *; encrypted-media *" allowfullscreen></iframe>
        </body></html>
    """.trimIndent()
}
