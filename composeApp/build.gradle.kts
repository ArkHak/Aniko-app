import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("aniko.kmp.application")
    id("aniko.kmp.compose")
    // нужен для type-safe навигации (@Serializable destinations)
    id("aniko.kmp.serialization")
}

val desktopMainClass = "com.aniko.app.MainKt"

kotlin {
    // Чтобы работал и `:composeApp:run` (Compose Desktop), и `:composeApp:desktopRun` (KGP).
    jvm("desktop") {
        mainRun {
            mainClass.set(desktopMainClass)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.aniko.app.composeapp")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:model"))
            implementation(project(":shared:network"))
            implementation(project(":shared:data"))
            implementation(project(":shared:player"))
            implementation(project(":shared:ui"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.multiplatform.settings)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }

        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.aniko.app"

    defaultConfig {
        applicationId = "com.aniko.app"
        versionCode = 1
        versionName = "0.0.1"
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

compose.desktop {
    application {
        mainClass = desktopMainClass

        nativeDistributions {
            // Desktop-таргет собираем только под macOS (согласовано в плане).
            targetFormats(TargetFormat.Dmg)
            packageName = "Aniko"
            // jpackage требует, чтобы первое число версии пакета было >= 1 — это отдельная
            // версия macOS-инсталлятора, не совпадающая с версией приложения (0.0.1).
            packageVersion = "1.0.0"
        }
    }
}
