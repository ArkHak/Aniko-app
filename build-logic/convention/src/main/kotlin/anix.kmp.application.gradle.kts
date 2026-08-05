import com.anixkmp.buildlogic.configureAnixAndroid
import com.anixkmp.buildlogic.configureAnixTargets
import com.anixkmp.buildlogic.libsCatalog
import com.anixkmp.buildlogic.version

/**
 * Convention-плагин для `:composeApp` — KMP-модуль, который одновременно является
 * Android-приложением, Desktop-приложением и iOS-фреймворком.
 *
 * Отличается от `anix.kmp.library` ровно двумя вещами: `com.android.application`
 * вместо `com.android.library` и наличием `targetSdk`. Всё остальное общее.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
}

private val catalog = libsCatalog
private val jvmTargetVersion = catalog.version("jvmTarget")

kotlin {
    configureAnixTargets(jvmTargetVersion)
}

android {
    configureAnixAndroid(
        compileSdkVersion = catalog.version("compileSdk").toInt(),
        minSdkVersion = catalog.version("minSdk").toInt(),
        jvmTargetVersion = jvmTargetVersion,
    )

    defaultConfig {
        targetSdk = catalog.version("targetSdk").toInt()
    }
}
