package com.aniko.app

import androidx.compose.ui.window.ComposeUIViewController
import com.aniko.app.di.initKoinOnce
import platform.UIKit.UIViewController

/**
 * Точка входа для Swift-обёртки (`iosApp/`).
 *
 * Экспортируется в фреймворк `ComposeApp` как `MainViewControllerKt.MainViewController()`.
 */
fun MainViewController(): UIViewController {
    initKoinOnce()
    return ComposeUIViewController {
        App()
    }
}
