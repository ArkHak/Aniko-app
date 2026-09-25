package com.aniko.data.update

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

class UpdateCheckerTest {
    private val store = UpdateStore(MapSettings())
    private var now = 1_000_000_000L
    private var releases: () -> List<AppRelease> = { emptyList() }
    private var error: UpdateError? = null
    private var fetches = 0

    private fun checker(current: String = "0.1.0") =
        UpdateChecker(
            source =
                ReleaseSource {
                    fetches++
                    error?.let { throw UpdateException(it) }
                    releases()
                },
            store = store,
            currentVersion = requireNotNull(AppVersion.parse(current)),
            nowMs = { now },
        )

    @Test
    fun offersTheNewestVersionNewerThanCurrent() =
        runTest {
            releases =
                { listOf(UpdateTestSupport.release("0.1.5"), UpdateTestSupport.release("0.2.0"), UpdateTestSupport.release("0.1.0")) }

            val result = checker().check(force = false)

            assertEquals("0.2.0", assertIs<UpdateCheckResult.Available>(result).release.version.toString())
        }

    @Test
    fun neverOffersCurrentOrOlderVersions() =
        runTest {
            releases = { listOf(UpdateTestSupport.release("0.1.0"), UpdateTestSupport.release("0.0.9")) }

            assertIs<UpdateCheckResult.UpToDate>(checker("0.1.0").check(force = false))
        }

    @Test
    fun throttlesAutomaticChecksWithinADay() =
        runTest {
            releases = { listOf(UpdateTestSupport.release("0.2.0")) }
            val checker = checker()

            assertIs<UpdateCheckResult.Available>(checker.check(force = false))
            now += 23.hours.inWholeMilliseconds
            assertIs<UpdateCheckResult.Throttled>(checker.check(force = false))
            assertEquals(1, fetches)

            now += 2.hours.inWholeMilliseconds
            assertIs<UpdateCheckResult.Available>(checker.check(force = false))
            assertEquals(2, fetches)
        }

    @Test
    fun manualCheckIgnoresThrottling() =
        runTest {
            releases = { listOf(UpdateTestSupport.release("0.2.0")) }
            val checker = checker()
            checker.check(force = false)

            assertIs<UpdateCheckResult.Available>(checker.check(force = true))
            assertEquals(2, fetches)
        }

    @Test
    fun skippedVersionIsHiddenFromAutomaticButNotManualChecks() =
        runTest {
            releases = { listOf(UpdateTestSupport.release("0.2.0")) }
            val checker = checker()
            checker.skip(AppVersion(0, 2, 0))
            now += 48.hours.inWholeMilliseconds

            assertIs<UpdateCheckResult.Skipped>(checker.check(force = false))
            assertIs<UpdateCheckResult.Available>(checker.check(force = true))
        }

    @Test
    fun aNewerVersionThanTheSkippedOneIsOfferedAgain() =
        runTest {
            releases = { listOf(UpdateTestSupport.release("0.3.0")) }
            val checker = checker()
            checker.skip(AppVersion(0, 2, 0))

            assertIs<UpdateCheckResult.Available>(checker.check(force = false))
        }

    @Test
    fun closedRepositoryMeansUpToDateAndIsNotRetriedEveryLaunch() =
        runTest {
            error = UpdateError.NotFound
            val checker = checker()

            assertIs<UpdateCheckResult.UpToDate>(checker.check(force = false))
            assertIs<UpdateCheckResult.Throttled>(checker.check(force = false))
            assertEquals(1, fetches)
        }

    @Test
    fun failuresAreReportedAndDoNotStartTheThrottleWindow() =
        runTest {
            error = UpdateError.Network
            val checker = checker()

            assertEquals(UpdateError.Network, assertIs<UpdateCheckResult.Failed>(checker.check(force = false)).error)
            assertEquals(0L, store.lastCheckAtMs)
            assertNull(store.skippedVersion)
            assertIs<UpdateCheckResult.Failed>(checker.check(force = false)) // повтор допустим сразу
            assertEquals(2, fetches)
        }
}
