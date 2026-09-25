package com.aniko.app.update

import com.aniko.data.update.AppRelease
import com.aniko.data.update.AppUpdateInstaller
import com.aniko.data.update.InstallOutcome
import com.aniko.data.update.UpdateCapability
import com.aniko.data.update.UpdateError
import com.aniko.data.update.UpdatePlatform
import com.aniko.data.update.installFailureFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Внешние зависимости установщика, которые тесты подменяют: запуск процессов, PID приложения,
 * команда запуска (`open`) и время ожидания старта новой версии.
 */
internal class MacOsUpdateEnvironment(
    val runner: ProcessRunner = SystemProcessRunner,
    val currentPid: () -> Long = { ProcessHandle.current().pid() },
    val openCommand: String = "open",
    val verifySeconds: Int = 8,
)

/**
 * Обновление macOS-приложения заменой `Aniko.app`.
 *
 * Скачанный и проверенный по SHA-256 DMG монтируется, из него берётся `Aniko.app`; идентификатор
 * bundle должен совпасть с текущим (чужой или подменённый образ не ставится). Затем запускается
 * отдельный процесс-скрипт (`aniko-update.sh`, ресурс) и возвращается [InstallOutcome.Restarting]:
 * приложение завершается, скрипт дожидается его выхода, копирует новую версию рядом, подменяет и
 * запускает; при любом сбое или если новая версия не стартовала — откат на прежнюю.
 *
 * Файл скачан самим приложением, поэтому без атрибута карантина: Gatekeeper его не блокирует.
 *
 * @param bundle текущий `Aniko.app` — `null`, если приложение запущено не из bundle
 * (`gradlew run`); тогда установка из приложения недоступна ([UpdateCapability.OpenReleasePage]).
 */
internal class MacOsAppUpdateInstaller(
    private val bundle: File?,
    override val workDirectory: Path,
    private val ioDispatcher: CoroutineDispatcher,
    private val environment: MacOsUpdateEnvironment = MacOsUpdateEnvironment(),
) : AppUpdateInstaller {
    private val runner: ProcessRunner get() = environment.runner

    override val platform: UpdatePlatform = UpdatePlatform.MacOs
    override val capability: UpdateCapability =
        if (bundle != null && isReplaceable(bundle)) UpdateCapability.InAppInstall else UpdateCapability.OpenReleasePage

    override suspend fun install(
        file: Path,
        release: AppRelease,
    ): InstallOutcome =
        withContext(ioDispatcher) {
            runCatching { replaceWith(file.toFile()) }.getOrElse { installFailureFor(it) }
        }

    private fun replaceWith(dmg: File): InstallOutcome {
        val target = checkNotNull(bundle) { "not running from an .app bundle" }
        val mountPoint = mount(dmg)
        val newApp = File(mountPoint, APP_NAME)
        if (!sameBundle(newApp, target)) {
            detach(mountPoint)
            return InstallOutcome.Failed(UpdateError.InstallRejected)
        }
        runner.startDetached(scriptCommand(newApp, target, mountPoint, dmg), updateLog())
        return InstallOutcome.Restarting
    }

    private fun mount(dmg: File): File {
        val mountPoint = File(workDirectory.toFile(), "mount-${System.nanoTime()}").also { it.mkdirs() }
        val attach = listOf("hdiutil", "attach", "-nobrowse", "-readonly", "-noverify")
        val result = runner.run(attach + listOf("-mountpoint", mountPoint.path, dmg.path))
        check(result.exitCode == 0) { "hdiutil attach failed: ${result.output.take(LOG_SNIPPET)}" }
        return mountPoint
    }

    private fun detach(mountPoint: File) {
        runner.run(listOf("hdiutil", "detach", mountPoint.path, "-force"))
        mountPoint.delete()
    }

    /** Тот же `CFBundleIdentifier`, что у текущего приложения, и есть исполняемый каталог `Contents/MacOS`. */
    private fun sameBundle(
        candidate: File,
        current: File,
    ): Boolean {
        val id = bundleIdentifier(candidate)
        return File(candidate, "Contents/MacOS").isDirectory && id.isNotEmpty() && id == bundleIdentifier(current)
    }

    private fun bundleIdentifier(app: File): String {
        val plist = File(app, "Contents/Info.plist").path
        val result = runner.run(listOf("plutil", "-extract", "CFBundleIdentifier", "raw", "-o", "-", plist))
        return if (result.exitCode == 0) result.output.trim() else ""
    }

    private fun scriptCommand(
        newApp: File,
        target: File,
        mountPoint: File,
        dmg: File,
    ): List<String> {
        val script = writeScript()
        // Аргументы — позиционные ($0, $@): ни одна строка не вклеивается в текст команды.
        return listOf(
            "/bin/sh",
            "-c",
            "nohup /bin/sh \"\$0\" \"\$@\" </dev/null >/dev/null 2>&1 &",
            script.path,
            environment.currentPid().toString(),
            newApp.path,
            target.path,
            mountPoint.path,
            dmg.path,
            environment.openCommand,
            updateLog().path,
            environment.verifySeconds.toString(),
        )
    }

    private fun writeScript(): File {
        val script = File(workDirectory.toFile(), SCRIPT_FILE)
        val text =
            MacOsAppUpdateInstaller::class.java
                .getResourceAsStream("/$SCRIPT_RESOURCE")!!
                .bufferedReader()
                .readText()
        script.parentFile.mkdirs()
        script.writeText(text)
        return script
    }

    private fun updateLog(): File = File(workDirectory.toFile(), "update.log").also { it.parentFile.mkdirs() }

    companion object {
        private const val APP_NAME = "Aniko.app"
        private const val SCRIPT_RESOURCE = "aniko-update.sh"
        private const val SCRIPT_FILE = "aniko-update.sh"
        private const val LOG_SNIPPET = 200

        /** Заменяемый bundle: сам каталог `.app` и его родитель доступны для записи. */
        internal fun isReplaceable(bundle: File): Boolean {
            val parentWritable = bundle.parentFile?.canWrite() == true
            return bundle.isDirectory && bundle.canWrite() && parentWritable
        }

        /** `Aniko.app` по пути лаунчера jpackage (`.../Aniko.app/Contents/MacOS/Aniko`); `null` — не из bundle. */
        internal fun bundleFromLauncherPath(launcherPath: String?): File? =
            launcherPath
                ?.let { File(it).parentFile?.parentFile?.parentFile }
                ?.takeIf { it.name.endsWith(".app") }
    }
}

/** Рабочая папка обновлений на Desktop: `~/Library/Caches/Aniko/updates`. */
internal fun desktopUpdateWorkDirectory(): Path {
    val home = System.getProperty("user.home")
    return File(home, "Library/Caches/Aniko/updates").toOkioPath()
}
