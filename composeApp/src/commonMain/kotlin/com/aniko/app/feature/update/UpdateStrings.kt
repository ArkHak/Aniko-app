package com.aniko.app.feature.update

import com.aniko.data.update.UpdateError
import com.aniko.ui.i18n.Strings

/** Текст причины сбоя обновления: выбирает экран через i18n (координатор про `Strings` не знает). */
internal fun UpdateError.toMessage(strings: Strings): String =
    when (this) {
        UpdateError.Network -> strings.updateErrorNetwork
        UpdateError.RateLimited -> strings.updateErrorRateLimited
        UpdateError.Server, UpdateError.NotFound -> strings.updateErrorServer
        UpdateError.NoAssetForPlatform -> strings.updateErrorNoAsset
        UpdateError.ChecksumMissing, UpdateError.ChecksumMismatch -> strings.updateErrorChecksum
        UpdateError.DownloadFailed -> strings.updateErrorDownload
        UpdateError.InsufficientStorage -> strings.updateErrorStorage
        UpdateError.InstallRejected -> strings.updateErrorInstall
        UpdateError.Unknown -> strings.updateErrorUnknown
    }
