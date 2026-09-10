plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.compose")
    id("aniko.lint")
}

android {
    namespace = "com.aniko.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.components.resources)
            api(compose.ui)

            api(libs.coil.compose)
            api(libs.coil.network.ktor3)
            implementation(libs.ktor.client.core)
            // i18n (P2.T7/P2.T8): Lyricist — CMP Resources не подходит для runtime-переключения
            // языка, см. docs/REELWAVE_PLAN.md.
            implementation(libs.lyricist)
        }

        androidMain.dependencies {
            // WindowInsetsControllerCompat — синхронизация цвета иконок статус-бара с темой
            // приложения (ревью замечание #4, `SystemBarStyle.android.kt`).
            implementation(libs.androidx.core.ktx)
        }
    }
}
