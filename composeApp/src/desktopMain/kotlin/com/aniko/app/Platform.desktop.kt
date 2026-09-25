package com.aniko.app

actual fun platformName(): String = "Desktop ${System.getProperty("os.name")} ${System.getProperty("os.arch")}"

actual fun quitApplication() {
    kotlin.system.exitProcess(0)
}
