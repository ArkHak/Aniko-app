package com.aniko.app.update

import com.aniko.data.update.AppUpdateInstaller
import com.aniko.data.update.OpenPageOnlyInstaller
import com.aniko.data.update.UpdatePlatform
import kotlinx.coroutines.CoroutineDispatcher
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Установщик обновлений для Desktop. На macOS из упакованного `.app` — замена приложения; в любом
 * другом случае (`gradlew run`, Windows/Linux) — только страница релиза: готовых сборок под них нет.
 */
fun createDesktopUpdateInstaller(ioDispatcher: CoroutineDispatcher): AppUpdateInstaller {
    val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)
    val launcher = System.getProperty("jpackage.app-path")
    val bundle = if (isMac) MacOsAppUpdateInstaller.bundleFromLauncherPath(launcher) else null
    return if (bundle != null) {
        MacOsAppUpdateInstaller(bundle, desktopUpdateWorkDirectory(), ioDispatcher)
    } else {
        val tmp = File(System.getProperty("java.io.tmpdir"), "aniko-updates")
        OpenPageOnlyInstaller(UpdatePlatform.MacOs, tmp.toOkioPath())
    }
}
