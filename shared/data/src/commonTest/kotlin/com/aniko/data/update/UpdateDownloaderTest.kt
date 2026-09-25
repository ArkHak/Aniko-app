package com.aniko.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateDownloaderTest {
    private val fileSystem = FakeFileSystem()
    private val payload = "aniko-installer-bytes".repeat(2_000)

    private fun downloader(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = payload,
    ) = UpdateDownloader(HttpClient(MockEngine { respond(body, status) }), fileSystem)

    @Test
    fun savesVerifiedFileUnderItsRealNameOnly() =
        runTest {
            val asset = UpdateTestSupport.asset("aniko-v0.2.0-android.apk", size = payload.length.toLong())
            val progress = mutableListOf<Float?>()

            val file =
                downloader().download(asset, UpdateTestSupport.sha256Of(payload), UpdateTestSupport.workDir) { progress += it }

            assertEquals(UpdateTestSupport.workDir / asset.name, file)
            assertContentEquals(payload.encodeToByteArray(), fileSystem.read(file) { readByteArray() })
            assertFalse(fileSystem.exists(UpdateTestSupport.workDir / "${asset.name}.part"))
            assertEquals(1f, progress.last())
            assertTrue(progress.all { it == null || it in 0f..1f })
        }

    @Test
    fun checksumMismatchDeletesEverything() =
        runTest {
            val asset = UpdateTestSupport.asset("aniko-v0.2.0-android.apk")

            val error =
                assertFailsWith<UpdateException> {
                    downloader().download(asset, UpdateTestSupport.sha256Of("другое"), UpdateTestSupport.workDir) {}
                }

            assertEquals(UpdateError.ChecksumMismatch, error.error)
            assertTrue(fileSystem.list(UpdateTestSupport.workDir).isEmpty())
        }

    @Test
    fun hashComparisonIgnoresCase() =
        runTest {
            val asset = UpdateTestSupport.asset("aniko-v0.2.0-macos.dmg")

            downloader().download(asset, UpdateTestSupport.sha256Of(payload).uppercase(), UpdateTestSupport.workDir) {}
        }

    @Test
    fun httpErrorIsDownloadFailed() =
        runTest {
            val asset = UpdateTestSupport.asset("aniko-v0.2.0-android.apk")

            val error =
                assertFailsWith<UpdateException> {
                    downloader(HttpStatusCode.NotFound, "").download(asset, "0".repeat(64), UpdateTestSupport.workDir) {}
                }

            assertEquals(UpdateError.DownloadFailed, error.error)
        }

    @Test
    fun refusesUntrustedHosts() =
        runTest {
            val asset = ReleaseAsset("aniko-v0.2.0-android.apk", 1, "https://evil.example/aniko-v0.2.0-android.apk")

            val error =
                assertFailsWith<UpdateException> {
                    downloader().download(asset, UpdateTestSupport.sha256Of(payload), UpdateTestSupport.workDir) {}
                }

            assertEquals(UpdateError.DownloadFailed, error.error)
            assertTrue(fileSystem.list(UpdateTestSupport.workDir).isEmpty())
        }

    @Test
    fun readsChecksumsOfTheRelease() =
        runTest {
            val sums = "${UpdateTestSupport.sha256Of(
                "a",
            )}  aniko-v0.2.0-android.apk\n${UpdateTestSupport.sha256Of("b")} *aniko-v0.2.0-macos.dmg\n"

            val parsed = downloader(body = sums).fetchChecksums(UpdateTestSupport.release("0.2.0"))

            assertEquals(UpdateTestSupport.sha256Of("a"), parsed["aniko-v0.2.0-android.apk"])
            assertEquals(UpdateTestSupport.sha256Of("b"), parsed["aniko-v0.2.0-macos.dmg"])
        }

    @Test
    fun releaseWithoutChecksumsFileIsRejected() =
        runTest {
            val bare = UpdateTestSupport.release("0.2.0", assets = listOf(UpdateTestSupport.asset("aniko-v0.2.0-android.apk")))

            val error = assertFailsWith<UpdateException> { downloader().fetchChecksums(bare) }

            assertEquals(UpdateError.ChecksumMissing, error.error)
        }

    @Test
    fun parsesSha256SumFormat() {
        val h = UpdateTestSupport.sha256Of("x")

        assertEquals(mapOf("a b.apk" to h), Sha256Sums.parse("$h  a b.apk\nnot a checksum line\n"))
    }
}
