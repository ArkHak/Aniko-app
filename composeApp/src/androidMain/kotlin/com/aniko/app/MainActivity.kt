package com.aniko.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aniko.app.feature.player.PlayerPipAutoEnter
import com.aniko.app.feature.player.PlayerPipModeBridge
import com.aniko.app.navigation.DeepLinkDispatcher

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        dispatchDeepLink(intent)
        setContent {
            App()
        }
    }

    /**
     * `android:launchMode="singleTask"` (см. `AndroidManifest.xml`) гарантирует, что повторный
     * тап по deep link на уже запущенном приложении приходит сюда, а не пересоздаёт Activity —
     * без этого следующий deep link был бы виден только в [onCreate] следующего холодного старта.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchDeepLink(intent)
    }

    /**
     * «Свернул приложение — плеер ушёл в окошко» (`P16.T8`): единственный колбэк «пользователь
     * уходит домой/в задачи», который вызывается на любой версии Android. Делегирование наружу:
     * Activity не знает, открыт ли сейчас плеер и играет ли видео — это знает контроллер PiP,
     * который подписывается на хук только пока в полноэкранном плеере реально идёт
     * воспроизведение.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlayerPipAutoEnter.onUserLeaveHint?.invoke()
    }

    /** Смена PiP-режима — framework-колбэк, доступный только Activity (см. `PlayerPipModeBridge`). */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerPipModeBridge.onChanged?.invoke(isInPictureInPictureMode)
    }

    private fun dispatchDeepLink(intent: Intent?) {
        intent?.data?.toString()?.let(DeepLinkDispatcher::dispatch)
    }
}
