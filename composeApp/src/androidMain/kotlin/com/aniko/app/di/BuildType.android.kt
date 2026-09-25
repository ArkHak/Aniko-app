package com.aniko.app.di

import com.aniko.app.BuildConfig

/** `BuildConfig.DEBUG` — константа варианта сборки, R8 в release вырезает ветки под ней. */
actual fun isDebugBuild(): Boolean = BuildConfig.DEBUG
