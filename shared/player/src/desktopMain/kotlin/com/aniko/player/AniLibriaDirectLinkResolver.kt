// `UnreachableCode`: тот же класс ложного срабатывания, уже задокументирован и подавлен в
// `EmbedVideoController.android.kt` (`onBridgeMessage`) и в других резолверах этой ветки (см. KDoc
// [DesktopStreamResolver]/[KodikDirectLinkResolver]) — detekt метит достижимый код после
// `runCatching { ... }.getOrNull() ?: return` как "недостижимый" в [queryParam] ниже.
@file:Suppress("UnreachableCode")

package com.aniko.player

import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import java.net.URI

/**
 * AniLibria: прямой поток без единого исполнения JS — портировано по `scrapeAnilibriaDirectFiles`/
 * `parseLibriaFileField` в
 * `github.com/Maks1mio/anixapp/blob/main/electron/lib/direct-video-link.js` (первоисточник, не
 * пересказ — см. KDoc [DesktopStreamResolver]). Стретч-цель брифа задачи — портирован ПОСЛЕ
 * Kodik/Sibnet (см. отчёт задачи за тем, что реально подтверждено вживую, а что нет: этот резолвер
 * НЕ проверялся на реальном логине, в отличие от Kodik/Sibnet).
 *
 * В отличие от Kodik/Sibnet, здесь не нужна ни отдельная API-ручка, ни цепочка редиректов — сама
 * embed-страница (`anixart.libria.fun/public/iframe.php?id=&ep=`, см. `docs/api/ANIXART_API.md`,
 * пункт 3 таблицы находок P8.T2) уже содержит готовые ссылки `.m3u8` прямо в своём HTML/инлайновом
 * JSON, их остаётся достать регуляркой:
 * 1. Сначала ищем блок, привязанный именно к номеру серии [epOrdinal] (`"s{ep}"..."file":"..."`,
 *    современная разметка кладёт несколько серий в один документ) — [episodeFileBlockRegex].
 * 2. Если блок по номеру серии не нашёлся (другой формат страницы), берём ВСЕ вхождения
 *    `"file":"..."` по порядку и индексируем по [epOrdinal] — тот же фолбэк, что в оригинале.
 * 3. Значение `file` — либо один URL, либо список вида `[480p]url1,[720p]url2,[1080p]url3`
 *    ([parseQualityMap]) — берём лучшее доступное качество.
 *
 * Referer — `https://anilibria.top/` фиксированный (для самого API/CDN AniLibria; см. оригинал),
 * не self-referer, как у Sibnet — у AniLibria это два разных партнёрских домена под одним
 * контентом (`anilibria.top`/`aniliberty.top`/`libria.fun`), Referer привязан к бренду, не к
 * конкретному embed-пути.
 */
internal object AniLibriaDirectLinkResolver {
    /** `@Suppress("ReturnCount")` — четыре guard-clause выхода (нет номера серии в URL / страница
     *  не отдалась / не нашли поле `file` / не распарсили качество) читаются честнее одной
     *  пирамиды `if/else`. */
    @Suppress("ReturnCount")
    suspend fun resolve(
        client: HttpClient,
        embedUrl: String,
    ): DesktopStreamResolver.Resolved? {
        val epOrdinal = queryParam(embedUrl, "ep")?.toIntOrNull() ?: return null
        val html =
            client.getTextFollowingRedirects(
                embedUrl,
                mapOf(
                    HttpHeaders.Referrer to embedUrl.substringBefore('?'),
                    HttpHeaders.UserAgent to BROWSER_USER_AGENT,
                    HttpHeaders.Accept to "text/html,application/xhtml+xml",
                ),
            ) ?: return null
        val fileField = extractFileField(html, epOrdinal) ?: return null
        val qualityMap = parseQualityMap(fileField) ?: return null
        val best =
            QUALITY_PRIORITY.firstNotNullOfOrNull { quality -> qualityMap[quality] }
                ?: qualityMap.values.firstOrNull()
        return best?.let { DesktopStreamResolver.Resolved(streamUrl = it, referer = REFERER) }
    }

    /** `@Suppress("ReturnCount")` — тот же принцип, что и у [resolve] (см. её KDoc). */
    @Suppress("ReturnCount")
    private fun extractFileField(
        html: String,
        epOrdinal: Int,
    ): String? {
        val perEpisode = episodeFileBlockRegex(epOrdinal).find(html)?.groupValues?.get(1)
        if (perEpisode != null) return perEpisode
        val allFiles = FILE_FIELD_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        if (allFiles.isEmpty()) return null
        return allFiles[(epOrdinal - 1).coerceIn(allFiles.indices)]
    }

    /** Не top-level `Regex` (не может быть — зависит от рантайм-значения [epOrdinal]), собирается
     *  заново на каждый вызов [extractFileField] — та же цена, что и у динамического `new RegExp`
     *  оригинала (`blockRe` в `scrapeAnilibriaDirectFiles`). */
    private fun episodeFileBlockRegex(epOrdinal: Int): Regex = Regex(""""s$epOrdinal"[\s\S]*?"file":"(.*?)["]""")

    /** `raw` — либо один URL (возможно, экранированный `\/`), либо CSV вида `[480p]url,[720p]url2`
     *  ([QUALITY_FIELD_REGEX]) — оба случая встречаются у AniLibria в зависимости от релиза.
     *
     *  `@Suppress("ReturnCount")` — тот же принцип, что и у [resolve] (см. её KDoc). */
    @Suppress("ReturnCount")
    private fun parseQualityMap(raw: String): Map<String, String>? {
        val cleaned = raw.replace("\\/", "/")
        val fromQualityList =
            QUALITY_FIELD_REGEX
                .findAll(cleaned)
                .associate { match -> match.groupValues[1] to toAbsolute(match.groupValues[2].trim()) }
        if (fromQualityList.isNotEmpty()) return fromQualityList
        if (!cleaned.startsWith("[") && MEDIA_EXTENSION_REGEX.containsMatchIn(cleaned)) {
            return mapOf(FALLBACK_QUALITY to toAbsolute(cleaned))
        }
        return null
    }

    private fun toAbsolute(src: String): String = if (src.startsWith("http", ignoreCase = true)) src else "https:$src"

    private fun queryParam(
        url: String,
        name: String,
    ): String? {
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        return query.split("&").firstNotNullOfOrNull { pair ->
            val (key, value) = pair.split("=", limit = 2).let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            key.takeIf { it == name }?.let { value }
        }
    }

    private const val REFERER = "https://anilibria.top/"
    private const val FALLBACK_QUALITY = "720"
    private val QUALITY_PRIORITY = listOf("1080", "720", "480", "360")
    private val FILE_FIELD_REGEX = Regex(""""file"\s*:\s*"((?:\\.|[^"\\])*)["]""")
    private val QUALITY_FIELD_REGEX = Regex("""\[(\d+)p]([^,\[]+)""")
    private val MEDIA_EXTENSION_REGEX = Regex("""\.(mp4|mkv|webm|m3u8)(\?|$)""", RegexOption.IGNORE_CASE)
}
