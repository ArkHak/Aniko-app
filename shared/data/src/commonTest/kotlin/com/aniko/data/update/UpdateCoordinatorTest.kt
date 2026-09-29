package com.aniko.data.update

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCoordinatorTest {
    private val installerBytes = "installer".repeat(1_000)
    private val store = UpdateStore(MapSettings())
    private var published = listOf(UpdateTestSupport.release("0.2.0"))
    private var checksumsBody: () -> String = {
        "${UpdateTestSupport.sha256Of(installerBytes)}  aniko-v0.2.0-android.apk\n" +
            "${UpdateTestSupport.sha256Of(installerBytes)}  aniko-v0.2.0-macos.dmg\n"
    }
    private var installOutcome: InstallOutcome = InstallOutcome.SystemInstallerLaunched
    private val installed = mutableListOf<Path>()

    private inner class FakeInstaller(
        override val platform: UpdatePlatform,
        override val capability: UpdateCapability,
    ) : AppUpdateInstaller {
        override val workDirectory: Path = UpdateTestSupport.workDir

        override suspend fun install(
            file: Path,
            release: AppRelease,
        ): InstallOutcome {
            installed += file
            return installOutcome
        }
    }

    /** Ждёт, пока конвейер выйдет из промежуточных состояний (загрузка идёт на реальных потоках Ktor). */
    private suspend fun UpdateCoordinator.awaitSettled(): UpdateState =
        withContext(Dispatchers.Default) {
            withTimeout(AWAIT_MS) {
                state.first { it !is UpdateState.Downloading && it !is UpdateState.Installing && it !is UpdateState.Checking }
            }
        }

    private suspend fun UpdateCoordinator.awaitEffect(): UpdateEffect =
        withContext(Dispatchers.Default) { withTimeout(AWAIT_MS) { effects.first() } }

    private fun TestScope.coordinator(
        platform: UpdatePlatform = UpdatePlatform.Android,
        capability: UpdateCapability = UpdateCapability.InAppInstall,
    ): UpdateCoordinator {
        val client =
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("SHA256SUMS.txt")) {
                        respond(checksumsBody(), HttpStatusCode.OK)
                    } else {
                        respond(installerBytes, HttpStatusCode.OK)
                    }
                },
            )
        return UpdateCoordinator(
            checker = UpdateChecker(ReleaseSource { published }, store, AppVersion(0, 1, 0), { 1_000_000_000L }),
            downloader = UpdateDownloader(client, FakeFileSystem()),
            installer = FakeInstaller(platform, capability),
            scope = backgroundScope,
        )
    }

    @Test
    fun startCheckOpensThePromptWhenANewerVersionExists() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = coordinator()

            coordinator.checkOnStart()

            assertEquals("0.2.0", assertIs<UpdateState.Available>(coordinator.state.value).release.version.toString())
            assertTrue(coordinator.promptOpen.value)
        }

    @Test
    fun startCheckIsSilentWhenUpToDate() =
        runTest(UnconfinedTestDispatcher()) {
            published = emptyList()
            val coordinator = coordinator()

            coordinator.checkOnStart()

            assertEquals(UpdateState.Idle, coordinator.state.value)
            assertFalse(coordinator.promptOpen.value)
        }

    @Test
    fun manualCheckReportsUpToDate() =
        runTest(UnconfinedTestDispatcher()) {
            published = emptyList()
            val coordinator = coordinator()

            coordinator.checkNow()

            assertEquals(UpdateState.UpToDate(AppVersion(0, 1, 0)), coordinator.state.value)
            assertFalse(coordinator.promptOpen.value)
        }

    @Test
    fun updateDownloadsVerifiesAndHandsTheFileToTheInstaller() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = coordinator()
            coordinator.checkNow()

            coordinator.startUpdate()
            coordinator.awaitSettled()

            assertEquals(listOf(UpdateTestSupport.workDir / "aniko-v0.2.0-android.apk"), installed)
            assertIs<UpdateState.Available>(coordinator.state.value) // системный установщик показан
            assertFalse(coordinator.promptOpen.value)
        }

    @Test
    fun macOsInstallAsksTheAppToExit() =
        runTest(UnconfinedTestDispatcher()) {
            installOutcome = InstallOutcome.Restarting
            val coordinator = coordinator(platform = UpdatePlatform.MacOs)
            coordinator.checkNow()

            coordinator.startUpdate()

            assertEquals(UpdateEffect.ExitApplication, coordinator.awaitEffect())
        }

    @Test
    fun androidPermissionRequestKeepsTheUpdateForRetry() =
        runTest(UnconfinedTestDispatcher()) {
            installOutcome = InstallOutcome.PermissionRequired
            val coordinator = coordinator()
            coordinator.checkNow()

            coordinator.startUpdate()
            coordinator.awaitSettled()

            assertIs<UpdateState.NeedsInstallPermission>(coordinator.state.value)
            assertTrue(coordinator.promptOpen.value)
        }

    @Test
    fun corruptedDownloadIsRejectedAndNeverInstalled() =
        runTest(UnconfinedTestDispatcher()) {
            checksumsBody = { "${UpdateTestSupport.sha256Of("другое")}  aniko-v0.2.0-android.apk\n" }
            val coordinator = coordinator()
            coordinator.checkNow()

            coordinator.startUpdate()
            coordinator.awaitSettled()

            assertEquals(UpdateError.ChecksumMismatch, assertIs<UpdateState.Failed>(coordinator.state.value).error)
            assertTrue(installed.isEmpty())
        }

    @Test
    fun missingChecksumLineBlocksInstall() =
        runTest(UnconfinedTestDispatcher()) {
            checksumsBody = { "${UpdateTestSupport.sha256Of(installerBytes)}  some-other-file\n" }
            val coordinator = coordinator()
            coordinator.checkNow()

            coordinator.startUpdate()
            coordinator.awaitSettled()

            assertEquals(UpdateError.ChecksumMissing, assertIs<UpdateState.Failed>(coordinator.state.value).error)
            assertTrue(installed.isEmpty())
        }

    @Test
    fun releaseWithoutAnAssetForThePlatformFails() =
        runTest(UnconfinedTestDispatcher()) {
            published = listOf(UpdateTestSupport.release("0.2.0", assets = listOf(UpdateTestSupport.asset("SHA256SUMS.txt"))))
            val coordinator = coordinator()
            coordinator.checkNow()

            coordinator.startUpdate()
            coordinator.awaitSettled()

            assertEquals(UpdateError.NoAssetForPlatform, assertIs<UpdateState.Failed>(coordinator.state.value).error)
        }

    @Test
    fun iosOnlyOpensTheReleasePage() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = coordinator(platform = UpdatePlatform.Ios, capability = UpdateCapability.OpenReleasePage)
            coordinator.checkNow()

            coordinator.startUpdate()

            val effect = assertIs<UpdateEffect.OpenUrl>(coordinator.awaitEffect())
            assertEquals("https://github.com/ArkHak/Aniko-app/releases/tag/v0.2.0", effect.url)
            assertTrue(installed.isEmpty())
            assertFalse(coordinator.promptOpen.value)
        }

    @Test
    fun systemInstallFailureIsShownToTheUser() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = coordinator()
            coordinator.checkNow()
            coordinator.startUpdate()
            coordinator.awaitSettled() // системный установщик показан, диалог закрыт
            assertFalse(coordinator.promptOpen.value)

            coordinator.onInstallFailed(UpdateError.InstallRejected)

            val failed = assertIs<UpdateState.Failed>(coordinator.state.value)
            assertEquals(UpdateError.InstallRejected, failed.error)
            assertEquals("0.2.0", failed.release?.version?.toString())
            assertTrue(coordinator.promptOpen.value)
        }

    @Test
    fun installFailureWithoutAKnownReleaseStaysInTheSettingsRowOnly() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = coordinator()

            coordinator.onInstallFailed(UpdateError.InsufficientStorage)

            val failed = assertIs<UpdateState.Failed>(coordinator.state.value)
            assertEquals(UpdateError.InsufficientStorage, failed.error)
            assertEquals(null, failed.release)
            assertFalse(coordinator.promptOpen.value)
        }

    @Test
    fun skipHidesTheVersionFromFutureAutomaticChecks() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = coordinator()
            coordinator.checkNow()

            coordinator.skipVersion()

            assertEquals(UpdateState.Idle, coordinator.state.value)
            assertFalse(coordinator.promptOpen.value)
            assertEquals("0.2.0", store.skippedVersion)
        }

    @Test
    fun laterClosesThePromptButKeepsTheUpdateVisibleInSettings() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = coordinator()
            coordinator.checkNow()

            coordinator.later()

            assertFalse(coordinator.promptOpen.value)
            assertIs<UpdateState.Available>(coordinator.state.value)
        }

    private companion object {
        const val AWAIT_MS = 10_000L
    }
}
