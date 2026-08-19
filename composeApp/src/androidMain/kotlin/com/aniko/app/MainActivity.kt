package com.aniko.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    private fun dispatchDeepLink(intent: Intent?) {
        intent?.data?.toString()?.let(DeepLinkDispatcher::dispatch)
    }
}
