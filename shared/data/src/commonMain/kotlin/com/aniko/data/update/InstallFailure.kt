package com.aniko.data.update

import kotlinx.coroutines.CancellationException

/**
 * Сбой установки как [InstallOutcome.Failed]: нехватка места отличается от прочих отказов, отмена
 * корутины пробрасывается как есть. Общая для платформ (Android `PackageInstaller`, macOS `ditto`).
 */
fun installFailureFor(cause: Throwable): InstallOutcome =
    when {
        cause is CancellationException -> throw cause
        cause.isNoSpaceLeft() -> InstallOutcome.Failed(UpdateError.InsufficientStorage)
        else -> InstallOutcome.Failed(UpdateError.InstallRejected)
    }

private fun Throwable.isNoSpaceLeft(): Boolean {
    val text = message.orEmpty()
    return text.contains("No space left", ignoreCase = true) || text.contains("ENOSPC")
}
