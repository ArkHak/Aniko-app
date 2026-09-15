package com.aniko.player

import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

/**
 * Sibnet: прямой поток без единого исполнения JS — портировано по `getSibnetDirectLink`/
 * `followSibnetLocation` в `github.com/Maks1mio/anixapp/blob/main/electron/lib/direct-video-link.js`
 * (первоисточник, не пересказ — см. KDoc [DesktopStreamResolver]).
 *
 * Три шага:
 * 1. GET embed-страницы (`Referer: https://sibnet.ru/`, фиксированный — та же страница у Sibnet
 *    отдаёт `403`/заглушку без него, вне зависимости от конкретного `shell.php?videoid=`).
 * 2. Вытащить относительный путь плеера из инлайновой инициализации JS-плеера страницы
 *    (`player.src([{src: "/v/....mp4"}])` и несколько похожих паттернов у разных версий разметки
 *    — [SRC_PATTERNS], первый совпавший побеждает, как в оригинале). Если страница явно говорит
 *    "видео удалено" ([UNAVAILABLE_REGEX]) — честный `null` сразу, не пытаясь распарсить путь из
 *    HTML заглушки.
 * 3. Путь резолвится относительно `https://video.sibnet.ru`, и дальше — РУЧНАЯ петля редиректов
 *    (`Referer` = сам embed-URL — Sibnet требует self-referer и здесь тоже, не фиксированный
 *    `sibnet.ru`, см. `EpisodeRepository.resolveEpisodeTarget`/`PlaybackSource.Embed.referer`)
 *    вплоть до реального файла на CDN. Проверка `Content-Type` на последнем шаге
 *    ([looksLikeMediaContentType]) — не декоративная: Sibnet у части удалённых видео всё равно
 *    отдаёт `200 OK` с HTML-заглушкой вместо ожидаемого редиректа на файл, и без этой проверки
 *    резолвер отдал бы VLCJ ссылку на HTML-страницу вместо честного `null`.
 */
internal object SibnetDirectLinkResolver {
    /** `@Suppress("ReturnCount")` — пять guard-clause выходов (страница не отдалась / хост сказал
     *  "видео удалено" / не нашли путь плеера в разметке / редирект-цепочка оборвалась / конечный
     *  ответ не похож на медиа) читаются честнее одной пирамиды `if/else`. */
    @Suppress("ReturnCount")
    suspend fun resolve(
        client: HttpClient,
        embedUrl: String,
    ): DesktopStreamResolver.Resolved? {
        val html =
            client.getTextFollowingRedirects(
                embedUrl,
                mapOf(
                    HttpHeaders.UserAgent to BROWSER_USER_AGENT,
                    HttpHeaders.Referrer to PAGE_REFERER,
                    HttpHeaders.Accept to HTML_ACCEPT,
                ),
            ) ?: return null
        if (UNAVAILABLE_REGEX.containsMatchIn(html)) return null
        val srcPath = extractSrcPath(html) ?: return null
        val videoUrl = if (srcPath.startsWith("http", ignoreCase = true)) srcPath else "$VIDEO_ORIGIN$srcPath"

        val headers =
            mapOf(
                HttpHeaders.Referrer to embedUrl,
                HttpHeaders.UserAgent to BROWSER_USER_AGENT,
                HttpHeaders.Accept to "*/*",
            )
        val result = client.followMediaRedirects(videoUrl, headers) ?: return null
        if (!result.status.isSuccess() || !looksLikeMediaContentType(result.contentType)) return null
        return DesktopStreamResolver.Resolved(streamUrl = result.finalUrl, referer = embedUrl)
    }

    private fun extractSrcPath(html: String): String? =
        SRC_PATTERNS.firstNotNullOfOrNull { pattern -> pattern.find(html)?.groupValues?.get(1) }

    private const val PAGE_REFERER = "https://sibnet.ru/"
    private const val VIDEO_ORIGIN = "https://video.sibnet.ru"
    private const val HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    /**
     * Строки хоста на "видео удалено"/"не найдено" — часть встречается только на русском (сама
     * страница локализована под ru), часть — общий английский фолбэк того же смысла.
     *
     * `@Suppress("ForbiddenCyrillicStringLiteral")` — это не UI-текст пользователя (который
     * обязан идти через i18n-слой, см. описание правила), а паттерн для разбора HTML-ответа
     * реального хоста: страница Sibnet сама локализована под ru и буквально этими словами
     * сообщает "видео удалено" в разметке — тот же случай уже принят в этом кодбейзе для
     * нехардкод-данных вне UI (см. `VideoHost` в `:shared:model/Episode.kt`).
     */
    @Suppress("ForbiddenCyrillicStringLiteral")
    private val UNAVAILABLE_REGEX =
        Regex(
            "видео удалено|ролик удал[её]н|видео не найдено|видео не существует|удал[её]н пользователем|" +
                "video (is )?deleted|video not found|access denied",
            RegexOption.IGNORE_CASE,
        )

    /** Несколько паттернов вместо одного — у Sibnet встречается больше одного варианта инлайновой
     *  инициализации плеера в зависимости от версии разметки конкретной страницы (те же паттерны,
     *  что `extractSibnetSrcPath` оригинала, в том же порядке приоритета). */
    private val SRC_PATTERNS =
        listOf(
            Regex("""player\.src\(\[\s*\{\s*src:\s*"(/[^"]+)["]""", RegexOption.IGNORE_CASE),
            Regex("""src:\s*"(/v/[^"]+\.mp4[^"]*)["]""", RegexOption.IGNORE_CASE),
            Regex("""src:\s*"(/shell\.php\?[^"]+)["]""", RegexOption.IGNORE_CASE),
            Regex("""src:\s*"(/[^"]+)["]""", RegexOption.IGNORE_CASE),
            Regex("""src:\s*'(/[^']+)'""", RegexOption.IGNORE_CASE),
            Regex("""file\s*:\s*"(/shell\.php[^"]+)["]""", RegexOption.IGNORE_CASE),
        )
}
