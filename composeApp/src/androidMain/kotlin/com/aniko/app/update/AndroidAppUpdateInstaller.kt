package com.aniko.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.aniko.data.update.AppRelease
import com.aniko.data.update.AppUpdateInstaller
import com.aniko.data.update.InstallOutcome
import com.aniko.data.update.UpdateCapability
import com.aniko.data.update.UpdatePlatform
import com.aniko.data.update.installFailureFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

/**
 * Установка обновления на Android через системный `PackageInstaller`: APK, скачанный и проверенный
 * по SHA-256 координатором, ставится поверх текущего приложения.
 *
 * Подпись APK проверяет сама система: пакет с другим ключом (или меньшим `versionCode`) она не
 * поставит — это и есть проверка подлинности, хэш из релиза от неё не защищает.
 *
 * Сначала нужно разрешение «Установка неизвестных приложений» для самого Aniko: без него
 * ([android.content.pm.PackageManager.canRequestPackageInstalls]) открывается экран этого разрешения
 * и возвращается [InstallOutcome.PermissionRequired] — пользователь включает переключатель и жмёт
 * «Обновить» ещё раз. Дальше система показывает своё подтверждение установки
 * ([AppUpdateInstallReceiver]).
 */
class AndroidAppUpdateInstaller(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : AppUpdateInstaller {
    override val platform: UpdatePlatform = UpdatePlatform.Android
    override val capability: UpdateCapability = UpdateCapability.InAppInstall
    override val workDirectory: Path = context.cacheDir.absolutePath.toPath() / "updates"

    override suspend fun install(
        file: Path,
        release: AppRelease,
    ): InstallOutcome =
        withContext(ioDispatcher) {
            val apk = file.toFile()
            try {
                if (context.packageManager.canRequestPackageInstalls()) commit(apk) else requestPermission()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "Update install failed", e)
                installFailureFor(e)
            } finally {
                // Файл не нужен: после commit система держит свою копию, а при отказе координатор скачает заново.
                apk.delete()
            }
        }

    private fun requestPermission(): InstallOutcome {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return InstallOutcome.PermissionRequired
    }

    private fun commit(apk: File): InstallOutcome {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(sessionParams())
        installer.openSession(sessionId).use { session ->
            session.openWrite(SESSION_ENTRY_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(statusReceiver(sessionId))
        }
        return InstallOutcome.SystemInstallerLaunched
    }

    private fun sessionParams(): PackageInstaller.SessionParams =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Обновление своего же приложения: подтверждение всё равно может понадобиться (его запросит
                // система через AppUpdateInstallReceiver), но лишнего запроса без нужды не будет.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

    private fun statusReceiver(sessionId: Int): IntentSender {
        val intent = Intent(context, AppUpdateInstallReceiver::class.java).setPackage(context.packageName)
        // FLAG_MUTABLE обязателен: система дописывает в интент статус и EXTRA_INTENT подтверждения.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutable
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }

    private companion object {
        const val SESSION_ENTRY_NAME = "aniko-update.apk"
        const val TAG = "AnikoUpdate"
    }
}
