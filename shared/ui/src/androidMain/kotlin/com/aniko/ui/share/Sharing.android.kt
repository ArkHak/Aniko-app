package com.aniko.ui.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberClipboardController(): ClipboardController {
    val context = LocalContext.current
    return remember(context) { AndroidClipboardController(context) }
}

@Composable
actual fun rememberShareController(): ShareController {
    val context = LocalContext.current
    return remember(context) { AndroidShareController(context) }
}

private class AndroidClipboardController(
    private val context: Context,
) : ClipboardController {
    override fun copyText(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(text, text))
    }
}

/**
 * `Intent.ACTION_SEND` + `Intent.createChooser` (P10.T9).
 *
 * `FLAG_ACTIVITY_NEW_TASK`, если [context] — не `Activity`: `LocalContext.current` в Compose почти
 * всегда возвращает `Activity`-контекст, но не гарантированно на 100% (например, превью-хосты),
 * а запуск `startActivity` из не-`Activity`-контекста без этого флага падает
 * `AndroidRuntimeException` — дешёвая защита без побочных эффектов в обычном случае.
 */
private class AndroidShareController(
    private val context: Context,
) : ShareController {
    override fun shareText(
        text: String,
        subject: String?,
    ): ShareResult {
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        val chooser = Intent.createChooser(sendIntent, subject)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return ShareResult.SHEET_PRESENTED
    }
}
