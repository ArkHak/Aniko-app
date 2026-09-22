// `UnreachableCode`: тот же класс ложного срабатывания уже задокументирован и подавлен в
// `EmbedVideoController.android.kt` (`onBridgeMessage`) — detekt метит достижимый код после
// `runCatching { ... }.getOrNull() ?: return` как "недостижимый" в нескольких функциях этого
// файла (реальный компилятор — `:shared:player:compileKotlinDesktop` — доволен, код реально
// выполняется, см. отчёт задачи и KDoc [DesktopStreamResolver] за тем же классом находок там).
@file:Suppress("UnreachableCode")

package com.aniko.player

import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

/**
 * Kodik: прямой поток без единого исполнения JS — портировано по
 * `github.com/Maks1mio/anixapp/blob/main/electron/kodik-direct.js` (первоисточник, не пересказ —
 * см. KDoc [DesktopStreamResolver]).
 *
 * Три шага (совпадают с `loadKodikPlayer`/`getDirectVideoLink` оригинала):
 * 1. Достать `type`/`id`/`hash` — почти всегда прямо из пути embed-URL
 *    (`/seria|video|movie|anime/{id}/{hash}/`, см. [parseInfoFromUrl] — ЭТО и есть форма, в
 *    которой `EpisodeRepository.resolveEpisodeTarget` отдаёт Kodik-ссылку в этом приложении, HTML-
 *    фолбэк ниже почти никогда не понадобится на живых данных), иначе — из HTML самой страницы
 *    плеера (`player.hash = '...'` и т.п. JS-переменные, [parseInfoFromHtml]).
 * 2. `GET https://kodikplayer.com/ftor?type=&hash=&id=` — тот же внутренний API, что использует
 *    сам плеер хоста, отдаёт `{"links": {"720p": [{"src": "...", ...}], ...}}`.
 * 3. Каждый `src`, который не абсолютный URL/уже на "открытом" CDN, зашифрован посимвольным
 *    сдвигом Цезаря (+18, с переносом в границах СВОЕГО регистра) поверх base64 — см.
 *    [decryptIfNeeded]. Расшифрованный URL иногда указывает на progressive-`.mp4`, который CDN
 *    отдаёт per-редиректом на нестабильный edge-хост — [preferPlayableUrl] форсирует HLS-манифест
 *    вместо него там, где это применимо (см. её KDoc).
 *
 * **Referer — два РАЗНЫХ значения, не одно** (см. подробный разбор в KDoc
 * [DesktopStreamResolver.Resolved.referer]): [pageReferer] (обычно `https://anixmirai.com/` —
 * см. `EpisodeRepository.resolveEpisodeTarget`) нужен ТОЛЬКО чтобы получить страницу/подписи от
 * Kodik под ЭТИМ партнёрским аккаунтом; `/ftor` и финальный CDN-стрим используют домен самого
 * Kodik (`https://kodikplayer.com/`) — не партнёрский. Не путать эти два значения местами: то, что
 * работает для одного запроса, у другого просто вернёт `403`/`500`.
 *
 * Оригинал после расшифровки ещё зондирует достижимость выбранного CDN-манифеста
 * (`probeKodikManifest` — Kodik иногда редиректит на edge-хосты, недоступные с части сетей) и,
 * если недоступен, СДАЁТСЯ целиком (даже не пробуя другое качество). Этот порт делает не совсем то
 * же самое, а немного лучше: зондирует КАЖДОГО кандидата по приоритету качества (от лучшего к
 * худшему, 1080p → 720p → 480p → 360p, см. [decryptAndRank]) и берёт первый реально достижимый —
 * то же зондирование ([followMediaRedirects]), но не сдаётся после первой неудачи, если хост
 * отдал несколько качеств. Дешёвая правка (тот же вызов в цикле вместо одного), а устойчивость к
 * "верхнее качество недоступно с этой сети, а 480p — доступно" заметно выше.
 *
 * `@Suppress("TooManyFunctions")` — все 14 функций обслуживают РОВНО один связный алгоритм в три
 * шага (см. выше), разнесённый по маленьким именованным функциям ради читаемости каждого шага —
 * резать этот объект по произвольной границе на несколько ради счётчика было бы хуже, чем чуть
 * больший объект (тот же принцип, что и у `EpisodeRepository`, см. её KDoc).
 */
@Suppress("TooManyFunctions")
internal object KodikDirectLinkResolver {
    /** `@Suppress("ReturnCount")` — три guard-clause выхода (не распарсили type/id/hash / хост не
     *  отдал `links` / ни один кандидат не прошёл зонд достижимости) читаются честнее одной
     *  пирамиды `if/else`.
     *
     *  Переключение качества (Desktop, этой же веткой): [decryptAndRank] возвращает кандидатов с
     *  лейблами качества от лучшего к худшему, дефолтный поток («Авто») — первый реально доступный
     *  из них, то есть ЛУЧШЕЕ достижимое качество (то же зондирование [followMediaRedirects] по
     *  [probeReachable] с кэшем, чтобы дефолт не зондить дважды), а в `qualityStreams` попадают
     *  ВСЕ достижимые качества в том же порядке «от лучшего к худшему» — UI предлагает
     *  переключение только на то, что реально проиграется (мёртвое качество хуже отсутствия чипа). */
    @Suppress("ReturnCount")
    suspend fun resolve(
        client: HttpClient,
        embedUrl: String,
        pageReferer: String?,
    ): DesktopStreamResolver.Resolved? {
        val pageUrl = normalize(embedUrl)
        val info =
            parseInfoFromUrl(pageUrl)
                ?: parseInfoFromHtml(fetchPageHtml(client, pageUrl, pageReferer))
                ?: return null
        val links = fetchLinks(client, pageUrl, info) ?: return null
        val candidates = decryptAndRank(links)
        val probeHeaders = mapOf(HttpHeaders.Referrer to PLAYER_ORIGIN, HttpHeaders.UserAgent to BROWSER_USER_AGENT)
        // Кэш зондов: дефолтный поток — первый по приоритету достижимый кандидат, качества из
        // qualityStreams зондируются тем же вызовом по уникальным URL (дубль зонда того же
        // адреса — это тот же ответ, второй раз не ходим в сеть).
        val probeResults = mutableMapOf<String, Boolean>()

        suspend fun probeReachable(url: String): Boolean =
            probeResults.getOrPut(url) {
                client.followMediaRedirects(url, probeHeaders)?.status?.isSuccess() == true
            }

        val default = candidates.firstOrNull { (_, url) -> probeReachable(url) } ?: return null
        val qualityStreams =
            candidates
                .associate { (quality, url) -> quality to url }
                .filterValues { url -> probeReachable(url) }
        return DesktopStreamResolver.Resolved(
            streamUrl = default.second,
            referer = PLAYER_ORIGIN,
            qualityStreams = qualityStreams,
        )
    }

    private suspend fun fetchPageHtml(
        client: HttpClient,
        pageUrl: String,
        pageReferer: String?,
    ): String? =
        client.getTextFollowingRedirects(
            pageUrl,
            mapOf(
                HttpHeaders.Referrer to (pageReferer ?: PLAYER_ORIGIN),
                HttpHeaders.UserAgent to BROWSER_USER_AGENT,
                HttpHeaders.Accept to "text/html,application/xhtml+xml",
            ),
        )

    /** `@Suppress("ReturnCount")` — тот же принцип, что и у [resolve] (см. её KDoc): несколько
     *  guard-clause выходов вместо пирамиды `if/else`. */
    @Suppress("ReturnCount")
    private suspend fun fetchLinks(
        client: HttpClient,
        pageUrl: String,
        info: KodikInfo,
    ): JsonObject? {
        val body =
            client.getTextFollowingRedirects(
                ftorUrl(info),
                mapOf(
                    // Referer здесь — сама разрешённая страница плеера (`pageUrl`), НЕ
                    // `PLAYER_ORIGIN` и НЕ партнёрский `pageReferer` — так делает оригинал
                    // (`fetchKodikFtorLinks`), `/ftor` идентифицирует сессию по этой странице.
                    HttpHeaders.Referrer to pageUrl,
                    HttpHeaders.Accept to "application/json",
                    HttpHeaders.UserAgent to BROWSER_USER_AGENT,
                ),
            ) ?: return null
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        return root[LINKS_FIELD] as? JsonObject
    }

    private fun ftorUrl(info: KodikInfo): String {
        val query =
            listOf("type" to info.type, "hash" to info.hash, "id" to info.id)
                .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return "${PLAYER_ORIGIN}ftor?$query"
    }

    /** Кандидаты потока (лейбл качества → URL), отсортированные ОТ ЛУЧШЕГО К ХУДШЕМУ по высоте кадра
     *  (1080p → 720p → 480p → 360p …) — вызывающая сторона зондирует их по очереди и первый
     *  достижимый берёт дефолтом (режим «Авто»), см. KDoc [resolve]; тот же порядок получает
     *  чип качества в UI. Лейблы без распознаваемой высоты ([qualityHeightOf] == `null`, нештатные
     *  ключи хоста вроде `auto`) идут в конце в порядке ответа.
     *
     *  Лейбл — в том виде, в котором качество показывает UI («720p»): ключи `links` у Kodik обычно
     *  уже в форме `720p`, но суффикс гарантируем (голое `720` тоже нормализуется в `720p`) — вдруг
     *  хост сменит формат ключей. Сортируем ИМЕННО нормализованные лейблы: раньше порядок задавал
     *  список голых `1080`/`720`/…, ни один элемент которого не совпадал с ключом `720p`, поэтому
     *  ранжирование молча пустело и дефолтом становился первый ключ ответа (у Kodik — по
     *  возрастанию, то есть худшее качество).
     *
     *  `internal` (а не `private`) — чтобы ранжирование проверялось юнит-тестом без сети. */
    internal fun decryptAndRank(links: JsonObject): List<Pair<String, String>> {
        val byQuality = LinkedHashMap<String, String>()
        for ((quality, sourcesElement) in links) {
            val rawSrc =
                (sourcesElement as? JsonArray)
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("src")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: continue
            val absolute = toAbsolute(decryptIfNeeded(rawSrc))
            byQuality[qualityLabel(quality)] = preferPlayableUrl(absolute)
        }
        // `sortedByDescending` устойчива: лейблы с равной (в т.ч. неизвестной) высотой сохраняют
        // порядок ответа хоста.
        return byQuality.entries
            .map { (quality, url) -> quality to url }
            .sortedByDescending { (quality, _) -> qualityHeightOf(quality) ?: UNKNOWN_QUALITY_HEIGHT }
    }

    /** Голое число (`720`) → `720p`; всё остальное (`720p`, нештатное `auto`) — как есть, чтобы не
     *  плодить бессмысленные лейблы вроде `autop`. */
    private fun qualityLabel(quality: String): String =
        if (quality.isNotEmpty() && quality.all(Char::isDigit)) {
            quality + "p"
        } else {
            quality
        }

    /**
     * Посимвольный ROT18 (сдвиг +18 внутри алфавита СВОЕГО регистра, с переносом на `Z`/`z`) поверх
     * base64 — та же схема, что `decryptKodikSrc` оригинала. Уже абсолютные ссылки/ссылки на
     * "открытые" CDN (`kodik-storage`/`solodcdn`) не зашифрованы вовсе — [PLAIN_SRC_REGEX] их
     * пропускает, иначе base64-декодирование обычного URL просто вернёт мусор.
     */
    private fun decryptIfNeeded(src: String): String =
        if (PLAIN_SRC_REGEX.containsMatchIn(src) || src.startsWith("http", ignoreCase = true) || src.startsWith("//")) {
            src
        } else {
            decryptCaesar(src)
        }

    private fun decryptCaesar(src: String): String {
        val shifted =
            buildString(src.length) {
                for (char in src) {
                    append(
                        when (char) {
                            in 'A'..'Z' -> shiftWithinAlphabet(char, 'Z')
                            in 'a'..'z' -> shiftWithinAlphabet(char, 'z')
                            else -> char
                        },
                    )
                }
            }
        return runCatching { String(Base64.getDecoder().decode(shifted), Charsets.UTF_8) }.getOrDefault(src)
    }

    private fun shiftWithinAlphabet(
        char: Char,
        alphabetEnd: Char,
    ): Char {
        val shifted = char + CAESAR_SHIFT
        return if (shifted > alphabetEnd) shifted - ALPHABET_SIZE else shifted
    }

    /** Kodik CDN: progressive `.mp4` (в т.ч. с `/f/` в пути) 302-редиректит на нестабильный
     *  `shadow.*`/`bingo.*` edge-хост, который с части сетей недоступен. Тот же приём, что
     *  `preferPlayableKodikUrl` оригинала — форсируем HLS-манифест вместо прямого `.mp4` для
     *  известных доменов Kodik CDN, добавляя суффикс к ПУТИ (не к URL целиком — иначе у ссылок
     *  с query/hash суффикс попал бы не туда).
     *
     *  `@Suppress("ReturnCount")` — четыре guard-clause выхода ("уже HLS" / "не наш CDN-хост" /
     *  "не наш CDN-домен" / "не progressive-`.mp4`") читаются честнее одной пирамиды `if/else`. */
    @Suppress("ReturnCount")
    private fun preferPlayableUrl(url: String): String {
        if (":hls:" in url) return url
        val host = runCatching { URI(url).host }.getOrNull() ?: return url
        if (!PLAYABLE_CDN_HOST_REGEX.containsMatchIn(host)) return url
        val pathEnd = url.indexOfFirst { it == '?' || it == '#' }.let { if (it == -1) url.length else it }
        val path = url.substring(0, pathEnd)
        if (!path.endsWith(".mp4", ignoreCase = true)) return url
        return path + HLS_SUFFIX + url.substring(pathEnd)
    }

    private fun toAbsolute(src: String): String = if (src.startsWith("http", ignoreCase = true)) src else "https:$src"

    private fun normalize(embedUrl: String): String {
        val absolute = if (embedUrl.startsWith("http", ignoreCase = true)) embedUrl else "https:$embedUrl"
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return absolute
        return "${uri.scheme}://${uri.authority}${uri.rawPath}"
    }

    private fun parseInfoFromUrl(pageUrl: String): KodikInfo? {
        val match = LINK_INFO_REGEX.find(pageUrl) ?: return null
        return KodikInfo(type = match.groupValues[1], id = match.groupValues[2], hash = match.groupValues[3])
    }

    /** `@Suppress("ReturnCount")` — четыре guard-clause выхода (нет HTML / нет одной из трёх
     *  JS-переменных) читаются честнее одной пирамиды `if/else`. */
    @Suppress("ReturnCount")
    private fun parseInfoFromHtml(html: String?): KodikInfo? {
        if (html == null) return null
        val hash = HASH_REGEX.find(html)?.groupValues?.get(1) ?: return null
        val id = ID_REGEX.find(html)?.groupValues?.get(1) ?: return null
        val type = TYPE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        return KodikInfo(type = type, id = id, hash = hash)
    }

    private data class KodikInfo(
        val type: String,
        val id: String,
        val hash: String,
    )

    private const val PLAYER_ORIGIN = "https://kodikplayer.com/"
    private const val LINKS_FIELD = "links"
    private const val CAESAR_SHIFT = 18
    private const val ALPHABET_SIZE = 26
    private const val HLS_SUFFIX = ":hls:manifest.m3u8"

    /** Высота для лейбла, которого не распознал [qualityHeightOf]: ниже любого настоящего качества,
     *  поэтому такие лейблы при сортировке по убыванию оказываются в конце. */
    private const val UNKNOWN_QUALITY_HEIGHT = -1

    private val LINK_INFO_REGEX = Regex("""/(seria|video|movie|anime)/(\d+)/([0-9a-f]+)/""", RegexOption.IGNORE_CASE)
    private val HASH_REGEX = Regex("""\w+\.hash\s*=\s*'([^']+)';""")
    private val ID_REGEX = Regex("""\w+\.id\s*=\s*'([^']+)';""")
    private val TYPE_REGEX = Regex("""\w+\.type\s*=\s*'([^']+)';""")
    private val PLAIN_SRC_REGEX = Regex("""(?:kodik-storage|solodcdn)\.com/""", RegexOption.IGNORE_CASE)
    private val PLAYABLE_CDN_HOST_REGEX =
        Regex("solodcdn|kodik-storage|zerocdn|animedia|kodik-cdn", RegexOption.IGNORE_CASE)
}
