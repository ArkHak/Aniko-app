package com.aniko.player

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Desktop: локальный HTTP-сервер на 127.0.0.1, обслуживающий единственную статическую HTML-
 * страницу — полноэкранный `<iframe src="реальный URL источника">`.
 *
 * Зачем это вообще нужно, раз [Desktop.browse] мог бы открыть исходный URL и напрямую: Kodik
 * сверяет в своём JS `isIframe()` (`window.self !== window.top`) и рисует «данной страницы не
 * существует», если открыть ссылку как top-level документ (см. KDoc `EmbedPlayer.android.kt`) —
 * без обёртки в НАСТОЯЩИЙ `<iframe>` видео не показывается вообще.
 *
 * Почему НЕ через `Referer`-спуфинг (первая версия этого файла пыталась сервером самому
 * забирать embed-страницу с нужным заголовком и переотдавать её с локального порта): страница,
 * переотданная с `127.0.0.1`, лишается своего РЕАЛЬНОГО origin (`kodikplayer.com`) — её
 * собственный JS дальше делает `POST /ftor` (получение подписанной ссылки на манифест) тем же
 * XHR, который браузер трактует как cross-origin к `kodikplayer.com` и блокирует CORS
 * (`kodikplayer.com` не шлёт `Access-Control-Allow-Origin` для чужого origin) — воспроизведение
 * падает с «эта видеозапись в данный момент недоступна», хотя сама embed-страница успевала
 * загрузиться. Простой `<iframe src="реальный URL">` — БЕЗ переотдачи содержимого — сохраняет
 * настоящую навигацию браузера на `kodikplayer.com` внутри iframe: `document.location.origin`
 * внутри него — тот же `kodikplayer.com`, `POST /ftor` — честный same-origin запрос, CORS не
 * участвует вовсе. Referer для этой настоящей навигации подделать всё равно нельзя (это
 * ограничение браузера, не наше), но живой тест (2026-08-23, Playwright + реальный fresh URL из
 * приложения) показал: без Referer вообще Kodik всё равно отдаёт рабочий `.m3u8`-манифест —
 * жёсткая проверка Referer, которую документировал `EmbedPlayer.android.kt`
 * (`500 "Error code: ds"`), для базовой загрузки страницы/резолвинга видео не оказалась
 * обязательной, только для isIframe().
 */
internal object KodikProxyServer {
    private val startMutex = Mutex()
    private var server: EmbeddedServer<*, *>? = null
    private var boundPort: Int = 0

    /** Локальный URL обёртки для [targetUrl], поднимая сервер при первом вызове (идемпотентно). */
    suspend fun wrapperUrl(targetUrl: String): String {
        val port = ensureStarted()
        val encodedUrl = URLEncoder.encode(targetUrl, "UTF-8")
        return "http://$LOCALHOST:$port/?url=$encodedUrl"
    }

    // detekt ложно принимает `Mutex.withLock` (inline suspend-функцию) за отсутствие точки
    // приостановки — функция реально suspend, без него не компилируется (проверено: убрать
    // suspend -> "can only be called from a coroutine or another suspend function").
    @Suppress("RedundantSuspendModifier")
    private suspend fun ensureStarted(): Int =
        startMutex.withLock {
            server?.let { return boundPort }
            val port = findFreePort()
            server =
                embeddedServer(CIO, host = LOCALHOST, port = port) {
                    routing {
                        get("/") {
                            val encodedUrl = call.request.queryParameters["url"]
                            val targetUrl = encodedUrl?.let { URLDecoder.decode(it, "UTF-8") }
                            call.respondText(wrapperHtml(targetUrl), contentType = ContentType.Text.Html)
                        }
                    }
                }.start(wait = false)
            boundPort = port
            boundPort
        }

    private fun wrapperHtml(targetUrl: String?): String {
        if (targetUrl == null) return "<html><body>Missing url</body></html>"
        val escaped = targetUrl.replace("\"", "&quot;")
        return """
            <html><head><meta charset="utf-8"></head>
            <body style="margin:0;background:#000">
            <iframe src="$escaped" style="border:0;width:100vw;height:100vh" allow="autoplay; fullscreen" allowfullscreen></iframe>
            </body></html>
            """.trimIndent()
    }

    private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

    private const val LOCALHOST = "127.0.0.1"
}
