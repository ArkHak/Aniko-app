// `UnreachableCode`: тот же класс ложного срабатывания, что уже задокументирован и подавлен в
// `EmbedVideoController.android.kt` (`onBridgeMessage`) — detekt метит достижимый код после
// `runCatching { ... }.getOrNull() ?: return`/`continue` как "недостижимый" внутри функций этого
// файла (реальный компилятор — `:shared:player:compileKotlinDesktop`, зелёный — доволен, код
// реально выполняется и резолвит живые URL, см. отчёт задачи). Похоже на ту же слабость детекта
// с типами из свежедобавленных зависимостей (здесь — Ktor/kotlinx.serialization.json), а не на
// специфику Android; локальные `@Suppress` на каждую из ~15 затронутых строк раздули бы файл
// сильнее, чем один file-level суффикс с одним объяснением.
@file:Suppress("UnreachableCode")

package com.aniko.player

import com.aniko.model.VideoHost
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

/**
 * Резолвер реального медиа-URL со страницы embed-хоста (Kodik/Sibnet/AniLibria) — Step 3
 * пересмотра P8.T1 (см. журнал `docs/REELWAVE_PLAN.md`, `feature/desktop-video-player`).
 *
 * **Заменяет headless-JCEF версию этого класса (Step 2), а не расширяет её.** Та версия поднимала
 * реальный `CefBrowser` за пределами экрана и ждала, пока страница хоста сама исполнит свой JS и
 * запросит настоящий поток — рабочая идея в теории, но живая проверка на реальном логине (эпизод
 * id 693, "Дневник будущего") показала: медленно (полноценный boot Chromium + загрузка страницы на
 * КАЖДУЮ серию), хрупко (headless-реализация JCEF уже роняла JVM нативным крэшем под память на этой
 * ветке) и на практике не резолвило вовсе — таймаут в тишину без единого сигнала, откуда именно
 * ушёл провал.
 *
 * **Этот резолвер не запускает браузер вообще.** Реальный референс-клиент Anixart —
 * github.com/Maks1mio/anixapp (Electron, MIT-подобная лицензия, актуален) — резолвит те же три
 * хоста чистым HTTP: страница embed-хоста либо содержит прямой поток прямо в HTML (AniLibria),
 * либо отдаёт его через собственный внутренний API по тем же параметрам, что видны в URL/HTML
 * (Kodik `/ftor`), либо через цепочку HTTP-редиректов с сервера на реальный файл (Sibnet). Ни один
 * из трёх случаев не требует исполнения JS — только сетевые запросы и разбор ответа. Портировано
 * по авторитетному первоисточнику (`electron/kodik-direct.js`,
 * `electron/lib/direct-video-link.js` того репозитория), а не по пересказу — там есть неочевидные
 * детали (см. KDoc [KodikDirectLinkResolver]/[SibnetDirectLinkResolver] за конкретикой).
 *
 * Хост определяется через [VideoHost.fromUrl] (`:shared:model`, уже покрыт тестами) — не через
 * свой набор regex, как было бы проще ошибиться.
 */
object DesktopStreamResolver {
    /**
     * Итог удачного резолва.
     *
     * @param streamUrl прямая ссылка (m3u8/mp4), которую понимает libVLC.
     * @param referer Referer, с которым нужно СТРИМИТЬ именно этот [streamUrl] (см.
     * `EmbedPlayer.desktop.kt`, `:http-referrer=`). **Не обязательно совпадает** с Referer'ом,
     * которым была загружена сама embed-страница ([resolve]'s `referer` параметр) — Kodik тому
     * прямой пример: `https://anixmirai.com/` нужен ТОЛЬКО чтобы Kodik согласился отдать страницу
     * плеера этому партнёрскому аккаунту (см. `docs/api/ANIXART_API.md`, иначе `500 "Error code:
     * ds"`), а сам CDN-хост потока (`solodcdn`/`kodik-storage`/...) проверяет Referer на
     * `https://kodikplayer.com/` — свой собственный домен, а не домен партнёра. Использование
     * "чужого" referer тут было бы тихой порчей воспроизведения: не ошибка резолва, а 403/зависший
     * буфер уже внутри VLCJ, которую было бы гораздо труднее диагностировать постфактум.
     */
    data class Resolved(
        val streamUrl: String,
        val referer: String?,
    )

    /**
     * `null` — резолв не удался: [url] не прошёл [isSafeEmbedUrl], хост не входит в
     * поддерживаемые ([VideoHost.KODIK]/[VideoHost.SIBNET]/[VideoHost.ANILIBRIA]), либо ни один
     * шаг конкретного резолвера не завершился успехом за [timeoutMs] (мёртвая ссылка/таймаут
     * сети/хост сменил формат ответа). Вызывающая сторона (`EmbedPlayer.desktop.kt`) обязана
     * трактовать `null` как честную неудачу — [PlayerOverlay] (commonMain, `composeApp`) сам
     * деградирует до одной кнопки «назад» через `EmbedVideoState.isVideoFound == false`, ничего
     * дополнительно сигнализировать отсюда не нужно.
     *
     * [referer] — Referer, с которым ОЖИДАЕТСЯ, что можно загрузить саму embed-страницу [url]
     * (`PlaybackSource.Embed.referer`, см. `EpisodeRepository.resolveEpisodeTarget`) — то же
     * значение, что подставляют Android/iOS WebView. Используется резолверами ТОЛЬКО там, где
     * нужен именно этот, вызывающей стороной подтверждённый referer (например, HTML-фолбэк Kodik,
     * см. его KDoc) — не путать с [Resolved.referer] (см. его KDoc за разницей).
     *
     * `@Suppress("ReturnCount")` — три guard-clause выхода (небезопасный URL / неподдерживаемый
     * хост / провал конкретного резолвера) читаются честнее одной пирамиды `if/else`.
     */
    @Suppress("ReturnCount")
    suspend fun resolve(
        url: String,
        referer: String?,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Resolved? {
        if (!isSafeEmbedUrl(url)) return null
        val host = VideoHost.fromUrl(url)
        return withTimeoutOrNull(timeoutMs) {
            when (host) {
                VideoHost.KODIK -> KodikDirectLinkResolver.resolve(httpClient, url, referer)
                VideoHost.SIBNET -> SibnetDirectLinkResolver.resolve(httpClient, url)
                VideoHost.ANILIBRIA -> AniLibriaDirectLinkResolver.resolve(httpClient, url)
                else -> null
            }
        }
    }

    /**
     * Единый на весь процесс `HttpClient` (OkHttp-движок — тот же, что и у `:shared:network` на
     * Desktop, см. `PlatformHttpClient.jvmShared.kt`, — но ОТДЕЛЬНЫЙ инстанс, не переиспользует
     * `AnixHttpClient`: тот сконфигурирован под API Anixart персонально — фиксированный `baseUrl`,
     * `expectSuccess = true`, автоподстановка токена, cookie jar под ddos-guard конкретно
     * `api-s.anixsekai.com` — ни одно из этого не подходит для произвольных сторонних CDN-хостов
     * с их собственными Referer/редирект-цепочками).
     *
     * `followRedirects = false` — намеренно ГЛОБАЛЬНО для всего клиента, а не только для
     * CDN-запросов: и Kodik ([KodikDirectLinkResolver]), и Sibnet ([SibnetDirectLinkResolver])
     * обязаны видеть КАЖДЫЙ промежуточный `3xx`/`Location` сами (Sibnet — чтобы нормализовать
     * относительные/протокол-относительные адреса по ходу переходов, Kodik — чтобы слать
     * `Range: bytes=0-0` на зонд достижимости CDN, не скачивая файл целиком; см.
     * [followMediaRedirects]/[getTextFollowingRedirects]) — автоматический редирект движка
     * (`HttpRedirect`, включён Ktor по умолчанию через `HttpClientConfig.followRedirects`) не
     * даёт заглянуть внутрь цепочки, только конечный результат.
     *
     * Не закрывается явно (`close()`) — это процесс-level синглтон уровня приложения (тот же
     * принцип, что был у старого `DesktopWebEngine`, но без его веса: здесь нет ни нативного
     * процесса, ни файлов на диске, только пул соединений OkHttp) — JVM освобождает его сокеты при
     * выходе из процесса, отдельного `dispose()`-хука ради этого заводить не стали.
     */
    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    /** С запасом больше типичного времени полного резолва по живым замерам (страница + `/ftor`/
     *  редирект-цепочка укладываются в 1-3 с), но не настолько долго, чтобы плеер завис в
     *  состоянии "резолвим" на мёртвой ссылке (истёкший токен, недоступный хост). На порядок
     *  меньше, чем было у headless-JCEF версии (15 с) — здесь нет boot'а браузера. */
    private const val DEFAULT_TIMEOUT_MS = 12_000L
    private const val REQUEST_TIMEOUT_MS = 8_000L
    private const val CONNECT_TIMEOUT_MS = 5_000L
}

/** Браузероподобный User-Agent — часть хостов (Sibnet, Kodik) отдают урезанную/иную разметку
 *  клиентам без него (проверено эталонным клиентом, см. KDoc [DesktopStreamResolver]). */
internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

/** Content-Type финального ответа похож на настоящее медиа, а не на HTML-страницу (например,
 *  страницу "видео удалено", которая у некоторых хостов тоже отдаётся с `200 OK`). Используется
 *  там, где сам факт "сервер ответил 2xx" ещё не значит "это действительно поток" — см.
 *  [SibnetDirectLinkResolver]. */
internal fun looksLikeMediaContentType(contentType: String?): Boolean {
    val normalized = contentType?.lowercase() ?: return false
    return MEDIA_CONTENT_TYPE_REGEX.containsMatchIn(normalized)
}

private val MEDIA_CONTENT_TYPE_REGEX = Regex("video/|octet-stream|mpegurl|mp4")
private val ABSOLUTE_URL_REGEX = Regex("^https?://", RegexOption.IGNORE_CASE)
private const val MAX_REDIRECT_HOPS = 5
private const val HTTP_REDIRECT_STATUS_FIRST = 300
private const val HTTP_REDIRECT_STATUS_LAST = 399
private val REDIRECT_STATUS_RANGE = HTTP_REDIRECT_STATUS_FIRST..HTTP_REDIRECT_STATUS_LAST

/**
 * Итог ручной петли редиректов: [status]/`Content-Type` — от ПОСЛЕДНЕГО (не редиректящего) шага,
 * [finalUrl] — адрес этого шага (после всех переходов, для Sibnet это и есть искомый прямой URL).
 */
internal data class RedirectResult(
    val status: HttpStatusCode,
    val finalUrl: String,
    val contentType: String?,
)

private data class RedirectStep(
    val status: HttpStatusCode,
    val location: String?,
    val contentType: String?,
)

/**
 * Ручная петля редиректов с `Range: bytes=0-0` на КАЖДОМ шаге — не тянет тело файла целиком ради
 * проверки, реален ли конечный URL (см. KDoc [DesktopStreamResolver] за тем, почему это вообще
 * ручная петля, а не `followRedirects = true` движка). Используется и Kodik-зондом достижимости
 * CDN-манифеста, и Sibnet-резолвом самого прямого URL — оба хотят видеть каждый промежуточный
 * `Location` и решать по нему сами, не просто "долистать до конца".
 *
 * `null` — сетевая ошибка на любом из шагов (таймаут/DNS/обрыв) или [maxHops] исчерпаны без
 * терминального (не-`3xx`) ответа.
 *
 * `@Suppress("RedundantSuspendModifier")` — detekt ложно принимает `prepareGet(...).execute {}`
 * (inline suspend-функции) за отсутствие точки приостановки — тот же класс ложного срабатывания,
 * что уже не раз документировался в истории этой ветки для `withTimeoutOrNull`/`withContext`;
 * suspend реально нужен, без него не компилируется вызов `.execute { ... }`.
 *
 * `@Suppress("ReturnCount")` — три guard-clause выхода (сетевая ошибка / терминальный ответ /
 * исчерпание хопов) читаются честнее одной пирамиды `if/else`.
 */
@Suppress("RedundantSuspendModifier", "ReturnCount")
internal suspend fun HttpClient.followMediaRedirects(
    startUrl: String,
    headers: Map<String, String>,
    maxHops: Int = MAX_REDIRECT_HOPS,
): RedirectResult? {
    var current = startUrl
    var hop = 0
    while (hop <= maxHops) {
        val step =
            runCatching {
                prepareGet(current) {
                    headers.forEach { (name, value) -> header(name, value) }
                    header(HttpHeaders.Range, "bytes=0-0")
                }.execute { response ->
                    RedirectStep(
                        status = response.status,
                        location = response.headers[HttpHeaders.Location],
                        contentType = response.headers[HttpHeaders.ContentType],
                    )
                }
            }.getOrNull() ?: return null
        val location = step.location
        if (step.status.value in REDIRECT_STATUS_RANGE && location != null) {
            current = resolveRedirectLocation(current, location)
            hop++
            continue
        }
        return RedirectResult(step.status, current, step.contentType)
    }
    return null
}

/**
 * Обычный GET с ручным прохождением редиректов, но БЕЗ `Range` — тело читается полностью
 * ([HttpResponse.bodyAsText]). Для HTML/JSON страниц-обёрток (embed-страница, Kodik `/ftor`,
 * AniLibria HTML) — там, в отличие от [followMediaRedirects], нужен именно полный текст ответа,
 * не факт его существования.
 *
 * `null` — сетевая ошибка, [maxHops] исчерпаны, либо терминальный статус не 2xx.
 *
 * `@Suppress`: тот же случай (`RedundantSuspendModifier` — false positive на `get(...)`;
 * `ReturnCount` — три guard-clause выхода), что и у [followMediaRedirects] выше, см. её KDoc.
 */
@Suppress("RedundantSuspendModifier", "ReturnCount")
internal suspend fun HttpClient.getTextFollowingRedirects(
    startUrl: String,
    headers: Map<String, String>,
    maxHops: Int = MAX_REDIRECT_HOPS,
): String? {
    var current = startUrl
    var hop = 0
    while (hop <= maxHops) {
        val response =
            runCatching {
                get(current) { headers.forEach { (name, value) -> header(name, value) } }
            }.getOrNull() ?: return null
        val location = response.headers[HttpHeaders.Location]
        if (response.status.value in REDIRECT_STATUS_RANGE && location != null) {
            current = resolveRedirectLocation(current, location)
            hop++
            continue
        }
        return if (response.status.isSuccess()) response.bodyAsText() else null
    }
    return null
}

/**
 * Нормализует значение заголовка `Location` в абсолютный URL относительно [baseUrl] — Sibnet
 * (см. [SibnetDirectLinkResolver]) отдаёт корневые (`/v/...`) и протокол-относительные (`//...`)
 * адреса на промежуточных хопах, `java.net.URL`/Ktor это за нас не делают при ручном (не
 * `followRedirects`) проходе.
 */
private fun resolveRedirectLocation(
    baseUrl: String,
    location: String,
): String {
    val cleaned = location.replace(":443", "")
    return when {
        cleaned.startsWith("//") -> "https:$cleaned"
        cleaned.startsWith("/") -> {
            val base = runCatching { URI(baseUrl) }.getOrNull()
            if (base != null) "${base.scheme}://${base.authority}$cleaned" else cleaned
        }
        ABSOLUTE_URL_REGEX.containsMatchIn(cleaned) -> cleaned
        else -> "https:$cleaned"
    }
}
