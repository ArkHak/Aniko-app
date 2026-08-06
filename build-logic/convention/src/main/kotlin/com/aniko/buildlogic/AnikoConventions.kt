package com.aniko.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Общая настройка KMP-таргетов для всех модулей проекта.
 *
 * Держится здесь, а не копипастой в `aniko.kmp.library` / `aniko.kmp.application`:
 * набор таргетов и иерархия source set'ов обязаны совпадать во всех модулях,
 * иначе `expect/actual` разъедутся между библиотеками и приложением.
 */
internal fun KotlinMultiplatformExtension.configureAnikoTargets(jvmTargetVersion: String) {
    val target = JvmTarget.fromTarget(jvmTargetVersion)

    androidTarget {
        compilerOptions {
            jvmTarget.set(target)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(target)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    // Стандартная иерархия (commonMain → iosMain → iosArm64Main/...) плюс собственная
    // группа `jvmShared` для кода, общего между Android и Desktop (например, OkHttp-engine).
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets.getByName("commonTest").dependencies {
        implementation(kotlin("test"))
    }
}

/**
 * Общая настройка Android-части. Подходит и `com.android.library`, и
 * `com.android.application` — обе даются через [CommonExtension].
 *
 * `targetSdk` сюда НЕ входит: у library-модулей его нет, он задаётся только в приложении.
 */
internal fun CommonExtension<*, *, *, *, *, *>.configureAnikoAndroid(
    compileSdkVersion: Int,
    minSdkVersion: Int,
    jvmTargetVersion: String,
) {
    compileSdk = compileSdkVersion

    defaultConfig {
        minSdk = minSdkVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
        targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    }
}
