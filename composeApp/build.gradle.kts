import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("anix.kmp.application")
    id("anix.kmp.compose")
    // нужен для type-safe навигации (@Serializable destinations)
    id("anix.kmp.serialization")
}

val desktopMainClass = "com.anixkmp.app.MainKt"

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
            binaryOption("bundleId", "com.anixkmp.app.composeapp")
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
    namespace = "com.anixkmp.app"

    defaultConfig {
        applicationId = "com.anixkmp.app"
        versionCode = 1
        versionName = "0.1.0"
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
            packageName = "AnixKMP"
            packageVersion = "1.0.0"
        }
    }
}
