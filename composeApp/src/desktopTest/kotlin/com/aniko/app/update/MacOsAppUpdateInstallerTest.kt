package com.aniko.app.update

import com.aniko.data.update.AppRelease
import com.aniko.data.update.AppVersion
import com.aniko.data.update.InstallOutcome
import com.aniko.data.update.UpdateCapability
import com.aniko.data.update.UpdateError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Замена Aniko.app на macOS: логика с фейковым раннером и полный сценарий на настоящих hdiutil/ditto. */
class MacOsAppUpdateInstallerTest {
    private val release =
        AppRelease(AppVersion(0, 2, 0), "v0.2.0", "Aniko v0.2.0", "", "https://github.com/x", null, emptyList())

    /** [bundleIdOf] получает путь к Info.plist и возвращает CFBundleIdentifier этого bundle. */
    private class FakeRunner(
        private val attachExit: Int = 0,
        private val bundleIdOf: (String) -> String = { "com.aniko.updatetest" },
    ) : ProcessRunner {
        val commands = mutableListOf<List<String>>()
        var detached: List<String>? = null

        override fun run(command: List<String>): CommandResult {
            commands += command
            if (command.take(2) == listOf("hdiutil", "attach")) populateMountPoint(command)
            return when (command.first()) {
                "hdiutil" -> CommandResult(if (command[1] == "attach") attachExit else 0, "")
                "plutil" -> CommandResult(0, bundleIdOf(command.last()) + "\n")
                else -> CommandResult(0, "")
            }
        }

        /** Как настоящий attach: в точке монтирования появляется Aniko.app с каталогом Contents/MacOS. */
        private fun populateMountPoint(command: List<String>) {
            val mountPoint = command[command.indexOf("-mountpoint") + 1]
            File(mountPoint, "Aniko.app/Contents/MacOS").mkdirs()
        }

        override fun startDetached(
            command: List<String>,
            log: File,
        ) {
            detached = command
        }
    }

    private fun tempDir(): File = Files.createTempDirectory("aniko-update-test").toFile()

    private fun fakeBundle(
        parent: File,
        version: String,
        id: String = "com.aniko.updatetest",
        launcher: String = "#!/bin/sh\nexit 0\n",
    ): File {
        val app = File(parent, "Aniko.app")
        File(app, "Contents/MacOS").mkdirs()
        File(app, "Contents/Info.plist").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>CFBundleIdentifier</key><string>$id</string>
<key>CFBundleShortVersionString</key><string>$version</string></dict></plist>
""",
        )
        File(app, "Contents/MacOS/Aniko").apply {
            writeText(launcher)
            setExecutable(true)
        }
        return app
    }

    private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac")

    @Test
    fun bundleIsFoundFromTheJpackageLauncherPath() {
        assertEquals(
            File("/Applications/Aniko.app"),
            MacOsAppUpdateInstaller.bundleFromLauncherPath("/Applications/Aniko.app/Contents/MacOS/Aniko"),
        )
        assertNull(MacOsAppUpdateInstaller.bundleFromLauncherPath(null))
        assertNull(MacOsAppUpdateInstaller.bundleFromLauncherPath("/usr/bin/java"))
    }

    @Test
    fun withoutAWritableBundleOnlyTheReleasePageIsOffered() {
        val work = tempDir().toOkioPath()
        assertEquals(
            UpdateCapability.OpenReleasePage,
            MacOsAppUpdateInstaller(null, work, Dispatchers.Unconfined).capability,
        )
        val bundle = fakeBundle(tempDir(), "1")
        assertEquals(
            UpdateCapability.InAppInstall,
            MacOsAppUpdateInstaller(bundle, work, Dispatchers.Unconfined).capability,
        )
    }

    @Test
    fun anImageWithAnotherBundleIdentifierIsRejectedAndUnmounted() {
        val bundle = fakeBundle(tempDir(), "1")
        // Новое приложение (внутри точки монтирования mount-*) — с чужим идентификатором.
        val runner = FakeRunner(bundleIdOf = { plist -> if (plist.contains("mount-")) "com.other.app" else "com.aniko.updatetest" })
        val installer = MacOsAppUpdateInstaller(bundle, tempDir().toOkioPath(), Dispatchers.Unconfined, MacOsUpdateEnvironment(runner))

        val outcome = runBlocking { installer.install(File(tempDir(), "x.dmg").toOkioPath(), release) }

        assertEquals(InstallOutcome.Failed(UpdateError.InstallRejected), outcome)
        assertNull(runner.detached)
        assertTrue(runner.commands.any { it.take(2) == listOf("hdiutil", "detach") }, "the image must be unmounted")
    }

    @Test
    fun attachFailureIsReportedAsInstallRejected() {
        val bundle = fakeBundle(tempDir(), "1")
        val runner = FakeRunner(attachExit = 1)
        val installer = MacOsAppUpdateInstaller(bundle, tempDir().toOkioPath(), Dispatchers.Unconfined, MacOsUpdateEnvironment(runner))

        val outcome = runBlocking { installer.install(File(tempDir(), "x.dmg").toOkioPath(), release) }

        assertEquals(InstallOutcome.Failed(UpdateError.InstallRejected), outcome)
        assertNull(runner.detached)
    }

    @Test
    fun theScriptIsStartedWithPositionalArgumentsOnly() {
        val bundle = fakeBundle(tempDir(), "1")
        val runner = FakeRunner()
        val work = tempDir()
        val env = MacOsUpdateEnvironment(runner, currentPid = { 4242 }, openCommand = "/usr/bin/open", verifySeconds = 3)
        val installer = MacOsAppUpdateInstaller(bundle, work.toOkioPath(), Dispatchers.Unconfined, env)
        val dmg = File(work, "aniko-v0.2.0-macos.dmg")

        val outcome = runBlocking { installer.install(dmg.toOkioPath(), release) }

        assertEquals(InstallOutcome.Restarting, outcome)
        val command = requireNotNull(runner.detached)
        assertEquals(listOf("/bin/sh", "-c"), command.take(2))
        // Ни один путь не вклеен в текст команды: они идут отдельными позиционными аргументами.
        assertFalse(command[2].contains(bundle.path) || command[2].contains(dmg.path))
        assertEquals("4242", command[4])
        assertEquals(bundle.path, command[6])
        assertEquals(dmg.path, command[8])
        assertEquals("/usr/bin/open", command[9])
        assertEquals("3", command[11])
        assertTrue(File(work, "aniko-update.sh").readText().startsWith("#!/bin/sh"))
    }

    // ---------- полный сценарий на настоящих hdiutil / ditto / скрипте (только macOS) ----------

    private fun deadPid(): Long = ProcessBuilder("true").start().also { it.waitFor() }.pid()

    private fun makeDmg(
        source: File,
        dmg: File,
    ): Boolean =
        ProcessBuilder("hdiutil", "create", "-srcfolder", source.path, "-volname", "AnikoTest", "-format", "UDZO", "-ov", dmg.path)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0

    private fun waitFor(
        seconds: Int,
        condition: () -> Boolean,
    ): Boolean {
        repeat(seconds * 2) {
            if (condition()) return true
            Thread.sleep(500)
        }
        return condition()
    }

    private fun runScenario(
        newLauncher: String,
        expectedVersion: String,
    ) {
        val root = tempDir()
        val installed = File(root, "installed").apply { mkdirs() }
        val oldApp = fakeBundle(installed, "1", launcher = "#!/bin/sh\nexit 0\n")
        val staging = File(root, "image").apply { mkdirs() }
        fakeBundle(staging, "2", launcher = newLauncher)
        val work = File(root, "work").apply { mkdirs() }
        val dmg = File(work, "aniko-v0.2.0-macos.dmg")
        assertTrue(makeDmg(staging, dmg), "hdiutil create failed")
        val fakeOpen =
            File(root, "fake-open.sh").apply {
                writeText("#!/bin/sh\nnohup \"\$1/Contents/MacOS/Aniko\" >/dev/null 2>&1 &\n")
                setExecutable(true)
            }
        val env = MacOsUpdateEnvironment(currentPid = ::deadPid, openCommand = fakeOpen.path, verifySeconds = 2)
        val installer = MacOsAppUpdateInstaller(oldApp, work.toOkioPath(), Dispatchers.IO, env)

        try {
            val outcome = runBlocking { installer.install(dmg.toOkioPath(), release) }
            assertEquals(InstallOutcome.Restarting, outcome)

            val log = File(work, "update.log")
            assertTrue(
                waitFor(60) {
                    log.exists() && Regex("updated|rolled back|failed|cannot|did not exit").containsMatchIn(log.readText())
                },
                "script did not finish",
            )
            assertTrue(File(installed, "started.marker").exists(), "the app was not launched: ${log.readText()}")
            assertTrue(File(oldApp, "Contents/Info.plist").readText().contains("<string>$expectedVersion</string>"), log.readText())
            assertFalse(File(installed, "Aniko.app.old").exists(), "backup must be cleaned")
            assertFalse(File(installed, "Aniko.app.new").exists())
            assertTrue(
                File(
                    work
                        .listFiles()
                        .orEmpty()
                        .firstOrNull {
                            it.name.startsWith("mount-")
                        }?.path ?: "none",
                ).let { !it.exists() },
                "DMG still mounted",
            )
        } finally {
            ProcessBuilder("pkill", "-f", installed.path).start().waitFor()
            ProcessBuilder("hdiutil", "detach", "-force", File(work, "mount-x").path).start().waitFor()
            root.deleteRecursively()
        }
    }

    @Test
    fun replacesTheAppAndRelaunchesTheNewVersion() {
        if (!isMac) return
        // Новое приложение пишет маркер и живёт дольше окна проверки (2 с).
        runScenario(
            newLauncher = "#!/bin/sh\necho started > \"\$(dirname \"\$0\")/../../../started.marker\"\nsleep 30\n",
            expectedVersion = "2",
        )
    }

    @Test
    fun rollsBackWhenTheNewVersionDoesNotStayAlive() {
        if (!isMac) return
        // Новое приложение сразу завершается — проверка живости не проходит, остаётся прежняя версия.
        runScenario(
            newLauncher = "#!/bin/sh\necho started > \"\$(dirname \"\$0\")/../../../started.marker\"\nexit 0\n",
            expectedVersion = "1",
        )
    }
}
