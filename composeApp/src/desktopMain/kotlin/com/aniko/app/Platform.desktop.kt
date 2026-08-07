package com.aniko.app

actual fun platformName(): String = "Desktop ${System.getProperty("os.name")} ${System.getProperty("os.arch")}"
