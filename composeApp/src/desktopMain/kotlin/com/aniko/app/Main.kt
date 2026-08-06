package com.aniko.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aniko.app.di.initKoinOnce

fun main() {
    initKoinOnce()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Aniko",
            state = rememberWindowState(size = DpSize(1100.dp, 800.dp)),
        ) {
            App()
        }
    }
}
