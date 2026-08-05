import com.anixkmp.buildlogic.configureAnixAndroid
import com.anixkmp.buildlogic.configureAnixTargets
import com.anixkmp.buildlogic.libsCatalog
import com.anixkmp.buildlogic.version

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
    configureAnixTargets(jvmTargetVersion)
}

android {
    configureAnixAndroid(
        compileSdkVersion = catalog.version("compileSdk").toInt(),
        minSdkVersion = catalog.version("minSdk").toInt(),
        jvmTargetVersion = jvmTargetVersion,
    )
}
