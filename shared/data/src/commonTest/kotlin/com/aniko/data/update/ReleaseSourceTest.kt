package com.aniko.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseSourceTest {
    private fun source(handler: () -> Pair<HttpStatusCode, String>): GitHubReleaseSource {
        val engine =
            MockEngine {
                val (status, body) = handler()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return GitHubReleaseSource(HttpClient(engine))
    }

    @Test
    fun mapsReleasesAndFiltersOutNoise() =
        runTest {
            val releases = source { HttpStatusCode.OK to SAMPLE_RELEASES }.fetchReleases()

            // черновик и тег `nightly` отброшены
            assertEquals(listOf("v0.2.0", "v0.1.0"), releases.map { it.tag })
            val newest = releases.first()
            assertEquals(AppVersion(0, 2, 0), newest.version)
            assertEquals("Aniko v0.2.0", newest.title)
            assertNotNull(newest.checksumsAsset)
            assertEquals("aniko-v0.2.0-android.apk", newest.assetFor(UpdatePlatform.Android)?.name)
            assertEquals("aniko-v0.2.0-macos.dmg", newest.assetFor(UpdatePlatform.MacOs)?.name)
            assertEquals("aniko-v0.2.0-ios-unsigned.ipa", newest.assetFor(UpdatePlatform.Ios)?.name)
            assertNull(newest.assetFor(UpdatePlatform.Other))
        }

    @Test
    fun dropsAssetsFromUntrustedHostsAndFallsBackPageUrl() =
        runTest {
            val v1 = source { HttpStatusCode.OK to SAMPLE_RELEASES }.fetchReleases().single { it.tag == "v0.1.0" }

            assertNull(v1.assetFor(UpdatePlatform.MacOs)) // ассет указывал на evil.example
            assertEquals(GitHubReleaseSource.DEFAULT_PAGE_URL, v1.pageUrl) // html_url вне GitHub
        }

    @Test
    fun maps404ToNotFound() = assertError(UpdateError.NotFound) { HttpStatusCode.NotFound to "{}" }

    @Test
    fun maps403And429ToRateLimited() {
        assertError(UpdateError.RateLimited) { HttpStatusCode.Forbidden to "{}" }
        assertError(UpdateError.RateLimited) { HttpStatusCode.TooManyRequests to "{}" }
    }

    @Test
    fun maps5xxToServer() = assertError(UpdateError.Server) { HttpStatusCode.BadGateway to "" }

    @Test
    fun brokenJsonIsServerError() = assertError(UpdateError.Server) { HttpStatusCode.OK to "<html>not json</html>" }

    @Test
    fun ioFailureIsNetwork() =
        runTest {
            val engine = MockEngine { throw IOException("offline") }
            val error = assertFailsWith<UpdateException> { GitHubReleaseSource(HttpClient(engine)).fetchReleases() }
            assertEquals(UpdateError.Network, error.error)
        }

    private fun assertError(
        expected: UpdateError,
        handler: () -> Pair<HttpStatusCode, String>,
    ) = runTest {
        val error = assertFailsWith<UpdateException> { source(handler).fetchReleases() }
        assertEquals(expected, error.error)
        assertTrue(error.message == expected.name)
    }

    private companion object {
        val SAMPLE_RELEASES =
            """
            [
              {"tag_name":"v0.2.0","name":"Aniko v0.2.0","body":"## Что нового\n- A","draft":false,"prerelease":true,
               "html_url":"https://github.com/ArkHak/Aniko-app/releases/tag/v0.2.0","published_at":"2026-10-01T10:00:00Z",
               "assets":[
                 {"name":"aniko-v0.2.0-android.apk","size":4867384,"browser_download_url":"https://github.com/ArkHak/Aniko-app/releases/download/v0.2.0/aniko-v0.2.0-android.apk"},
                 {"name":"aniko-v0.2.0-macos.dmg","size":179802026,"browser_download_url":"https://github.com/ArkHak/Aniko-app/releases/download/v0.2.0/aniko-v0.2.0-macos.dmg"},
                 {"name":"aniko-v0.2.0-ios-unsigned.ipa","size":17046211,"browser_download_url":"https://github.com/ArkHak/Aniko-app/releases/download/v0.2.0/aniko-v0.2.0-ios-unsigned.ipa"},
                 {"name":"SHA256SUMS.txt","size":276,"browser_download_url":"https://github.com/ArkHak/Aniko-app/releases/download/v0.2.0/SHA256SUMS.txt"}
               ]},
              {"tag_name":"v0.1.0","name":"","body":null,"draft":false,"prerelease":true,
               "html_url":"https://evil.example/releases/v0.1.0","published_at":"2026-09-25T00:00:00Z",
               "assets":[
                 {"name":"aniko-v0.1.0-macos.dmg","size":1,"browser_download_url":"https://evil.example/aniko-v0.1.0-macos.dmg"}
               ]},
              {"tag_name":"v0.3.0","name":"Draft","body":"","draft":true,"prerelease":true,"html_url":"https://github.com/x","assets":[]},
              {"tag_name":"nightly","name":"nightly","body":"","draft":false,"prerelease":true,"html_url":"https://github.com/x","assets":[]}
            ]
            """.trimIndent()
    }
}
