@file:Suppress("MatchingDeclarationName") // `.desktop.kt` — общепринятый суффикс actual-файла в KMP.

package com.aniko.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberClipboardController(): ClipboardController = remember { DesktopClipboardController }

@Composable
actual fun rememberShareController(): ShareController = remember { DesktopShareController }

private object DesktopClipboardController : ClipboardController {
    override fun copyText(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }
}

/**
 * Desktop (P10.T9): системного шер-диалога общего назначения (аналога `Intent.createChooser`/
 * `UIActivityViewController`) в AWT/Swing нет. Полноценная альтернатива — регистрировать
 * приложение как обработчик `mailto:`/собственного протокола через `java.awt.Desktop.mail`/
 * `Desktop.browse`, но это открыло бы конкретный email/браузер-клиент, а не выбор "куда
 * поделиться" — то есть не решило бы задачу лучше, просто по-другому узко. Согласованный в задании
 * прагматичный фоллбэк: копирование в буфер + [ShareResult.COPIED_TO_CLIPBOARD] — экран сам
 * показывает уведомление по этому результату (см. `ReleaseHeaderSection.WatchAndFavoriteRow`,
 * composeApp, и `Strings.shareLinkCopiedMessage`).
 */
private object DesktopShareController : ShareController {
    override fun shareText(
        text: String,
        subject: String?,
    ): ShareResult {
        DesktopClipboardController.copyText(text)
        return ShareResult.COPIED_TO_CLIPBOARD
    }
}
