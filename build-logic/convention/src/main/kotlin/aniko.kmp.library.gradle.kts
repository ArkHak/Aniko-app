import com.aniko.buildlogic.configureAnikoAndroid
import com.aniko.buildlogic.configureAnikoTargets
import com.aniko.buildlogic.libsCatalog
import com.aniko.buildlogic.version

/**
 * Базовый convention-плагин для всех `:shared:*` модулей.
 *
 * Таргеты: androidTarget, jvm("desktop"), iosArm64, iosSimulatorArm64.
 * Модулю остаётся задать только `android.namespace` и свои зависимости.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

private val catalog = libsCatalog
private val jvmTargetVersion = catalog.version("jvmTarget")

kotlin {
    configureAnikoTargets(jvmTargetVersion)
}

android {
    configureAnikoAndroid(
        compileSdkVersion = catalog.version("compileSdk").toInt(),
        minSdkVersion = catalog.version("minSdk").toInt(),
        jvmTargetVersion = jvmTargetVersion,
    )
}
