package com.aniko.data.repository

import com.aniko.data.api.EpisodeApi
import com.aniko.model.AnixError
import com.aniko.model.VideoHost
import com.aniko.network.AnixJson
import com.aniko.player.PlaybackSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Архитектурное решение фазы 5 (не пересматривать): всё воспроизведение идёт через embed
 * (WebView), независимо от хоста и от `EpisodeTargetDto.iframe`. Этот тест фиксирует, что
 * [EpisodeRepository.resolvePlaybackSource] всегда возвращает [PlaybackSource.Embed] — и для
 * источника, где сервер отдаёт `iframe:true` (Kodik), и для `iframe:false` (Sibnet).
 *
 * `host` теперь явный параметр (см. код-ревью Фазы 5: раньше репозиторий держал `lastSources`
 * как мутабельный кэш специально для этого — гонка состояния между параллельными экранами),
 * поэтому тестам больше не нужно предварительно вызывать `sources()`.
 */
class EpisodeRepositoryTest {

    @Test
    fun resolvePlaybackSource_kodikIframeTrue_returnsEmbed() = runTest {
        val repository = repositoryWithTarget(url = "https://kodikplayer.com/seria/548657/xxx/720p", iframe = true)

        val source = repository.resolvePlaybackSource(releaseId = 186, sourceId = 8, position = 1, host = VideoHost.KODIK)

        val embed = assertIs<PlaybackSource.Embed>(source)
        assertEquals("https://kodikplayer.com/seria/548657/xxx/720p", embed.url)
        assertEquals(VideoHost.KODIK, embed.host)
    }

    @Test
    fun resolvePlaybackSource_sibnetIframeFalse_stillReturnsEmbed() = runTest {
        val repository = repositoryWithTarget(url = "https://video.sibnet.ru/shell.php?videoid=1", iframe = false)

        val source = repository.resolvePlaybackSource(releaseId = 186, sourceId = 1, position = 0, host = VideoHost.SIBNET)

        val embed = assertIs<PlaybackSource.Embed>(source)
        assertEquals(VideoHost.SIBNET, embed.host)
    }

    @Test
    fun resolvePlaybackSource_blankUrl_throwsPlaybackResolve() = runTest {
        val repository = repositoryWithTarget(url = "", iframe = false)

        assertFailsWith<AnixError.PlaybackResolve> {
            repository.resolvePlaybackSource(releaseId = 186, sourceId = 1, position = 0, host = VideoHost.SIBNET)
        }
    }

    private fun repositoryWithTarget(url: String, iframe: Boolean): EpisodeRepository {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            check(path.startsWith("/episode/target/")) { "Unexpected path: $path" }
            respond(
                content = """
                    {
                        "code": 0,
                        "episode": {
                            "position": 0,
                            "name": "1 серия",
                            "url": "$url",
                            "iframe": $iframe
                        }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(AnixJson) }
        }

        return EpisodeRepository(episodeApi = EpisodeApi(client = httpClient))
    }
}
