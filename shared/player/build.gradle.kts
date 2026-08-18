plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.compose")
    id("aniko.lint")
}

android {
    namespace = "com.aniko.player"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            // P8: только ради `WebViewCompat.addDocumentStartJavaScript` /
            // `addWebMessageListener` — единственного способа достучаться до `<video>`
            // в cross-origin подфрейме embed-страницы (см. `EmbedVideoBridge.kt`).
            implementation(libs.androidx.webkit)
        }
    }
}
