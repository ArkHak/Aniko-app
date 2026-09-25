package com.aniko.app.di

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * `Platform.isDebugBinary` — фреймворк собран в debug-режиме Kotlin/Native. Xcode-конфигурация
 * (Debug/Release) попадает в Gradle через `CONFIGURATION` в `embedAndSignAppleFrameworkForXcode`,
 * так что release-архив (`xcodebuild archive -configuration Release`) даёт `false`.
 */
@OptIn(ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean = Platform.isDebugBinary
