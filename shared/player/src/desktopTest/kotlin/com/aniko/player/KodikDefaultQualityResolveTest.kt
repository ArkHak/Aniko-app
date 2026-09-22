package com.aniko.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Выбор дефолтного потока («Авто») и состав чипа качества после зондирования CDN
 * ([KodikDirectLinkResolver.resolve]). Без сети и без libVLC: ответ `/ftor` и зонды достижимости
 * подменяет [MockEngine].
 */
class KodikDefaultQualityResolveTest {
    /**
     * Клиент без сети: `/ftor` отдаёт [links], зонды CDN — 206 для URL из [reachable] и 403 для остальных
     * (сравниваются `kodikPlayableUrl` — именно их резолвер зондирует после `preferPlayableUrl`).
     */
    private fun fakeKodikClient(
        links: JsonObject,
        reachable: Set<String>,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                when {
                    request.url.host == "kodikplayer.com" && request.url.encodedPath == "/ftor" ->
                        respond(
                            content = buildJsonObject { put("links", links) }.toString(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    request.url.toString() in reachable -> respond(content = "", status = HttpStatusCode.PartialContent)
                    else -> respond(content = "", status = HttpStatusCode.Forbidden)
                }
            },
        ) {
            followRedirects = false
        }

    private fun resolve(
        links: JsonObject,
        reachableLabels: Set<String>,
    ): DesktopStreamResolver.Resolved? =
        runBlocking {
            fakeKodikClient(links, reachableLabels.mapTo(mutableSetOf(), ::kodikPlayableUrl)).use { client ->
                KodikDirectLinkResolver.resolve(client, EMBED_URL, pageReferer = "https://anixmirai.com/")
            }
        }

    @Test
    fun autoDefaultIsBestReachableQuality() {
        val resolved = resolve(kodikLinksOf("360p", "480p", "720p"), reachableLabels = setOf("360p", "480p", "720p"))

        assertEquals(kodikPlayableUrl("720p"), resolved?.streamUrl)
        assertEquals(listOf("720p", "480p", "360p"), resolved?.qualityStreams?.keys?.toList())
        assertEquals("https://kodikplayer.com/", resolved?.referer)
    }

    @Test
    fun unreachableBestQualityFallsBackToNextAndIsNotOffered() {
        val resolved = resolve(kodikLinksOf("360p", "480p", "720p"), reachableLabels = setOf("360p", "480p"))

        assertEquals(kodikPlayableUrl("480p"), resolved?.streamUrl)
        assertEquals(listOf("480p", "360p"), resolved?.qualityStreams?.keys?.toList())
    }

    @Test
    fun singleQualityIsTheDefault() {
        val resolved = resolve(kodikLinksOf("480p"), reachableLabels = setOf("480p"))

        assertEquals(kodikPlayableUrl("480p"), resolved?.streamUrl)
        assertEquals(mapOf("480p" to kodikPlayableUrl("480p")), resolved?.qualityStreams)
    }

    @Test
    fun nothingReachableIsNull() {
        assertNull(resolve(kodikLinksOf("360p", "480p", "720p"), reachableLabels = emptySet()))
    }

    private companion object {
        // Форма ссылки, в которой `EpisodeRepository.resolveEpisodeTarget` отдаёт Kodik: type/id/hash — в пути.
        const val EMBED_URL = "https://kodikplayer.com/seria/123456/0123abcd4567ef/720p"
    }
}
