package com.aniko.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.aniko.data.update.UpdateCoordinator
import com.aniko.data.update.UpdateError
import org.koin.core.context.GlobalContext

/**
 * Принимает статусы сессии установки [AndroidAppUpdateInstaller]. На `STATUS_PENDING_USER_ACTION`
 * показывает системное окно подтверждения установки. Провал сессии (несовпавшая подпись, нехватка
 * места и т.п.) система сама НЕ показывает — статус приходит только сюда, поэтому он пробрасывается
 * в [UpdateCoordinator]: иначе пользователь видел бы молча закрывшийся диалог без причины. Успех
 * обрабатывать не нужно: процесс перезапускается уже новой версией.
 */
class AppUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirmationIntent(intent)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            // Пользователь сам отменил установку в системном диалоге — это не ошибка, состояние не трогаем.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> reportFailure(intent)
        }
    }

    private fun reportFailure(intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        Log.w(TAG, "Update install failed: status=$status $message")
        if (USER_CANCEL_MARKERS.any { message.contains(it) }) return
        val error =
            if (status == PackageInstaller.STATUS_FAILURE_STORAGE || message.contains(INSUFFICIENT_STORAGE_MARKER)) {
                UpdateError.InsufficientStorage
            } else {
                UpdateError.InstallRejected
            }
        runCatching { GlobalContext.get().get<UpdateCoordinator>().onInstallFailed(error) }
            .onFailure { Log.w(TAG, "Update coordinator is unavailable, install failure stays in the log only", it) }
    }

    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private companion object {
        const val TAG = "AnikoUpdate"
        const val INSUFFICIENT_STORAGE_MARKER = "INSTALL_FAILED_INSUFFICIENT_STORAGE"
        val USER_CANCEL_MARKERS = listOf("INSTALL_CANCELLED_BY_USER", "INSTALL_FAILED_ABORTED")
    }
}
